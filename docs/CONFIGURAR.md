# J.A Documentos · como configurar

App de computador (Windows e Mac) para montar **orçamentos, recibos e comprovantes Pix** no padrão da J.A Eletricidades, com chat por voz/texto, prévia igual ao PDF, botão **Baixar PDF** e botão **Enviar no WhatsApp**.

Sem nenhuma configuração o app já abre em **modo local**: login e documentos ficam só naquele computador e o chat da IA fica desligado (o "Preencher formulário" funciona). Para ligar a IA e acessar de mais de um computador, siga os passos abaixo.

---

## 1. Criar o projeto no Supabase (grátis)

1. Entre em <https://supabase.com> → **New project**. Anote a senha do banco.
2. Em **Project Settings → API**, copie:
   - **Project URL** (ex.: `https://abcd1234.supabase.co`)
   - **anon public** key
3. Em **SQL Editor → New query**, cole todo o conteúdo de `supabase/migrations/20261125000000_inicial.sql` e clique em **Run**. Isso cria as tabelas de empresas, usuários e documentos, com cada empresa vendo só os próprios dados.
4. (Opcional) Em **Authentication → Providers → Email**, desligue **Confirm email** se não quiser que cada pessoa confirme o e-mail antes de entrar.

## 2. Ligar a IA (função `assistente`)

A chave da IA fica **só no servidor do Supabase**, nunca dentro do app.

1. Crie uma chave da API da Anthropic em <https://platform.claude.com> → **API Keys**.
2. No computador, com a [CLI do Supabase](https://supabase.com/docs/guides/cli) instalada, dentro da pasta do projeto:
   ```bash
   supabase login
   supabase link --project-ref SEU_PROJECT_REF
   supabase secrets set ANTHROPIC_API_KEY=sua-chave-aqui
   supabase functions deploy assistente
   ```
   A função usa o modelo **Claude Opus 5.5**. Cada documento custa poucos centavos de dólar de IA (depende do tamanho do texto e dos anexos).

## 3. Apontar o app para o Supabase

Na raiz do projeto, copie `.env.example` para `.env` e preencha:

```
VITE_SUPABASE_URL=https://abcd1234.supabase.co
VITE_SUPABASE_ANON_KEY=sua-chave-anon-public
```

> A chave **anon public** pode ir no app: quem protege os dados são as regras de acesso (RLS) criadas no passo 1. Nunca coloque a chave da Anthropic nem a `service_role` no `.env` do app.

## 4. Primeiro acesso

- A primeira pessoa clica em **Criar conta** e informa a empresa (ex.: J.A Eletricidades). Ela vira administradora.
- No canto superior direito aparece o **código de convite** da empresa. Outras pessoas da J.A informam esse código no cadastro e passam a ver os mesmos documentos.

## 5. Gerar o instalador

### Pelo GitHub (recomendado, gera Windows e Mac)
1. No GitHub do projeto: **Settings → Secrets and variables → Actions → New repository secret**, crie `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY`.
2. Em **Actions → Gerar instaladores → Run workflow**.
3. Ao terminar, baixe em **Artifacts**: `.msi`/`.exe` (Windows) e `.dmg` (Mac).

### No próprio computador
Precisa de [Node.js 20+](https://nodejs.org) e [Rust](https://rustup.rs) (no Windows, também o "Desktop development with C++" do Visual Studio Build Tools).
```bash
npm install
npm run tauri build
```
O instalador sai em `src-tauri/target/release/bundle/`.

Para testar durante o desenvolvimento: `npm run tauri dev`.

---

## Como funciona

| Parte | Onde está |
|---|---|
| Telas do app (React) | `src/app/` |
| Modelos dos documentos (os mesmos das skills: orçamento Georevest, recibo, Pix) | `public/modelos/` |
| Geração do PDF (usa o **Edge**, que já vem no Windows, ou o Chrome no Mac) | `src-tauri/src/pdf.rs` |
| Tabelas e regras de acesso | `supabase/migrations/` |
| IA (Claude) | `supabase/functions/assistente/` |

- **Baixar PDF**: gera o PDF e pergunta onde salvar.
- **Enviar no WhatsApp**: gera o PDF, deixa o arquivo **copiado** e abre o WhatsApp. Escolha o contato e aperte **Ctrl+V** para colar o PDF na conversa. (O WhatsApp não permite anexar arquivo automaticamente por link; por isso o arquivo vai pela área de transferência.)
- **Voz**: o botão do microfone usa o reconhecimento de voz do sistema quando disponível. No Windows, a forma mais garantida é o ditado do próprio Windows: clique na caixa de texto e aperte **Windows + H**.

## Próximas etapas previstas

- **Relatório**: a aba já existe e recebe o modelo quando ele for definido (4ª skill).
- **Android e iPhone**: o app é Tauri 2, que também compila para celular. Falta adaptar o layout para tela pequena e trocar a geração de PDF (no celular não há Edge/Chrome para imprimir).
- **Versão web**: possível com o mesmo código; a geração de PDF passaria a ser feita no servidor.
- **Outras empresas**: login e tabelas já separam por empresa; faltaria a tela de "kit de marca" (logo, cores, assinatura, dados) e a cobrança mensal.
