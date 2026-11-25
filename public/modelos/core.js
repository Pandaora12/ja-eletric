/* J.A Eletricidades · núcleo comum dos documentos (cabeçalho, rodapé, paginação, formatos) */
(function () {
  const EMPRESA_PADRAO = {
    nome: 'J.A Eletricidades',
    razao: 'J.A Instalações e Manutenções Elétricas',
    assinatura_linha: 'Instalações e Manutenções Elétricas',
    cnpj: '09.560.091/0001-94',
    regime: 'Empresa cadastrada no Simples Nacional',
    endereco: 'Av. Antônio Júlio Toledo de Garcia Lopes, 2919 · Jd. Imperial · Atibaia/SP · CEP 12953-263',
    telefone: '(11) 99918-1975',
    telefone2: '(11) 4411-5833',
    email: 'jaeletricidades@gmail.com',
    responsavel: 'Jorge Luiz dos Santos',
    cargo: 'Eletrotécnico',
    registro: 'CRT 15127234874',
    pix: { chave: '151.272.348-74', tipo: 'CPF', banco: 'Itaú', favorecido: 'Jorge Luiz dos Santos' }
  };

  const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  // texto com **negrito** permitido
  const rich = s => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');
  const BRL = v => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const pad2 = n => String(n).padStart(2, '0');

  const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
  // aceita "2026-11-23" ou "23/11/2026"
  function parseData(d) {
    if (!d) return null;
    let m = String(d).match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (m) return { d: +m[3], m: +m[2], a: +m[1] };
    m = String(d).match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})/);
    if (m) return { d: +m[1], m: +m[2], a: +m[3] };
    return null;
  }
  const dataCurta = d => { const p = parseData(d); return p ? `${pad2(p.d)}/${pad2(p.m)}/${p.a}` : esc(d); };
  const dataLonga = d => { const p = parseData(d); return p ? `${p.d} de ${MESES[p.m - 1]} de ${p.a}` : esc(d); };
  function somaDias(d, n) {
    const p = parseData(d); if (!p) return '';
    const dt = new Date(p.a, p.m - 1, p.d + n);
    return `${pad2(dt.getDate())}/${pad2(dt.getMonth() + 1)}/${dt.getFullYear()}`;
  }

  // valor por extenso em reais
  const U = ['', 'um', 'dois', 'três', 'quatro', 'cinco', 'seis', 'sete', 'oito', 'nove', 'dez', 'onze', 'doze', 'treze', 'quatorze', 'quinze', 'dezesseis', 'dezessete', 'dezoito', 'dezenove'];
  const D = ['', '', 'vinte', 'trinta', 'quarenta', 'cinquenta', 'sessenta', 'setenta', 'oitenta', 'noventa'];
  const C = ['', 'cento', 'duzentos', 'trezentos', 'quatrocentos', 'quinhentos', 'seiscentos', 'setecentos', 'oitocentos', 'novecentos'];
  function ate999(n) {
    if (n === 0) return '';
    if (n === 100) return 'cem';
    const c = Math.floor(n / 100), r = n % 100, p = [];
    if (c) p.push(C[c]);
    if (r) p.push(r < 20 ? U[r] : D[Math.floor(r / 10)] + (r % 10 ? ' e ' + U[r % 10] : ''));
    return p.join(' e ');
  }
  function inteiro(n) {
    if (n === 0) return 'zero';
    const mi = Math.floor(n / 1e6), mil = Math.floor((n % 1e6) / 1000), un = n % 1000, partes = [];
    if (mi) partes.push([mi === 1 ? 'um milhão' : ate999(mi) + ' milhões', mi]);
    if (mil) partes.push([mil === 1 ? 'mil' : ate999(mil) + ' mil', mil]);
    if (un) partes.push([ate999(un), un]);
    let s = '';
    partes.forEach((p, i) => {
      if (i === 0) { s = p[0]; return; }
      s += (p[1] < 100 || p[1] % 100 === 0) ? ' e ' + p[0] : ' ' + p[0];
    });
    return s;
  }
  function extenso(v) {
    const cent = Math.round(Number(v) * 100), r = Math.floor(cent / 100), c = cent % 100;
    let s = '';
    if (r) s = inteiro(r) + ((r % 1e6 === 0) ? ' de reais' : (r === 1 ? ' real' : ' reais'));
    if (c) s += (s ? ' e ' : '') + inteiro(c) + (c === 1 ? ' centavo' : ' centavos');
    return s || 'zero reais';
  }

  // ---------- timbrado ----------
  function marca(E, sub) {
    return `<div class="marca"><img src="logo-ja.png" alt=""><div class="nm">${esc(E.razao)}<small>${esc(sub || '')}</small></div></div>`;
  }
  function rodape(E, tipo) {
    if (tipo === 'contato') return `<footer class="ftr ftr-min"><span>${esc(E.email)}</span><span>${esc(E.telefone)}</span></footer>`;
    if (tipo === 'telefone') return `<footer class="ftr ftr-min ftr-tel"><svg viewBox="0 0 24 24"><path d="M6.6 3.5 L9.5 3 L11 7.5 L9 9 C10 11.5 12.5 14 15 15 L16.5 13 L21 14.5 L20.5 17.4 C20.3 18.9 19 20 17.5 19.9 C10 19.3 4.7 14 4.1 6.5 C4 5 5.1 3.7 6.6 3.5 Z" fill="#0E6B38"/></svg><b>${esc(E.telefone)}</b></footer>`;
    const tels = [E.telefone, E.telefone2].filter(Boolean).join(' · ');
    return `<footer class="ftr"><div class="l1"><span><b>${esc(E.nome)}</b> · CNPJ ${esc(E.cnpj)} · ${esc(E.regime)}</span><span class="pg"></span></div><div class="l2">${esc(E.endereco)} · ${esc(tels)} · ${esc(E.email)}</div></footer>`;
  }
  function numerarPaginas() {
    const ps = document.querySelectorAll('.page');
    ps.forEach((p, i) => { const e = p.querySelector('.ftr .pg'); if (e) e.innerHTML = `${pad2(i + 1)} <span>/ ${pad2(ps.length)}</span>`; });
  }

  // ---------- paginação automática ----------
  // cria páginas com cabeçalho/rodapé e despeja blocos; quando um bloco não cabe, abre página nova.
  // blocos com [data-split] (tabela com <tbody>) são quebrados linha a linha.
  function fluxo(alvo, blocos, opt) {
    const mm = 96 / 25.4;
    let pagina, corpo;
    const nova = () => {
      pagina = document.createElement('section');
      pagina.className = 'page' + (opt.classe ? ' ' + opt.classe : '');
      pagina.innerHTML = (opt.cabecalho || '') + '<div class="corpo"></div>' + (opt.rodape || '');
      alvo.appendChild(pagina);
      corpo = pagina.querySelector('.corpo');
    };
    const limite = () => {
      const r = pagina.getBoundingClientRect(), f = pagina.querySelector('.ftr');
      return r.bottom - (f ? f.getBoundingClientRect().height : 0) - 4 * mm;
    };
    const fundo = () => corpo.lastElementChild ? corpo.lastElementChild.getBoundingClientRect().bottom : 0;
    const cabe = () => fundo() <= limite();
    const por = el => {
      corpo.appendChild(el);
      if (cabe()) return;
      const sozinho = corpo.children.length === 1;
      if (!el.hasAttribute('data-split')) { if (!sozinho) { corpo.removeChild(el); nova(); corpo.appendChild(el); } return; }
      const tb = el.querySelector('tbody'), sobra = [];
      while (!cabe() && tb.rows.length > 1) { const r = tb.rows[tb.rows.length - 1]; tb.removeChild(r); sobra.unshift(r); }
      if (!cabe() && !sozinho) { sobra.forEach(r => tb.appendChild(r)); corpo.removeChild(el); nova(); por(el); return; }
      if (!sobra.length) return;
      // evita linha órfã: a continuação leva pelo menos 3 linhas (e a primeira parte fica com pelo menos 3)
      while (sobra.length < 3 && tb.rows.length > 3) { const r = tb.rows[tb.rows.length - 1]; tb.removeChild(r); sobra.unshift(r); }
      if (tb.rows.length < 3 && !sozinho) { sobra.forEach(r => tb.appendChild(r)); corpo.removeChild(el); nova(); por(el); return; }
      const cont = el.cloneNode(true); cont.querySelector('tbody').innerHTML = '';
      const tit = cont.querySelector('[data-cont]'); if (tit) tit.textContent = tit.dataset.cont;
      sobra.forEach(r => cont.querySelector('tbody').appendChild(r));
      nova(); por(cont);
    };
    nova();
    blocos.forEach(html => {
      const t = document.createElement('div'); t.innerHTML = html.trim();
      const el = t.firstElementChild; if (!el) return;
      if (el.dataset.quebra === 'antes' && corpo.children.length) nova();
      por(el);
    });
  }

  // espera fontes e imagens antes de medir (a paginação depende das alturas reais)
  async function preparar(urls) {
    await Promise.all((urls || []).filter(Boolean).map(u => new Promise(ok => { const i = new Image(); i.onload = i.onerror = ok; i.src = u; })));
    if (document.fonts) await document.fonts.ready;
  }

  window.JA = { preparar, EMPRESA_PADRAO, esc, rich, BRL, pad2, dataCurta, dataLonga, somaDias, extenso, marca, rodape, numerarPaginas, fluxo };
})();
