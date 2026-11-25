/* Ponte com o app: na prévia (iframe), os dados vêm da janela principal.
   Na geração do PDF, o dados.js é reescrito com os dados reais. */
(function () {
  try {
    var k = new URLSearchParams(location.search).get('k');
    var fonte = (window.parent && window.parent !== window && window.parent.__JA_DOCS) ? window.parent : (window.opener && window.opener.__JA_DOCS ? window.opener : null);
    if (k && fonte && fonte.__JA_DOCS[k]) window.DOC = JSON.parse(JSON.stringify(fonte.__JA_DOCS[k]));
  } catch (e) { /* sem acesso à janela principal: segue com dados.js */ }
})();
