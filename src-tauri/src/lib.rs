mod pdf;

use std::path::PathBuf;
use tauri::Manager;

fn pastas(app: &tauri::AppHandle) -> Result<(PathBuf, PathBuf, PathBuf), String> {
    let modelos = app
        .path()
        .resolve("modelos", tauri::path::BaseDirectory::Resource)
        .map_err(|e| e.to_string())?;
    let trabalho = app.path().app_cache_dir().map_err(|e| e.to_string())?;
    let dados = app.path().app_data_dir().map_err(|e| e.to_string())?;
    Ok((modelos, trabalho, dados))
}

/// Gera o PDF direto no caminho que a pessoa escolheu na janela "Salvar como".
#[tauri::command]
async fn salvar_pdf(app: tauri::AppHandle, modelo: String, dados: serde_json::Value, destino: String) -> Result<String, String> {
    let (modelos, trabalho, log) = pastas(&app)?;
    let saida = PathBuf::from(&destino);
    let r = tauri::async_runtime::spawn_blocking(move || pdf::gerar_em(&modelos, &trabalho, &saida, &modelo, &dados))
        .await
        .map_err(|e| e.to_string())?;
    if let Err(e) = &r { pdf::registrar(&log, &format!("salvar_pdf: {e}")); }
    r.map(|_| destino)
}

/// Gera o PDF na pasta do app (usado no envio pelo WhatsApp) e devolve o caminho.
#[tauri::command]
async fn gerar_pdf(app: tauri::AppHandle, modelo: String, dados: serde_json::Value, nome: String) -> Result<String, String> {
    let (modelos, trabalho, log) = pastas(&app)?;
    let saida = log.join("documentos").join(format!("{}.pdf", pdf::nome_seguro(&nome)));
    let s2 = saida.clone();
    let r = tauri::async_runtime::spawn_blocking(move || pdf::gerar_em(&modelos, &trabalho, &s2, &modelo, &dados))
        .await
        .map_err(|e| e.to_string())?;
    if let Err(e) = &r { pdf::registrar(&log, &format!("gerar_pdf: {e}")); }
    r.map(|_| saida.to_string_lossy().to_string())
}

/// Abre o WhatsApp do computador com a mensagem pronta (a pessoa escolhe o contato).
/// Se o WhatsApp não estiver instalado, abre o WhatsApp Web no navegador.
#[tauri::command]
fn abrir_whatsapp(app: tauri::AppHandle, texto: String) -> Result<String, String> {
    let t = pdf::codificar_url(&texto);
    if tauri_plugin_opener::open_url(format!("whatsapp://send?text={t}"), None::<&str>).is_ok() {
        return Ok("app".into());
    }
    match tauri_plugin_opener::open_url(format!("https://web.whatsapp.com/send?text={t}"), None::<&str>) {
        Ok(_) => Ok("web".into()),
        Err(e) => {
            if let Ok((_, _, log)) = pastas(&app) { pdf::registrar(&log, &format!("abrir_whatsapp: {e}")); }
            Err(format!("Não consegui abrir o WhatsApp: {e}"))
        }
    }
}

/// Deixa o PDF na área de transferência para colar (Ctrl+V) na conversa do WhatsApp.
#[tauri::command]
fn copiar_arquivo(caminho: String) -> Result<(), String> {
    pdf::copiar_arquivo_area_transferencia(&PathBuf::from(caminho))
}

/// Mostra o arquivo na pasta (Explorador de Arquivos / Finder).
#[tauri::command]
fn mostrar_na_pasta(caminho: String) -> Result<(), String> {
    tauri_plugin_opener::reveal_item_in_dir(PathBuf::from(caminho)).map_err(|e| e.to_string())
}

/// Informa se há navegador para gerar PDF (para avisar a pessoa logo no início).
#[tauri::command]
fn navegador_pdf() -> Option<String> {
    pdf::achar_navegador().map(|p| p.to_string_lossy().to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![salvar_pdf, gerar_pdf, abrir_whatsapp, copiar_arquivo, mostrar_na_pasta, navegador_pdf])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o app");
}
