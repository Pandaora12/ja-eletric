/* J.A Eletricidades · dados para pagamento (Pix PF/PJ, TED, boleto, cartão), com ou sem valor, a partir de window.DOC */
async function desenhar() {
  document.getElementById('doc').innerHTML = '';
  const { esc, rich, BRL, dataCurta, extenso, rodape } = JA;
  const D = window.DOC || {}, E = Object.assign({}, JA.EMPRESA_PADRAO, D.empresa || {});
  // forma de pagamento escolhida no app; sem ela, usa o Pix (CPF) padrão
  const F = Object.assign({ tipo: 'pix_pf' }, D.forma || {});
  if ((F.tipo === 'pix_pf' || F.tipo === 'pix_pj') && !F.chave) Object.assign(F, F.tipo === 'pix_pf' ? E.pix : {}, D.pix_dados || {});
  const ehPix = F.tipo === 'pix_pf' || F.tipo === 'pix_pj';
  const temValor = D.valor != null && D.valor !== '';
  const itens = D.itens || [];
  const valorTxt = temValor ? BRL(D.valor) : '';

  const TITULO = { pix_pf: 'Pagamento via Pix', pix_pj: 'Pagamento via Pix', ted: 'Transferência bancária', boleto: 'Pagamento por boleto', cartao: 'Pagamento com cartão' };
  const ROTULO = { pix_pf: 'PIX', pix_pj: 'PIX · PJ', ted: 'TED', boleto: 'BOLETO', cartao: 'CARTÃO' };

  // ícone grande à esquerda do quadro principal
  const ICONE = {
    pix: '<svg viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="17" height="17" rx="3" transform="rotate(45 12 12)" fill="#C8F53B" stroke="#14301F" stroke-width=".9"/><path d="M8.6 12 L12 8.6 L15.4 12 L12 15.4 Z" fill="#14301F"/></svg>',
    ted: '<svg viewBox="0 0 24 24"><path d="M3 9 L12 4 L21 9 Z" fill="#C8F53B" stroke="#14301F" stroke-width=".9"/><path d="M5 10 V17 M9 10 V17 M15 10 V17 M19 10 V17 M3 19 H21" stroke="#14301F" stroke-width="1.6"/></svg>',
    boleto: '<svg viewBox="0 0 24 24"><rect x="2.5" y="5" width="19" height="14" rx="1.5" fill="#C8F53B" stroke="#14301F" stroke-width=".9"/><path d="M5 8 V16 M7 8 V16 M8.5 8 V16 M11 8 V16 M12.5 8 V16 M15 8 V16 M17 8 V16 M19 8 V16" stroke="#14301F" stroke-width="1"/></svg>',
    cartao: '<svg viewBox="0 0 24 24"><rect x="2.5" y="5" width="19" height="14" rx="2" fill="#C8F53B" stroke="#14301F" stroke-width=".9"/><rect x="2.5" y="8" width="19" height="2.6" fill="#14301F"/><rect x="5" y="14" width="6" height="2" fill="#14301F"/></svg>',
  };

  let principal = '', dados = [], passos = [];
  const so = (s) => String(s || '').replace(/\D/g, '');
  if (ehPix) {
    const semPontos = so(F.chave);
    principal = `
      <div class="lbl">Chave Pix${F.tipo_chave ? ' · ' + esc(F.tipo_chave) : ''}</div>
      <div class="key num">${esc(F.chave || '—')}</div>
      ${semPontos && semPontos !== F.chave && /^[\d.\-/ ]+$/.test(F.chave || '') ? `<div class="s">Sem pontos e traço: ${esc(semPontos)}</div>` : ''}`;
    dados = [['Favorecido', F.favorecido], ['Banco', F.banco], [F.tipo === 'pix_pj' ? 'CNPJ' : 'Tipo de chave', F.tipo === 'pix_pj' ? F.documento : F.tipo_chave]];
    passos = [
      'No app do seu banco, escolha <strong>Pix</strong> e depois <strong>Transferir com chave</strong>.',
      `Digite a chave <strong>${esc(F.chave || '')}</strong> e ${temValor ? `o valor de <strong>${valorTxt}</strong>` : 'o valor combinado'}.`,
      `Confirme o favorecido <strong>${esc(F.favorecido || '')}${F.banco ? ' · ' + esc(F.banco) : ''}</strong> e conclua.`,
    ];
  } else if (F.tipo === 'ted') {
    principal = `
      <div class="lbl">Banco · agência · conta</div>
      <div class="key num" data-caber>${esc([F.banco, F.agencia ? 'Ag. ' + F.agencia : '', F.conta ? 'C/C ' + F.conta : ''].filter(Boolean).join(' · ') || '—')}</div>
      ${F.tipo_conta ? `<div class="s">${esc(F.tipo_conta)}</div>` : ''}`;
    dados = [['Titular', F.favorecido], ['CPF / CNPJ', F.documento], ['Banco', F.banco]];
    passos = [
      'No app do seu banco, escolha <strong>Transferência</strong> (TED ou Pix por agência e conta).',
      `Informe banco <strong>${esc(F.banco || '')}</strong>, agência <strong>${esc(F.agencia || '')}</strong> e conta <strong>${esc(F.conta || '')}</strong>${temValor ? ` e o valor de <strong>${valorTxt}</strong>` : ''}.`,
      `Confira o titular <strong>${esc(F.favorecido || '')}</strong> e conclua.`,
    ];
  } else if (F.tipo === 'boleto') {
    principal = `
      <div class="lbl">Linha digitável</div>
      <div class="key num" data-caber style="font-size:15pt;letter-spacing:.02em">${esc(F.linha || 'O boleto segue em anexo ou por e-mail.')}</div>
      ${F.observacao ? `<div class="s">${esc(F.observacao)}</div>` : ''}`;
    dados = [['Beneficiário', F.favorecido || E.nome], ['Banco', F.banco], ['Vencimento', D.vencimento ? dataCurta(D.vencimento) : 'conforme o boleto']];
    passos = [
      'No app do seu banco, escolha <strong>Pagar boleto</strong>.',
      'Leia o código de barras ou digite a <strong>linha digitável</strong>.',
      `Confira o beneficiário${temValor ? ` e o valor de <strong>${valorTxt}</strong>` : ''} e pague até o vencimento.`,
    ];
  } else {
    principal = `
      <div class="lbl">${F.link ? 'Link de pagamento' : 'Pagamento no cartão'}</div>
      <div class="key num" data-caber style="font-size:${F.link ? '13pt' : '22pt'}">${esc(F.link || 'Crédito ou débito')}</div>
      ${F.parcelas ? `<div class="s">Em até ${esc(F.parcelas)}</div>` : ''}`;
    dados = [['Bandeiras', F.bandeiras || 'Visa · Mastercard · Elo'], ['Parcelamento', F.parcelas || 'a combinar'], ['Recebedor', F.favorecido || E.nome]];
    passos = F.link ? [
      'Abra o <strong>link de pagamento</strong> acima no celular ou no computador.',
      `Escolha crédito ou débito${temValor ? ` e confira o valor de <strong>${valorTxt}</strong>` : ''}.`,
      'Preencha os dados do cartão e conclua.',
    ] : [
      'O pagamento é feito na <strong>maquininha</strong>, no local do serviço.',
      `Escolha crédito ou débito${temValor ? ` para o valor de <strong>${valorTxt}</strong>` : ''}.`,
      'Guarde o comprovante impresso ou enviado por SMS.',
    ];
  }
  const icone = ICONE[ehPix ? 'pix' : F.tipo] || ICONE.pix;

  const blocoValor = temValor ? `
    <div class="cobra">
      <div class="cv">
        <div class="lbl">Valor a pagar</div>
        <div class="v num">${valorTxt}</div>
        <div class="ext">${extenso(D.valor)}</div>
      </div>
      <div class="cd">
        ${D.referente ? `<div><div class="lbl">Referente a</div><div class="t">${rich(D.referente)}</div></div>` : ''}
        ${D.cliente ? `<div><div class="lbl">Cliente</div><div class="t">${esc(D.cliente)}</div></div>` : ''}
        ${itens.length ? `<div class="its">${itens.map(i => `<div><span>${esc(i.descricao)}</span><b class="num">${BRL(i.valor)}</b></div>`).join('')}</div>` : ''}
        ${D.vencimento && F.tipo !== 'boleto' ? `<div><div class="lbl">Pagar até</div><div class="t">${dataCurta(D.vencimento)}</div></div>` : ''}
      </div>
    </div>` : '';

  const raiz = document.getElementById('doc');
  const s = document.createElement('section');
  s.className = 'page' + (temValor ? ' com-valor' : '');
  s.innerHTML = `
  <div class="band">
    <div class="marca"><img src="logo-ja.png" alt=""><div class="ja-nm"><b>J.A</b><i>ELETRICIDADES</i><small>${esc(E.assinatura_linha)}</small></div></div>
    <div class="doc">Informações de pagamento<b>${ROTULO[F.tipo] || 'PIX'}</b>${D.data ? `<span style="display:block;margin-top:1mm;font-weight:700;color:var(--tinta)">${esc(D.cidade || 'Atibaia')}, ${dataCurta(D.data)}</span>` : ''}</div>
  </div>
  <div class="wrap">
    <div class="k">Dados para pagamento</div>
    <h1>${esc(D.titulo || TITULO[F.tipo] || 'Pagamento')}</h1>
    <p class="intro">${esc(D.intro || `Para quitar os serviços prestados pela ${E.nome}, utilize os dados abaixo. Confira o nome do favorecido antes de confirmar o pagamento.`)}</p>
    ${blocoValor}
    <div class="chave">
      ${icone}
      <div style="min-width:0">${principal}</div>
    </div>
    <div class="dados">
      ${dados.map(([r, v]) => `<div><div class="lbl">${esc(r)}</div><div class="v">${esc(v || '—')}</div></div>`).join('')}
    </div>
    <div class="passos">
      <div class="lbl">Como pagar</div>
      <div class="lst">${passos.map((p, i) => `<div><b>0${i + 1}</b><span>${p}</span></div>`).join('')}</div>
    </div>
    <div class="aviso">
      <svg viewBox="0 0 24 24"><path d="M5 2.5 H15 L19 6.5 V21.5 H5 Z" fill="#14301F"/><path d="M8 9 H14 M8 12.5 H16" stroke="#E9EDE4" stroke-width="1.3"/><circle cx="15.5" cy="17" r="3.6" fill="#C8F53B" stroke="#14301F" stroke-width=".8"/><path d="M13.8 17 L15.1 18.3 L17.3 15.9" stroke="#14301F" stroke-width="1.2" fill="none"/></svg>
      <div>Após o pagamento, envie o comprovante pelo WhatsApp <b>${esc(E.telefone)}</b>.</div>
    </div>
  </div>
  ${rodape(E, 'telefone')}`;
  raiz.appendChild(s);
  JA.ajustarValores();
}
(async () => {
  await JA.preparar(['logo-ja.png']);
  await desenhar();
  window.__PRONTO = true;
  JA.ouvirPrevia(desenhar);
})();
