//! Geração de PDF dos documentos da J.A.
//!
//! Os modelos (HTML/CSS/JS) ficam em `resources/modelos`. Para gerar um PDF:
//! 1. copia os modelos para uma pasta temporária;
//! 2. grava `dados.js` com os dados do documento;
//! 3. imprime o HTML com o navegador do sistema em modo headless
//!    (Edge no Windows, que já vem instalado; Chrome/Edge/Chromium no Mac e Linux).

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

/// Caminhos onde costuma estar um navegador baseado em Chromium.
fn candidatos_navegador() -> Vec<PathBuf> {
    let mut v: Vec<PathBuf> = Vec::new();
    if let Ok(p) = std::env::var("JA_NAVEGADOR_PDF") {
        v.push(PathBuf::from(p));
    }
    #[cfg(windows)]
    {
        for base in ["ProgramFiles(x86)", "ProgramFiles", "LocalAppData"] {
            if let Ok(dir) = std::env::var(base) {
                let d = PathBuf::from(dir);
                v.push(d.join(r"Microsoft\Edge\Application\msedge.exe"));
                v.push(d.join(r"Google\Chrome\Application\chrome.exe"));
            }
        }
    }
    #[cfg(target_os = "macos")]
    {
        v.push("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome".into());
        v.push("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge".into());
        v.push("/Applications/Chromium.app/Contents/MacOS/Chromium".into());
        v.push("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser".into());
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        for n in ["/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/microsoft-edge"] {
            v.push(n.into());
        }
    }
    v
}

pub fn achar_navegador() -> Option<PathBuf> {
    candidatos_navegador().into_iter().find(|p| p.exists())
}

fn copiar_pasta(de: &Path, para: &Path) -> std::io::Result<()> {
    fs::create_dir_all(para)?;
    for e in fs::read_dir(de)? {
        let e = e?;
        let alvo = para.join(e.file_name());
        if e.file_type()?.is_dir() {
            copiar_pasta(&e.path(), &alvo)?;
        } else {
            fs::copy(e.path(), alvo)?;
        }
    }
    Ok(())
}

/// Troca caracteres que não podem ir em nome de arquivo.
pub fn nome_seguro(nome: &str) -> String {
    let n: String = nome
        .chars()
        .map(|c| if c.is_alphanumeric() || c == '-' || c == '_' || c == '.' || c == ' ' { c } else { '-' })
        .collect();
    let n = n.trim().trim_matches('.').to_string();
    if n.is_empty() { "documento".into() } else { n }
}

/// Gera o PDF e devolve o caminho do arquivo.
/// `modelo`: "orcamento" | "recibo" | "pix". `dados`: JSON do documento.
pub fn gerar(modelos: &Path, trabalho: &Path, destino_dir: &Path, modelo: &str, dados: &serde_json::Value, nome: &str) -> Result<PathBuf, String> {
    if !["orcamento", "recibo", "pix"].contains(&modelo) {
        return Err(format!("Modelo desconhecido: {modelo}"));
    }
    let navegador = achar_navegador().ok_or(
        "Não encontrei o Microsoft Edge nem o Google Chrome neste computador. Instale um deles para gerar o PDF.".to_string(),
    )?;

    let carimbo = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0);
    let pasta = trabalho.join(format!("render-{carimbo}"));
    copiar_pasta(modelos, &pasta).map_err(|e| format!("Não consegui preparar os modelos: {e}"))?;
    let js = format!("window.DOC = {};\n", serde_json::to_string(dados).map_err(|e| e.to_string())?);
    fs::write(pasta.join("dados.js"), js).map_err(|e| e.to_string())?;

    fs::create_dir_all(destino_dir).map_err(|e| e.to_string())?;
    let saida = destino_dir.join(format!("{}.pdf", nome_seguro(nome)));
    let _ = fs::remove_file(&saida);

    let html = pasta.join(format!("{modelo}.html"));
    let url = url_de_arquivo(&html);
    let perfil = pasta.join("perfil-navegador");

    let mut cmd = Command::new(&navegador);
    cmd.arg("--headless=new")
        .arg("--disable-gpu")
        .arg("--no-first-run")
        .arg("--no-default-browser-check")
        .arg("--disable-extensions")
        .arg("--allow-file-access-from-files")
        .arg(format!("--user-data-dir={}", perfil.display()))
        .arg("--no-pdf-header-footer")
        .arg("--run-all-compositor-stages-before-draw")
        .arg("--virtual-time-budget=15000")
        .arg(format!("--print-to-pdf={}", saida.display()))
        .arg(url);
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW: não abre janela preta

    let out = cmd.output().map_err(|e| format!("Falha ao abrir o navegador para gerar o PDF: {e}"))?;
    let _ = fs::remove_dir_all(&pasta);
    if !saida.exists() {
        return Err(format!(
            "O navegador não gerou o PDF. {}",
            String::from_utf8_lossy(&out.stderr).lines().last().unwrap_or("")
        ));
    }
    Ok(saida)
}

fn url_de_arquivo(p: &Path) -> String {
    let s = p.to_string_lossy().replace('\\', "/");
    let s = s.replace(' ', "%20").replace('#', "%23");
    if s.starts_with('/') { format!("file://{s}") } else { format!("file:///{s}") }
}

/// Copia o arquivo para a área de transferência, para colar (Ctrl+V) direto na conversa do WhatsApp.
pub fn copiar_arquivo_area_transferencia(caminho: &Path) -> Result<(), String> {
    if !caminho.exists() {
        return Err("Arquivo não encontrado.".into());
    }
    #[cfg(windows)]
    {
        let p = caminho.to_string_lossy().replace('\'', "''");
        let st = Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-Command", &format!("Set-Clipboard -Path '{p}'")])
            .creation_flags(0x0800_0000)
            .status()
            .map_err(|e| e.to_string())?;
        return if st.success() { Ok(()) } else { Err("Não consegui copiar o arquivo.".into()) };
    }
    #[cfg(target_os = "macos")]
    {
        let p = caminho.to_string_lossy().replace('"', "\\\"");
        let st = Command::new("osascript")
            .args(["-e", &format!("set the clipboard to (POSIX file \"{p}\")")])
            .status()
            .map_err(|e| e.to_string())?;
        return if st.success() { Ok(()) } else { Err("Não consegui copiar o arquivo.".into()) };
    }
    #[allow(unreachable_code)]
    Err("Copiar arquivo para a área de transferência não está disponível neste sistema.".into())
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn nome_de_arquivo() {
        assert_eq!(nome_seguro("Recibo REC/01: Montaresa"), "Recibo REC-01- Montaresa");
        assert_eq!(nome_seguro("..."), "documento");
    }

    /// Gera um recibo de verdade se houver navegador (JA_NAVEGADOR_PDF ou instalado).
    #[test]
    fn gera_recibo() {
        if achar_navegador().is_none() {
            eprintln!("sem navegador: teste pulado");
            return;
        }
        let raiz = Path::new(env!("CARGO_MANIFEST_DIR")).join("../public/modelos");
        let tmp = std::env::temp_dir().join("ja-teste-pdf");
        let dados = serde_json::json!({
            "data": "2026-11-25",
            "pagador": {"nome": "Cliente Teste"},
            "itens": [{"descricao": "Serviço", "tipo": "servico", "valor": 100}]
        });
        let pdf = gerar(&raiz, &tmp, &tmp.join("saida"), "recibo", &dados, "Recibo teste").expect("gerou");
        assert!(fs::metadata(&pdf).unwrap().len() > 10_000);
    }
}
