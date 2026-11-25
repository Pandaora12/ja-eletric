/* J.A Eletricidades · dados para pagamento via Pix (valor opcional) a partir de window.DOC */
(async function () {
  await JA.preparar(['logo-ja.png']);
  const { esc, rich, BRL, dataCurta, extenso, rodape } = JA;
  const D = window.DOC || {}, E = Object.assign({}, JA.EMPRESA_PADRAO, D.empresa || {});
  const PIX = Object.assign({}, E.pix, D.pix_dados || {});
  const temValor = D.valor != null && D.valor !== '';
  const semPontos = PIX.chave.replace(/\D/g, '');
  const itens = D.itens || [];

  const blocoValor = temValor ? `
    <div class="cobra">
      <div class="cv">
        <div class="lbl">Valor a pagar</div>
        <div class="v num">${BRL(D.valor)}</div>
        <div class="ext">${extenso(D.valor)}</div>
      </div>
      <div class="cd">
        ${D.referente ? `<div><div class="lbl">Referente a</div><div class="t">${rich(D.referente)}</div></div>` : ''}
        ${D.cliente ? `<div><div class="lbl">Cliente</div><div class="t">${esc(D.cliente)}</div></div>` : ''}
        ${itens.length ? `<div class="its">${itens.map(i => `<div><span>${esc(i.descricao)}</span><b class="num">${BRL(i.valor)}</b></div>`).join('')}</div>` : ''}
        ${D.vencimento ? `<div><div class="lbl">Vencimento</div><div class="t">${dataCurta(D.vencimento)}</div></div>` : ''}
      </div>
    </div>` : '';

  const passo2 = temValor ? `Digite a chave CPF <strong>${esc(PIX.chave)}</strong> e o valor de <strong>${BRL(D.valor)}</strong>.` : `Digite a chave CPF <strong>${esc(PIX.chave)}</strong> e o valor combinado.`;
  const raiz = document.getElementById('doc');
  const s = document.createElement('section');
  s.className = 'page' + (temValor ? ' com-valor' : '');
  s.innerHTML = `
  <div class="band">
    <div class="marca"><img src="logo-ja.png" alt=""><div class="ja-nm"><b>J.A</b><i>ELETRICIDADES</i><small>${esc(E.assinatura_linha)}</small></div></div>
    <div class="doc">Informações de pagamento<b>PIX</b>${D.data ? `<span style="display:block;margin-top:1mm;font-weight:700;color:var(--tinta)">${esc(D.cidade || 'Atibaia')}, ${dataCurta(D.data)}</span>` : ''}</div>
  </div>
  <div class="wrap">
    <div class="k">Dados para pagamento</div>
    <h1>${esc(D.titulo || 'Pagamento via Pix')}</h1>
    <p class="intro">${esc(D.intro || `Para quitar os serviços prestados pela ${E.nome}, utilize a chave Pix abaixo. Confira o nome do favorecido antes de confirmar a transferência.`)}</p>
    ${blocoValor}
    <div class="chave">
      <svg viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="17" height="17" rx="3" transform="rotate(45 12 12)" fill="#C8F53B" stroke="#14301F" stroke-width=".9"/><path d="M8.6 12 L12 8.6 L15.4 12 L12 15.4 Z" fill="#14301F"/></svg>
      <div>
        <div class="lbl">Chave Pix · ${esc(PIX.tipo)}</div>
        <div class="key num">${esc(PIX.chave)}</div>
        ${semPontos !== PIX.chave ? `<div class="s">Sem pontos e traço: ${esc(semPontos)}</div>` : ''}
      </div>
    </div>
    <div class="dados">
      <div><div class="lbl">Favorecido</div><div class="v">${esc(PIX.favorecido)}</div></div>
      <div><div class="lbl">Banco</div><div class="v">${esc(PIX.banco)}</div></div>
      <div><div class="lbl">Tipo de chave</div><div class="v">${esc(PIX.tipo)}</div></div>
    </div>
    <div class="passos">
      <div class="lbl">Como pagar</div>
      <div class="lst">
        <div><b>01</b><span>No app do seu banco, escolha <strong>Pix</strong> e depois <strong>Transferir com chave</strong>.</span></div>
        <div><b>02</b><span>${passo2}</span></div>
        <div><b>03</b><span>Confirme o favorecido <strong>${esc(PIX.favorecido)} · ${esc(PIX.banco)}</strong> e conclua.</span></div>
      </div>
    </div>
    <div class="aviso">
      <svg viewBox="0 0 24 24"><path d="M5 2.5 H15 L19 6.5 V21.5 H5 Z" fill="#14301F"/><path d="M8 9 H14 M8 12.5 H16" stroke="#E9EDE4" stroke-width="1.3"/><circle cx="15.5" cy="17" r="3.6" fill="#C8F53B" stroke="#14301F" stroke-width=".8"/><path d="M13.8 17 L15.1 18.3 L17.3 15.9" stroke="#14301F" stroke-width="1.2" fill="none"/></svg>
      <div>Após o pagamento, envie o comprovante pelo WhatsApp <b>${esc(E.telefone)}</b>.</div>
    </div>
  </div>
  ${rodape(E, 'telefone')}`;
  raiz.appendChild(s);
  window.__PRONTO = true;
})();
