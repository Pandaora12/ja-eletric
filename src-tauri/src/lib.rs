mod pdf;

use std::path::PathBuf;
use tauri::Manager;

/// Gera o PDF de um documento e devolve o caminho do arquivo (pasta Documentos J.A do app).
#[tauri::command]
async fn gerar_pdf(app: tauri::AppHandle, modelo: String, dados: serde_json::Value, nome: String) -> Result<String, String> {
    let modelos = app
        .path()
        .resolve("modelos", tauri::path::BaseDirectory::Resource)
        .map_err(|e| e.to_string())?;
    let trabalho = app.path().app_cache_dir().map_err(|e| e.to_string())?;
    let destino = app.path().app_data_dir().map_err(|e| e.to_string())?.join("documentos");
    tauri::async_runtime::spawn_blocking(move || pdf::gerar(&modelos, &trabalho, &destino, &modelo, &dados, &nome))
        .await
        .map_err(|e| e.to_string())?
        .map(|p| p.to_string_lossy().to_string())
}

/// Copia o PDF para onde a pessoa escolheu salvar.
#[tauri::command]
fn salvar_copia(origem: String, destino: String) -> Result<(), String> {
    std::fs::copy(&origem, &destino).map(|_| ()).map_err(|e| format!("Não consegui salvar o arquivo: {e}"))
}

/// Deixa o PDF na área de transferência para colar (Ctrl+V) na conversa do WhatsApp.
#[tauri::command]
fn copiar_arquivo(caminho: String) -> Result<(), String> {
    pdf::copiar_arquivo_area_transferencia(&PathBuf::from(caminho))
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
        .invoke_handler(tauri::generate_handler![gerar_pdf, salvar_copia, copiar_arquivo, navegador_pdf])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o app");
}
