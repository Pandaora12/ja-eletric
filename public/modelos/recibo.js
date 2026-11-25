/* J.A Eletricidades · recibo de prestação de serviço a partir de window.DOC */
async function desenhar() {
  document.getElementById('doc').innerHTML = '';
  const { esc, rich, BRL, dataCurta, dataLonga, extenso, rodape } = JA;
  const D = window.DOC, E = Object.assign({}, JA.EMPRESA_PADRAO, D.empresa || {});
  const PIX = Object.assign({}, E.pix, D.pix_dados || {});
  const itens = D.itens || [];
  const total = D.total != null ? D.total : itens.reduce((a, i) => a + Number(i.valor || 0), 0);
  const cidade = D.cidade || 'Atibaia';
  const pg = D.pagador || {}, loc = D.local && (D.local.nome || D.local.cidade) ? D.local : null;
  const pgLinha = [pg.cidade, pg.cep ? 'CEP ' + pg.cep : '', pg.telefone, pg.email].filter(Boolean).join(' · ') || pg.complemento || '';
  const hora = (h) => (h || '').trim();
  const horario = hora(D.hora_inicio) && hora(D.hora_fim) ? `${hora(D.hora_inicio)} às ${hora(D.hora_fim)}`
    : hora(D.hora_inicio) ? `início às ${hora(D.hora_inicio)}` : hora(D.hora_fim) ? `concluído às ${hora(D.hora_fim)}` : '';
  const TIPO = { servico: ['mo', 'Serviço'], material: ['mt', 'Material'] };
  const porTipo = t => itens.filter(i => (i.tipo || 'servico') === t).reduce((a, i) => a + Number(i.valor || 0), 0);
  const resumo = [['Serviço', porTipo('servico')], ['Material', porTipo('material')]].filter(r => r[1]).map(r => `${r[0]} ${BRL(r[1])}`).join(' · ');

  const docPg = pg.documento ? `, ${esc(pg.documento)}` : '';
  const ref = D.referente || 'ao serviço e ao material descritos abaixo';
  const ondeTxt = loc && loc.nome ? `, no ${esc(loc.nome)}${loc.cidade ? `, em ${esc(loc.cidade)}` : ''}` : '';
  const texto = D.texto ? rich(D.texto)
    : `${D.pago === false ? 'Declaro que é devida por' : 'Recebi de'} <b>${esc(pg.nome)}</b>${docPg}, a importância de <b>${BRL(total)} (${extenso(total)})</b>, referente ${rich(ref)}${ondeTxt}.`;

  const raiz = document.getElementById('doc');
  const s = document.createElement('section');
  s.className = 'page';
  s.innerHTML = `
  <div class="band">
    <div class="marca"><img src="logo-ja.png" alt=""><div class="ja-nm"><b>J.A</b><i>ELETRICIDADES</i><small>${esc(E.assinatura_linha)}</small></div></div>
    <div class="doc">${esc(D.rotulo_doc || 'Recibo de prestação de serviço')}${D.numero ? `<b>Nº ${esc(D.numero)}</b>` : ''}<strong>${esc(cidade)}, ${dataCurta(D.data)}</strong></div>
  </div>
  <div class="wrap">
    <div class="hero">
      <div>
        <div class="k">${esc(D.rotulo || 'Prestação de serviço')}</div>
        <h1>${esc(D.titulo || 'RECIBO')}</h1>
        <p>${texto}</p>
      </div>
      <div class="valor">
        <div class="lbl">Valor total</div>
        <div><div class="v num">${BRL(total)}</div><div class="ext">${extenso(total)}</div></div>
        <div class="ext" style="color:#949D95">${esc(resumo)}</div>
      </div>
    </div>
    <div class="boxes"${loc || horario ? '' : ' style="grid-template-columns:1fr"'}>
      <div class="box">
        <div><div class="lbl">Pagador</div><div class="v">${esc(pg.nome)}${pg.documento ? `<small>${esc(pg.documento)}</small>` : ''}</div></div>
        ${pg.endereco || pgLinha ? `<div><div class="lbl">Endereço e contato</div><div class="v">${esc(pg.endereco || '')}${pgLinha ? `<small>${esc(pgLinha)}</small>` : ''}</div></div>` : ''}
      </div>
      ${loc || horario ? `<div class="box">
        ${loc ? `<div><div class="lbl">Local do serviço</div><div class="v">${esc(loc.nome || '')}${loc.cidade ? `<small>${esc(loc.cidade)}</small>` : ''}</div></div>` : ''}
        ${horario ? `<div><div class="lbl">Data e horário do serviço</div><div class="v">${dataCurta(D.data_servico || D.data)} · ${esc(horario)}</div></div>` : ''}
        <div><div class="lbl">Prestador</div><div class="v">${esc(E.nome)}</div></div>
      </div>` : ''}
    </div>
    <table class="rec">
      <thead><tr><th>Item</th><th>Descrição</th><th>Tipo</th><th class="r">Valor</th></tr></thead>
      <tbody>${itens.map((i, k) => { const t = TIPO[i.tipo || 'servico'] || TIPO.servico; return `<tr><td class="n">${String(k + 1).padStart(2, '0')}</td><td>${esc(i.descricao)}${i.detalhe ? `<small>${esc(i.detalhe)}</small>` : ''}</td><td><span class="tp ${t[0]}">${t[1]}</span></td><td class="r val">${BRL(i.valor)}</td></tr>`; }).join('')}</tbody>
      <tfoot><tr><td colspan="2"></td><td class="tot-l">Total</td><td class="tv num">${BRL(total)}</td></tr></tfoot>
    </table>
    ${D.pix === false ? '' : `<div class="pix">
      <div class="ic"><svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="3" transform="rotate(45 12 12)" fill="#C8F53B" stroke="#14301F" stroke-width="1.2"/><path d="M8.5 12 L12 8.5 L15.5 12 L12 15.5 Z" fill="#14301F"/></svg>PIX</div>
      <div><div class="lbl">Banco</div><div class="v">${esc(PIX.banco)}</div></div>
      <div><div class="lbl">Favorecido</div><div class="v">${esc(PIX.favorecido)}</div></div>
      <div><div class="lbl">Chave Pix · ${esc(PIX.tipo)}</div><div class="v key num">${esc(PIX.chave)}</div></div>
    </div>`}
    <div class="assina">
      <div class="local">${esc(cidade)}, <b>${dataLonga(D.data)}</b>.<br><span style="color:var(--tinta-2);font-size:8pt">${esc(D.fecho || 'Para clareza, firmo o presente recibo.')}</span></div>
      <div class="sigbox">
        ${D.assinatura === false ? '' : '<img class="rubrica" src="assinatura.png" alt="Assinatura">'}
        <div class="line"><b>${esc(E.responsavel)}</b><small>${esc(E.nome)} · ${esc(E.email)} · ${esc(E.telefone)}</small></div>
      </div>
    </div>
  </div>
  ${rodape(E, D.rodape || 'contato')}`;
  raiz.appendChild(s);
  JA.ajustarValores();
}
(async () => {
  await JA.preparar(['logo-ja.png', 'assinatura.png']);
  await desenhar();
  window.__PRONTO = true;
  JA.ouvirPrevia(desenhar);
})();
