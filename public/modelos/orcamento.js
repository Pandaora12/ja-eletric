/* J.A Eletricidades · montagem do orçamento a partir de window.DOC */
(async function () {
  await JA.preparar([window.DOC.cliente && window.DOC.cliente.logo, ...((window.DOC.fotos || []).map(f => f.arquivo || f)), ...((window.DOC.fotos_referencia || []).map(f => f.arquivo)), 'logo-ja.png', 'assinatura.png']);
  const { esc, rich, BRL, pad2, dataCurta, dataLonga, somaDias, marca, rodape, numerarPaginas, fluxo } = JA;
  const D = window.DOC, E = Object.assign({}, JA.EMPRESA_PADRAO, D.empresa || {});
  const raiz = document.getElementById('doc');

  const TIPOS = {
    alimentador: ['al', 'Alimentador'], quadro: ['qd', 'Quadro'], infraestrutura: ['in', 'Infraestrutura'],
    circuitos: ['ci', 'Circuitos'], iluminacao: ['il', 'Iluminação'], spda: ['sp', 'SPDA'], suportes: ['su', 'Suportes'],
    servico: ['ci', 'Serviço'], material: ['qd', 'Material'], ensaio: ['in', 'Ensaio'], outro: ['su', 'Outro'], nao_orcado: ['no', 'Não orçado']
  };
  const tipo = t => TIPOS[t] || TIPOS.outro;
  const CORES = ['#14301F', '#2E6B47', '#5FA36B', '#B9824E', '#C8F53B', '#59625B', '#0E6B38', '#8A928B'];
  const STATUS = { aguardando: ['Aguardando<br>aprovação', 'Aguardando aprovação', 'a'], aprovado: ['Aprovado', 'Aprovado', 'b'], rascunho: ['Rascunho', 'Rascunho', 'd'], revisao: ['Revisão', 'Em revisão', 'c'] };
  const st = STATUS[D.status || 'aguardando'] || STATUS.aguardando;

  const setores = (D.setores || []).map((s, i) => Object.assign({ cor: s.cor || CORES[i % CORES.length] }, s));
  let num = 0;
  setores.forEach(s => { s.itens = (s.itens || []).map(it => Object.assign({ n: ++num }, it)); s.orcados = s.itens.filter(i => i.tipo !== 'nao_orcado').length; s.nao = s.itens.length - s.orcados; });
  const totItens = num, totOrc = setores.reduce((a, s) => a + s.orcados, 0), totNao = totItens - totOrc;
  const validade = D.validade || (D.validade_dias ? somaDias(D.data, D.validade_dias) : '');
  const parcelas = (D.parcelas || []).map((p, i) => ({ n: i + 1, pct: p.percentual, valor: p.valor != null ? p.valor : (D.valor * (p.percentual || 0) / 100), quando: p.quando || '' }));
  const iguais = parcelas.length > 1 && parcelas.every(p => p.pct && p.pct === parcelas[0].pct);
  const pagResumo = parcelas.length ? (iguais ? `${parcelas.length} × ${parcelas[0].pct}%` : `${parcelas.length} parcela${parcelas.length > 1 ? 's' : ''}`) : (D.forma_pagamento || 'a combinar');
  const pagNota = [D.forma_pagamento, parcelas.map(p => p.quando).filter(Boolean).join(', ')].filter(Boolean).join(' · ');
  const cliCurto = (D.cliente && (D.cliente.nome_curto || (D.cliente.nome || '').split(/\s+(ind|ltda|s\.?a|eireli|me|epp)\b/i)[0])) || '';
  const subCab = [`${D.rotulo_doc || 'Orçamento'} ${D.numero || ''}`.trim(), D.opcao, cliCurto].filter(Boolean).join(' · ');
  const cab = `<header class="hdr">${marca(E, subCab)}<div class="meta"><span>${esc(D.cidade || 'Atibaia')}, <b>${dataCurta(D.data)}</b></span><span class="live"><i></i><b>${st[1]}</b></span></div></header>`;
  const rod = rodape(E, D.rodape || 'completo');
  const sec = (k, h, aside, extra) => `<div class="sec"${extra || ''}><div><div class="k">${esc(k)}</div><h2>${h}</h2></div>${aside ? `<div class="aside">${esc(aside)}</div>` : ''}</div>`;

  // ---------- 01 · capa ----------
  const cli = D.cliente || {};
  const capa = document.createElement('section');
  capa.className = 'page capa capa-l';
  capa.innerHTML = `
  <div class="band">
    <div class="marca"><img src="logo-ja.png" alt="${esc(E.nome)}"><div class="nm">J.A Instalações e<br>Manutenções Elétricas<small>${esc(D.slogan || 'Instalações · manutenções · SPDA')}</small></div></div>
    <div class="doc">${esc(D.rotulo_capa || 'Orçamento')}<b>${esc(D.numero || '')}</b><strong class="dt">${esc(D.cidade || 'Atibaia')}, ${dataCurta(D.data)}</strong>${D.opcao ? `<span class="op">${esc(D.opcao)}</span>` : ''}</div>
  </div>
  <div class="ribbon">${st[0]}</div>
  <div class="cb">
    <div class="k">${esc(D.tipo || 'Orçamento')}</div>
    <h1>${esc(D.titulo)}</h1>
    ${D.subtitulo ? `<p class="sub">${esc(D.subtitulo)}</p>` : ''}
    <div class="para">
      <div><div class="lbl">Preparado para</div><div class="nm2">${esc(cli.nome)}</div><div class="s">${[cli.solicitante ? 'Solicitante: ' + esc(cli.solicitante) : '', esc(cli.local || '')].filter(Boolean).join(' · ')}</div></div>
      ${cli.logo ? `<img src="${esc(cli.logo)}" alt="">` : ''}
    </div>
    <div class="info">
      <div><div class="lbl">Data</div><div class="v num">${dataCurta(D.data)}</div></div>
      <div><div class="lbl">Escopo</div><div class="v num">${totItens} itens</div><div class="s">${setores.length} setor${setores.length > 1 ? 'es' : ''}${totNao ? ` · ${totNao} não orçado${totNao > 1 ? 's' : ''}` : ''}</div></div>
      <div><div class="lbl">Início</div><div class="v num">${esc((D.prazo || {}).inicio || '—')}</div><div class="s">após a aprovação</div></div>
      <div><div class="lbl">Término</div><div class="v num">${esc((D.prazo || {}).termino || '—')}</div><div class="s">prazo máximo</div></div>
      <div><div class="lbl">Pagamento</div><div class="v num">${esc(pagResumo)}</div><div class="s">${esc(pagNota)}</div></div>
      <div><div class="lbl">Validade</div><div class="v num">${esc(validade || '—')}</div><div class="s">${D.validade_dias ? D.validade_dias + ' dias' : ''}</div></div>
    </div>
    <div class="lbl" style="margin-top:7mm">Setores atendidos · itens</div>
    <div class="setores" style="grid-template-columns:repeat(${Math.min(Math.max(setores.length, 1), 6)},1fr)">${setores.map(s => `<div><b class="num">${s.itens.length}</b>${esc(s.nome)}</div>`).join('')}</div>
    <div class="tot"><div><div class="lbl">${esc(D.valor_rotulo || 'Valor total')}</div><div class="s">${esc(D.valor_nota || '')}</div></div><div class="v num">${BRL(D.valor)}</div></div>
  </div>`;
  raiz.appendChild(capa);

  // ---------- blocos das páginas internas ----------
  const B = [];
  // 01 · apresentação
  const dest = [['À empresa', cli.nome], ['Solicitante', cli.solicitante], ['Endereço', cli.endereco || cli.local], ['Data', dataLonga(D.data)]].filter(r => r[1]);
  const kpiOrc = `<div class="kpi"><div class="lbl">Escopo</div><div class="v num">${totOrc} itens</div><div class="s">${totNao ? `+ ${totNao} não orçado${totNao > 1 ? 's' : ''}` : 'todos orçados'}</div><div class="bar"><i style="width:${totItens ? totOrc / totItens * 100 : 100}%"></i></div></div>`;
  const maxS = Math.max(1, ...setores.map(s => s.itens.length));
  let painelDir;
  if (D.numeros && D.numeros.length) {
    painelDir = `<div class="panel"><h3>${esc(D.numeros_titulo || 'A obra em números')}</h3><div class="nums">${D.numeros.slice(0, 6).map(n => `<div><div class="lbl">${esc(n.rotulo)}</div><div class="v num">${esc(n.valor)}</div><div class="s">${esc(n.nota || '')}</div></div>`).join('')}</div></div>`;
  } else {
    const cont = {}; setores.forEach(s => s.itens.forEach(i => { const t = tipo(i.tipo)[1]; cont[t] = (cont[t] || 0) + 1; }));
    painelDir = `<div class="panel"><h3>Tipos de serviço <span>${totItens} itens</span></h3><div class="nums">${Object.entries(cont).slice(0, 6).map(([t, n]) => `<div><div class="lbl">${esc(t)}</div><div class="v num">${n}</div><div class="s">item${n > 1 ? 's' : ''}</div></div>`).join('')}</div></div>`;
  }
  B.push(`<div>${sec('01 — Apresentação', esc(D.saudacao || 'Prezados senhores'), D.aside_apresentacao || '')}
    <div class="carta">
      <div class="dest">${dest.map(r => `<div><div class="lbl">${esc(r[0])}</div><div class="v">${esc(r[1])}</div></div>`).join('')}
        <div><div class="lbl">Responsável</div><div class="v">${esc(E.responsavel)}<br><span style="font-weight:400;color:var(--tinta-2);font-size:7.4pt">${esc([E.cargo, E.registro].filter(Boolean).join(' · '))}</span></div></div></div>
      <div class="txt">${D.referencia ? `<p class="ref"><b>REF.</b> ${esc(D.referencia)}</p>` : ''}${(D.carta || []).map(p => `<p>${rich(p)}</p>`).join('')}</div>
    </div></div>`);
  B.push(`<div class="kpis" style="margin-top:6mm">
    <div class="kpi main"><div class="lbl">${esc(D.valor_rotulo || 'Valor total')}</div><div class="v num">${BRL(D.valor)}</div><div class="s">${parcelas.length > 1 && iguais ? `${parcelas.length} parcelas de ${BRL(parcelas[0].valor)}` : esc(pagNota)}</div></div>
    ${kpiOrc}
    <div class="kpi"><div class="lbl">Início</div><div class="v num">${esc((D.prazo || {}).inicio || '—')}</div><div class="s">após a aprovação</div><div class="bar"><i style="width:15%;background:var(--limao);outline:.2mm solid var(--selva)"></i></div></div>
    <div class="kpi"><div class="lbl">Execução</div><div class="v num">${esc((D.prazo || {}).termino || '—')}</div><div class="s">prazo máximo de término</div><div class="bar"><i style="width:100%;background:repeating-linear-gradient(90deg,var(--selva) 0 3mm,transparent 3mm 3.5mm)"></i></div></div>
  </div>`);
  B.push(`<div class="two" style="grid-template-columns:1fr 1fr">
    <div class="panel"><h3>Itens por setor <span>${totItens} itens</span></h3><div class="frentes">${setores.map(s => `<div class="r"><span>${esc(s.nome)}</span><div class="tr"><i style="width:${s.itens.length / maxS * 100}%;background:${s.cor};${s.cor === '#C8F53B' ? 'outline:.2mm solid #14301F' : ''}"></i></div><b>${s.itens.length}</b></div>`).join('')}</div></div>
    ${painelDir}</div>`);
  B.push(`<div class="panel" style="margin-top:4mm"><h3>Situação da proposta <span>atualizado em ${dataCurta(D.data)}</span></h3><div class="status-line">
    <div class="st done"><b>Levantamento</b>${esc(D.origem || 'projeto do cliente')}</div>
    <div class="st done"><b>Orçamento</b>${dataCurta(D.data)}</div>
    <div class="st ${D.status === 'aprovado' ? 'done' : 'now'}"><b>Aprovação</b>${D.status === 'aprovado' ? 'aprovado' : (validade ? 'válida até ' + esc(validade.slice(0, 5)) : 'aguardando')}</div>
    <div class="st"><b>Início</b>${esc((D.prazo || {}).inicio || '—')}</div>
    <div class="st"><b>Término</b>${esc((D.prazo || {}).termino || '—')}</div></div></div>`);

  // 02 · escopo por setores
  const usados = [...new Set(setores.flatMap(s => s.itens.map(i => i.tipo || 'outro')))];
  B.push(`<div data-quebra="antes">${sec('02 — Escopo', 'Serviços por setores', D.aside_escopo || 'Execução conforme o projeto apresentado pelo cliente.')}<div class="legend-ops">${usados.map(t => `<span><i class="tp ${tipo(t)[0]}">${tipo(t)[1]}</i></span>`).join('')}</div></div>`);
  setores.forEach((s, si) => {
    const rows = s.itens.map(i => `<tr class="${i.tipo === 'nao_orcado' ? 'no' : ''}"><td class="n">${pad2(i.n)}</td><td>${rich(i.descricao)}</td><td class="q">${i.qtd ? esc(i.qtd) + '<span style="font-weight:400;color:var(--tinta-2)"> ' + esc(i.unidade || 'un.') + '</span>' : ''}</td><td class="t"><span class="tp ${tipo(i.tipo)[0]}">${tipo(i.tipo)[1]}</span></td></tr>`).join('');
    const ini = s.itens.length ? pad2(s.itens[0].n) : '', fim = s.itens.length ? pad2(s.itens[s.itens.length - 1].n) : '';
    B.push(`<div class="fr" data-split><div class="fr-h" style="grid-template-columns:2.2mm 1fr auto auto"><i style="background:${s.cor}"></i><div class="info"><div class="lbl">Setor ${pad2(si + 1)} · itens ${ini} a ${fim}</div><h4 data-cont="${esc(s.nome.toUpperCase())} · CONTINUAÇÃO">${esc(s.nome.toUpperCase())}</h4></div><div class="m"><div class="lbl">Itens orçados</div><div class="v">${s.orcados}${s.nao ? ` <span style="color:#E5322B;font-size:7pt">+${s.nao} não orçado${s.nao > 1 ? 's' : ''}</span>` : ''}</div></div><div class="m"><div class="lbl">Escopo</div><div class="v" style="color:#E9EDE4">${esc(s.escopo || D.escopo_tipo || 'Serviço')}</div></div></div>
      <table class="esc"><tbody>${rows}</tbody></table></div>`);
  });
  if (D.fotos_referencia && D.fotos_referencia.length) {
    B.push(`<div><div class="lbl" style="margin:2mm 0 0">${esc(D.fotos_referencia_titulo || 'Referência de acabamento')}</div><div class="refs" style="grid-template-columns:repeat(${Math.min(D.fotos_referencia.length, 3)},1fr);margin-top:2.5mm">${D.fotos_referencia.map(f => `<figure><div class="ph photo" style="height:38mm;background:url('${esc(f.arquivo)}') center/cover"></div><figcaption><b>${esc(f.titulo || '')}</b>${esc(f.legenda || '')}</figcaption></figure>`).join('')}</div></div>`);
  }

  // 03 · proposta: incluso x cliente
  const lista = (arr, cls) => `<ul class="bullets ${cls || ''}">${(arr || []).map(t => `<li>${rich(t)}</li>`).join('')}</ul>`;
  B.push(`<div data-quebra="antes">${sec('03 — Proposta', '', D.aside_proposta || '', ' style="margin-bottom:4mm;border:0;padding:0"')}
    <div class="resp2">
      <div><h2>O que está incluso</h2><div class="col"><div class="lg lg-ja"><img src="logo-ja.png" alt=""><span class="ja-nm"><b>J.A</b><i>ELETRICIDADES</i></span></div>${lista(D.incluso)}</div></div>
      <div><h2>Responsabilidade do cliente</h2><div class="col"><div class="lg">${cli.logo ? `<img src="${esc(cli.logo)}" alt="">` : `<span class="cli-nm">${esc(cliCurto || cli.nome)}</span>`}</div>${lista(D.cliente_responsabilidades, 'no')}</div></div>
    </div></div>`);
  (D.observacoes || []).forEach(o => {
    const alerta = o.tipo !== 'info';
    const ic = alerta ? '<svg class="ic" viewBox="0 0 24 24"><path d="M12 2 L23 21 H1 Z" fill="#E5322B"/><path d="M12 9 V14" stroke="#fff" stroke-width="2.2"/><circle cx="12" cy="17.4" r="1.3" fill="#fff"/></svg>'
      : '<svg class="ic" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" fill="#14301F"/><path d="M7 8 H17 M7 12 H17 M7 16 H13" stroke="#C8F53B" stroke-width="1.8"/></svg>';
    B.push(`<div class="callout" style="margin-top:4mm;${alerta ? '' : 'border-left-color:var(--selva)'}">${ic}<div><div class="lbl"${alerta ? '' : ' style="color:var(--selva)"'}>${esc(o.titulo || '')}</div><p>${rich(o.texto)}</p></div></div>`);
  });
  if (D.normas && D.normas.length) B.push(`<div><div class="lbl" style="margin-top:6mm">Normas de referência</div><div class="norms">${D.normas.map(n => `<span>${esc(n)}</span>`).join('')}</div></div>`);

  // 04 · investimento
  const linhasInv = setores.map(s => `<div class="r"><span>${esc(s.nome)} · ${s.orcados} ite${s.orcados === 1 ? 'm' : 'ns'}</span><b>incluso</b></div>`)
    .concat(setores.flatMap(s => s.itens.filter(i => i.tipo === 'nao_orcado').map(i => `<div class="r"><span style="color:#E5322B">${esc(s.nome)} · ${esc((i.resumo || i.descricao).split(' · ')[0])}</span><b style="color:#E5322B">não orçado</b></div>`)));
  B.push(`<div data-quebra="antes">${sec('04 — Investimento', 'Valor, pagamento e prazo', D.aside_investimento || 'Custos referentes à execução da obra.')}
    <div class="invest"><div><div class="lbl">${esc(D.valor_rotulo || 'Valor total')}</div><div class="v num">${BRL(D.valor)}</div><div class="s">${esc(D.valor_nota || '')}</div></div><div class="sd">${linhasInv.join('')}</div></div></div>`);
  if (parcelas.length) {
    B.push(`<div><div class="lbl" style="margin-top:6mm">Condições comerciais${D.forma_pagamento ? ' · ' + esc(D.forma_pagamento) : ''}${parcelas.length > 1 ? ` em ${parcelas.length} parcelas${iguais ? ' iguais' : ''}` : ''}</div>
      <div class="parc" style="grid-template-columns:repeat(${Math.min(parcelas.length, 4)},1fr)">${parcelas.map(p => `<div><div class="n">${p.n}ª${p.pct ? ' · ' + p.pct + '%' : ''}</div><div class="v num">${BRL(p.valor)}</div><div class="s">${esc(p.quando)}</div></div>`).join('')}</div></div>`);
  }
  const P = D.prazo || {};
  B.push(`<div>${sec('05 — Prazo', 'Linha do tempo', P.nota || '', ' style="margin-top:8mm"')}
    <div class="tl"><div class="axis"></div>
      <div class="span" style="left:0;width:12%;background:var(--papel-2);outline:.2mm solid var(--tinta-3)"></div>
      <div class="span" style="left:12%;width:88%;background:var(--limao);outline:.2mm solid var(--selva)"></div>
      <div class="mk" style="left:0;transform:none;text-align:left;width:26mm"><b>Aceite</b>proposta assinada<div class="dot" style="margin-left:0;background:var(--vermelho);border-color:var(--vermelho)"></div></div>
      <div class="mk" style="left:12%;top:11.6mm"><div class="dot" style="margin-top:0"></div><b style="margin-top:1.4mm">Início</b>${esc(P.inicio || '—')}</div>
      <div class="mk" style="right:0;left:auto;transform:none;text-align:right;width:30mm"><b>Término</b>${esc(P.termino || '—')}<div class="dot" style="margin-right:0;background:var(--selva);border-color:var(--selva)"></div></div>
      <div class="mk bot" style="left:56%;width:60mm">execução dos ${setores.length} setor${setores.length > 1 ? 'es' : ''}</div></div>
    <div class="cond" style="margin-top:6mm">
      <div><div class="lbl">Validade</div><div class="v num">${D.validade_dias ? D.validade_dias + ' dias' : esc(validade || '—')}</div><div class="s">${validade ? 'até ' + esc(validade) : ''}</div></div>
      <div><div class="lbl">Início</div><div class="v num">${esc(P.inicio || '—')}</div><div class="s">após o aceite</div></div>
      <div><div class="lbl">Término</div><div class="v num">${esc(P.termino || '—')}</div><div class="s">prazo máximo</div></div>
      <div><div class="lbl">Faturamento</div><div class="v">${esc(D.forma_pagamento || 'a combinar')}</div><div class="s">${esc(pagResumo)}</div></div></div></div>`);
  if (D.fecho !== false) B.push(`<div class="panel" style="margin-top:6mm;display:flex;align-items:center;gap:4mm"><svg viewBox="0 0 24 24" style="width:9mm;height:9mm;flex:none"><circle cx="12" cy="12" r="10" fill="#C8F53B" stroke="#14301F" stroke-width="1"/><path d="M7 12.5 l3.2 3.2 L17 9" stroke="#0B0F0C" stroke-width="2" fill="none"/></svg><p style="font-size:8.6pt">${esc(D.fecho || 'Colocamo-nos à disposição para negociação e para quaisquer dúvidas e esclarecimentos.')}</p></div>`);

  // 05 · aceite
  B.push(`<div data-quebra="antes">${sec('06 — Aceite', 'Atenciosamente', 'Ao assinar, o cliente aprova o escopo, os valores e as condições desta proposta.')}
    <div class="assina">
      <div class="box"><div class="lbl">${esc(E.razao)}</div>${D.assinatura === false ? '' : '<img class="rubrica" src="assinatura.png" alt="">'}
        <div class="line"><b>${esc(E.responsavel)}</b><br>${esc([E.cargo, E.registro].filter(Boolean).join(' · '))}<small>${esc(E.email)} · ${esc(E.telefone)}</small></div></div>
      <div class="box"><div class="lbl">De acordo · cliente</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:3mm;font-size:7pt;color:var(--tinta-2)"><div style="border-bottom:.2mm solid var(--linha);padding-bottom:3mm">Nome</div><div style="border-bottom:.2mm solid var(--linha);padding-bottom:3mm">Data ___/___/______</div></div>
        <div class="line"><b>${esc(cli.nome)}</b><br>${esc(cli.solicitante || '')}<small>Assinatura</small></div></div>
    </div>
    <div class="selos"><span class="lbl" style="margin-right:2mm">Status do documento</span><span class="selo d">Rascunho</span><span class="selo a">Aguardando aprovação</span><span class="selo b">Aprovado</span><span class="selo c">Revisão 01</span></div></div>`);
  if (D.fotos && D.fotos.length) B.push(`<div>${sec('J.A em campo', 'Serviços executados', D.fotos_legenda || '', ' style="margin-top:9mm"')}<div class="galeria" style="grid-template-columns:repeat(${Math.min(D.fotos.length, 5)},1fr)">${D.fotos.map(f => `<div style="background-image:url('${esc(f.arquivo || f)}')"></div>`).join('')}</div></div>`);
  B.push(`<div class="panel" style="margin-top:6mm;display:grid;grid-template-columns:1fr 1fr 1fr;gap:4mm">
    <div><div class="lbl">Empresa</div><div style="font-weight:600;margin-top:1mm">${esc(E.nome)}</div><div style="font-size:7pt;color:var(--tinta-2)">CNPJ ${esc(E.cnpj)}<br>${esc(E.regime)}</div></div>
    <div><div class="lbl">Endereço</div><div style="font-size:7.6pt;margin-top:1mm">${esc(E.endereco).replace(' · Jd.', '<br>Jd.')}</div></div>
    <div><div class="lbl">Contato</div><div style="font-size:7.6pt;margin-top:1mm">${esc([E.telefone, E.telefone2].filter(Boolean).join(' · '))}<br>${esc(E.email)}</div></div></div>`);

  fluxo(raiz, B, { cabecalho: cab, rodape: rod });
  numerarPaginas();
  window.__PRONTO = true;
})();
