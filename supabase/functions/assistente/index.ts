// J.A Documentos · assistente de IA
// Recebe a conversa (texto, fotos, PDFs, texto de Word) e devolve uma resposta curta
// e, quando houver informação suficiente, os dados organizados de um documento
// (orçamento, recibo ou comprovante Pix) no formato que os modelos do app usam.
//
// Segredo necessário:  supabase secrets set ANTHROPIC_API_KEY=...
// A chave fica só aqui no servidor; o app nunca vê a chave.

import Anthropic from "npm:@anthropic-ai/sdk";
import { createClient } from "npm:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Anexo = { tipo: "imagem" | "pdf" | "texto"; mime?: string; base64?: string; texto?: string; nome?: string };
type Mensagem = { papel: "usuario" | "assistente"; texto: string; anexos?: Anexo[] };

const ITEM_ORC = {
  type: "object",
  properties: {
    tipo: { type: "string", enum: ["alimentador", "quadro", "infraestrutura", "circuitos", "iluminacao", "spda", "suportes", "servico", "material", "ensaio", "outro", "nao_orcado"] },
    descricao: { type: "string", description: "Descrição limpa, sem o número de quantidade" },
    qtd: { type: "string", description: "Quantidade, se o texto disser (ex.: '11')" },
    unidade: { type: "string" },
    resumo: { type: "string", description: "Só para nao_orcado: texto curto (ex.: 'malha no telhado')" },
  },
  required: ["tipo", "descricao"],
};

const TOOLS: Anthropic.Beta.BetaTool[] = [
  {
    name: "preparar_orcamento",
    description:
      "Organiza um orçamento/proposta de serviço elétrico. Use quando a pessoa descrever um orçamento ou mandar um orçamento para remodelar. " +
      "Agrupe os itens por setor/área da obra. Não invente valores, prazos nem quantidades.",
    input_schema: {
      type: "object",
      properties: {
        numero: { type: "string" }, opcao: { type: "string", description: "Ex.: '2ª opção'" },
        data: { type: "string", description: "AAAA-MM-DD" }, cidade: { type: "string" },
        validade_dias: { type: "number" }, tipo: { type: "string", description: "Linha curta acima do título, ex.: 'Mão de obra · Instalações elétricas e SPDA'" },
        titulo: { type: "string" }, subtitulo: { type: "string" },
        cliente: { type: "object", properties: { nome: { type: "string" }, nome_curto: { type: "string" }, solicitante: { type: "string" }, local: { type: "string" }, endereco: { type: "string" } }, required: ["nome"] },
        referencia: { type: "string" },
        carta: { type: "array", items: { type: "string" }, description: "1 a 3 parágrafos da apresentação; pode usar **negrito**" },
        numeros: { type: "array", items: { type: "object", properties: { rotulo: { type: "string" }, valor: { type: "string" }, nota: { type: "string" } }, required: ["rotulo", "valor"] } },
        escopo_tipo: { type: "string", description: "Ex.: 'Mão de obra' ou 'Fornecimento e montagem'" },
        setores: { type: "array", items: { type: "object", properties: { nome: { type: "string" }, itens: { type: "array", items: ITEM_ORC } }, required: ["nome", "itens"] } },
        incluso: { type: "array", items: { type: "string" } },
        cliente_responsabilidades: { type: "array", items: { type: "string" } },
        observacoes: { type: "array", items: { type: "object", properties: { tipo: { type: "string", enum: ["alerta", "info"] }, titulo: { type: "string" }, texto: { type: "string" } }, required: ["tipo", "texto"] } },
        normas: { type: "array", items: { type: "string" } },
        valor: { type: "number" }, valor_rotulo: { type: "string" }, valor_nota: { type: "string" },
        forma_pagamento: { type: "string" },
        parcelas: { type: "array", items: { type: "object", properties: { percentual: { type: "number" }, valor: { type: "number" }, quando: { type: "string" } } } },
        prazo: { type: "object", properties: { inicio: { type: "string" }, termino: { type: "string" }, nota: { type: "string" } } },
      },
      required: ["titulo", "cliente", "setores"],
    },
  },
  {
    name: "preparar_recibo",
    description: "Organiza um recibo (comprovante de pagamento de serviço já pago). Use quando a pessoa pedir recibo ou comprovante de pagamento.",
    input_schema: {
      type: "object",
      properties: {
        numero: { type: "string" }, data: { type: "string", description: "AAAA-MM-DD" }, cidade: { type: "string" },
        rotulo: { type: "string", description: "Linha curta acima de RECIBO, ex.: 'Atendimento emergencial'" },
        pagador: { type: "object", properties: { nome: { type: "string" }, documento: { type: "string", description: "Ex.: 'CNPJ 00.000.000/0000-00'" }, endereco: { type: "string" }, complemento: { type: "string" } }, required: ["nome"] },
        local: { type: "object", properties: { nome: { type: "string" }, cidade: { type: "string" } } },
        referente: { type: "string", description: "Texto depois de 'referente', começando com ao/à/aos/às" },
        itens: { type: "array", items: { type: "object", properties: { descricao: { type: "string" }, detalhe: { type: "string" }, tipo: { type: "string", enum: ["servico", "material"] }, valor: { type: "number" } }, required: ["descricao", "valor"] } },
        pago: { type: "boolean", description: "false se ainda não foi pago (vira cobrança)" },
      },
      required: ["pagador", "itens"],
    },
  },
  {
    name: "preparar_comprovante",
    description: "Organiza os dados para pagamento via Pix (chave Pix da empresa), com ou sem valor. Use quando pedirem a chave Pix, dados para pagamento ou cobrança por Pix.",
    input_schema: {
      type: "object",
      properties: {
        valor: { type: "number" }, referente: { type: "string" }, cliente: { type: "string" },
        itens: { type: "array", items: { type: "object", properties: { descricao: { type: "string" }, valor: { type: "number" } }, required: ["descricao", "valor"] } },
        vencimento: { type: "string" }, data: { type: "string" },
      },
    },
  },
];

const TIPO_DA_FERRAMENTA: Record<string, string> = {
  preparar_orcamento: "orcamento",
  preparar_recibo: "recibo",
  preparar_comprovante: "comprovante",
};

function sistema(empresa: Record<string, unknown>, hoje: string, aba?: string) {
  return `Você é o assistente de documentos da ${empresa.nome ?? "empresa"}, uma empresa de instalações e manutenções elétricas (média e baixa tensão, cabines primárias, SPDA, quadros, infraestrutura).
Quem usa o app está muitas vezes na rua, falando por áudio ou digitando rápido, às vezes com erros. Seu trabalho é transformar o que a pessoa disser (e os anexos) nos dados de um documento, usando uma das ferramentas:
- preparar_orcamento: orçamento ou proposta (também para remodelar um orçamento enviado em Word/PDF/foto);
- preparar_recibo: recibo / comprovante de pagamento de serviço;
- preparar_comprovante: chave Pix / dados para pagamento, com ou sem valor.

Regras:
- Escreva em português do Brasil, tom profissional e direto. Corrija ortografia e termos técnicos (ex.: "condolete", "SPDA", "QDFL").
- Nunca invente valores, quantidades, prazos, CNPJ ou nomes. Se faltar algo essencial (no recibo: pagador e valores; no orçamento: cliente e o que será feito), pergunte em uma frase curta em vez de chamar a ferramenta.
- Quando tiver o suficiente, chame a ferramenta e responda em 1 ou 2 frases dizendo o que montou e o que a pessoa deve conferir. Não repita o documento inteiro no texto.
- Datas em AAAA-MM-DD. Hoje é ${hoje}. Cidade padrão: Atibaia.
- No orçamento, agrupe os itens por setor/área (Fábrica, Piso térreo, Guarita, SPDA...). Item que o texto diz não estar orçado usa tipo "nao_orcado". O que a empresa faz vai em "incluso"; materiais, alvenaria, valas, plataformas e alterações de projeto vão em "cliente_responsabilidades".
- No recibo, serviço/mão de obra é tipo "servico" e peça/equipamento é "material".
- Deixe o campo "numero" vazio: o app numera sozinho.
${aba && aba !== "inicio" ? `- A pessoa está na aba "${aba}"; na dúvida, prefira esse tipo de documento.` : ""}

Dados da empresa (só para contexto, não precisa repetir): ${JSON.stringify(empresa)}`;
}

function conteudo(m: Mensagem): Anthropic.Beta.BetaContentBlockParam[] {
  const blocos: Anthropic.Beta.BetaContentBlockParam[] = [];
  for (const a of m.anexos ?? []) {
    if (a.tipo === "imagem" && a.base64 && a.mime) {
      blocos.push({ type: "image", source: { type: "base64", media_type: a.mime as "image/png", data: a.base64 } });
    } else if (a.tipo === "pdf" && a.base64) {
      blocos.push({ type: "document", source: { type: "base64", media_type: "application/pdf", data: a.base64 }, title: a.nome });
    } else if (a.tipo === "texto" && a.texto) {
      blocos.push({ type: "text", text: `Conteúdo do arquivo ${a.nome ?? ""}:\n${a.texto}` });
    }
  }
  blocos.push({ type: "text", text: m.texto?.trim() || "(sem texto)" });
  return blocos;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

  try {
    // Confere quem está chamando e busca os dados da empresa dele
    const supa = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    });
    const { data: u } = await supa.auth.getUser();
    if (!u?.user) return json({ erro: "Faça login para usar o assistente." }, 401);
    const { data: emp } = await supa.from("empresas").select("nome, dados").single();
    const empresa = { nome: emp?.nome, ...(emp?.dados ?? {}) };

    const { mensagens, aba, hoje } = (await req.json()) as { mensagens: Mensagem[]; aba?: string; hoje?: string };
    if (!Array.isArray(mensagens) || !mensagens.length) return json({ erro: "Mensagem vazia." }, 400);

    const messages: Anthropic.Beta.BetaMessageParam[] = mensagens.slice(-20).map((m) => ({
      role: m.papel === "assistente" ? "assistant" : "user",
      content: m.papel === "assistente" ? m.texto : conteudo(m),
    }));

    const client = new Anthropic(); // lê ANTHROPIC_API_KEY do ambiente da função
    const r = await client.beta.messages.create({
      model: "claude-opus-5-5",
      max_tokens: 16000,
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      output_config: { effort: "medium" },
      system: sistema(empresa, hoje ?? new Date().toISOString().slice(0, 10), aba),
      tools: TOOLS,
      tool_choice: { type: "auto" },
      messages,
    });

    if (r.stop_reason === "refusal") {
      return json({ texto: "Não consegui montar esse documento. Tente descrever de outro jeito.", documento: null });
    }
    let texto = "";
    let documento: { tipo: string; dados: unknown } | null = null;
    for (const b of r.content) {
      if (b.type === "text") texto += b.text;
      if (b.type === "tool_use" && TIPO_DA_FERRAMENTA[b.name]) {
        documento = { tipo: TIPO_DA_FERRAMENTA[b.name], dados: b.input };
      }
    }
    if (!texto.trim() && documento) texto = "Pronto, montei o documento. Confira os dados antes de enviar.";
    if (r.stop_reason === "max_tokens") texto += "\n(A resposta ficou longa demais e pode estar incompleta.)";
    return json({ texto: texto.trim(), documento });
  } catch (e) {
    if (e instanceof Anthropic.AuthenticationError) return json({ erro: "Chave da IA inválida no servidor." }, 500);
    if (e instanceof Anthropic.RateLimitError) return json({ erro: "Muitas solicitações agora. Tente de novo em instantes." }, 429);
    if (e instanceof Anthropic.APIError) return json({ erro: `Erro da IA (${e.status}). Tente de novo.` }, 502);
    return json({ erro: "Erro inesperado no assistente." }, 500);
  }
});
