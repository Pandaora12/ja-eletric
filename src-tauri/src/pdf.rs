//! Geração de PDF dos documentos da J.A.
//!
//! Os modelos (HTML/CSS/JS) ficam em `resources/modelos`. Para gerar um PDF:
//! 1. copia os modelos para uma pasta temporária;
//! 2. grava `dados.js` com os dados do documento;
//! 3. imprime o HTML com o navegador do sistema em modo headless
//!    (Edge no Windows, que já vem instalado; Chrome/Edge/Chromium no Mac e Linux).
//!
//! O navegador às vezes não fecha sozinho no Windows. Por isso não esperamos ele terminar:
//! acompanhamos o arquivo PDF e, quando ele para de crescer, encerramos o processo.
//! Se nada sair em 45 s, tentamos de novo com outra opção de modo headless.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::thread::sleep;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

const SEM_JANELA: u32 = 0x0800_0000; // CREATE_NO_WINDOW

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
        v.push(PathBuf::from(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"));
        v.push(PathBuf::from(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"));
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

/// Registro simples de erros, para diagnosticar problemas no computador do usuário.
pub fn registrar(pasta_log: &Path, msg: &str) {
    let _ = fs::create_dir_all(pasta_log);
    if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(pasta_log.join("ja-documentos.log")) {
        let t = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
        let _ = writeln!(f, "[{t}] {msg}");
    }
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

fn url_de_arquivo(p: &Path) -> String {
    let s = p.to_string_lossy().replace('\\', "/");
    let mut out = String::new();
    for c in s.chars() {
        match c {
            ' ' => out.push_str("%20"),
            '#' => out.push_str("%23"),
            '%' => out.push_str("%25"),
            '?' => out.push_str("%3F"),
            _ => out.push(c),
        }
    }
    if out.starts_with('/') { format!("file://{out}") } else { format!("file:///{out}") }
}

/// Espera o PDF aparecer e parar de crescer; encerra o navegador ao fim. true = gerou.
fn imprimir(navegador: &Path, url: &str, perfil: &Path, saida: &Path, modo_headless: &str, prazo: Duration) -> Result<bool, String> {
    let _ = fs::remove_file(saida);
    let mut cmd = Command::new(navegador);
    cmd.arg(modo_headless)
        .arg("--disable-gpu")
        .arg("--no-first-run")
        .arg("--no-default-browser-check")
        .arg("--disable-extensions")
        .arg("--disable-sync")
        .arg("--no-sandbox")
        .arg("--allow-file-access-from-files")
        .arg(format!("--user-data-dir={}", perfil.display()))
        .arg("--no-pdf-header-footer")
        .arg("--run-all-compositor-stages-before-draw")
        .arg("--virtual-time-budget=10000")
        .arg(format!("--print-to-pdf={}", saida.display()))
        .arg(url)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    #[cfg(windows)]
    cmd.creation_flags(SEM_JANELA);
    #[cfg(not(windows))]
    let _ = SEM_JANELA;

    let mut filho = cmd.spawn().map_err(|e| format!("não consegui abrir o navegador ({}): {e}", navegador.display()))?;
    let inicio = Instant::now();
    let mut ultimo = 0u64;
    let mut estavel_desde: Option<Instant> = None;
    loop {
        let tam = fs::metadata(saida).map(|m| m.len()).unwrap_or(0);
        if tam > 0 {
            if tam == ultimo {
                let desde = *estavel_desde.get_or_insert_with(Instant::now);
                if desde.elapsed() >= Duration::from_millis(700) {
                    break;
                }
            } else {
                ultimo = tam;
                estavel_desde = None;
            }
        }
        if let Ok(Some(_)) = filho.try_wait() {
            // terminou: dá um instante para o arquivo aparecer por completo
            sleep(Duration::from_millis(300));
            break;
        }
        if inicio.elapsed() > prazo {
            break;
        }
        sleep(Duration::from_millis(200));
    }
    let _ = filho.kill();
    let _ = filho.wait();
    let ok = fs::metadata(saida).map(|m| m.len() > 1000).unwrap_or(false);
    Ok(ok)
}

/// Gera o PDF em `saida` (caminho completo do arquivo).
/// `modelo`: "orcamento" | "recibo" | "pix". `dados`: JSON do documento.
pub fn gerar_em(modelos: &Path, trabalho: &Path, saida: &Path, modelo: &str, dados: &serde_json::Value) -> Result<(), String> {
    if !["orcamento", "recibo", "pix"].contains(&modelo) {
        return Err(format!("Modelo desconhecido: {modelo}"));
    }
    if !modelos.join(format!("{modelo}.html")).exists() {
        return Err(format!("Os modelos dos documentos não foram encontrados em {}. Reinstale o app.", modelos.display()));
    }
    let navegador = achar_navegador().ok_or(
        "Não encontrei o Microsoft Edge nem o Google Chrome neste computador. Instale um deles para gerar o PDF.".to_string(),
    )?;

    let carimbo = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0);
    let pasta = trabalho.join(format!("render-{carimbo}"));
    copiar_pasta(modelos, &pasta).map_err(|e| format!("Não consegui preparar os modelos: {e}"))?;
    let js = format!("window.DOC = {};\n", serde_json::to_string(dados).map_err(|e| e.to_string())?);
    fs::write(pasta.join("dados.js"), js).map_err(|e| e.to_string())?;
    if let Some(p) = saida.parent() {
        fs::create_dir_all(p).map_err(|e| format!("Não consegui criar a pasta de destino: {e}"))?;
    }

    // gera primeiro numa pasta temporária (caminho simples) e depois copia para o destino
    let temp = pasta.join("saida.pdf");
    let url = url_de_arquivo(&pasta.join(format!("{modelo}.html")));
    let mut ok = false;
    let mut detalhe = String::new();
    for (i, modo) in ["--headless=new", "--headless"].iter().enumerate() {
        let perfil = pasta.join(format!("perfil-{i}"));
        match imprimir(&navegador, &url, &perfil, &temp, modo, Duration::from_secs(45)) {
            Ok(true) => { ok = true; break; }
            Ok(false) => detalhe = format!("o navegador não gerou o arquivo ({modo})"),
            Err(e) => { detalhe = e; break; }
        }
    }
    let resultado = if ok {
        fs::copy(&temp, saida).map(|_| ()).map_err(|e| format!("Não consegui salvar o PDF em {}: {e}", saida.display()))
    } else {
        Err(format!("Não consegui gerar o PDF: {detalhe}. Navegador usado: {}", navegador.display()))
    };
    let _ = fs::remove_dir_all(&pasta);
    resultado
}

/// Codifica texto para ir numa URL (mensagem do WhatsApp).
pub fn codificar_url(s: &str) -> String {
    let mut out = String::new();
    for b in s.as_bytes() {
        match *b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(*b as char),
            _ => out.push_str(&format!("%{:02X}", b)),
        }
    }
    out
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
            .args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", &format!("Set-Clipboard -Path '{p}'")])
            .creation_flags(SEM_JANELA)
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

    #[test]
    fn url_codificada() {
        assert_eq!(codificar_url("Olá R$ 10"), "Ol%C3%A1%20R%24%2010");
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
        let saida = tmp.join("saida com espaço").join("Recibo teste.pdf");
        gerar_em(&raiz, &tmp, &saida, "recibo", &dados).expect("gerou");
        assert!(fs::metadata(&saida).unwrap().len() > 10_000);
    }
}
