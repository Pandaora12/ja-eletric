import React from "react";
import ReactDOM from "react-dom/client";
import App from "./app/App";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

// no celular (navegador), registra o modo offline / "instalar na tela inicial"
if ('serviceWorker' in navigator && !('__TAURI_INTERNALS__' in window) && location.protocol === 'https:') {
  window.addEventListener('load', () => { navigator.serviceWorker.register('/sw.js').catch(() => {}); });
}
