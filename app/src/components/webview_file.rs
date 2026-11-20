//! iOS·안드로이드의 파일 선택.
//!
//! Dioxus(0.7)는 데스크톱에서 파일 입력을 누르면 웹뷰 대신 네이티브 파일 창(rfd)을 띄우는데,
//! iOS·안드로이드에는 그 창이 없어 빈 목록을 돌려준다. 그래서 버튼을 눌러도 아무 일이 없었다.
//! Dioxus는 자기가 그린 요소(조상 포함)의 파일 입력만 가로채므로, 파일 입력을 Dioxus 화면 바깥(body 바로 아래)에 두고
//! 화면의 버튼을 그 입력에 연결한 `label`로 만든다. 그러면 웹뷰 자체의 선택 창(사진 보관함, 촬영, 파일)이 뜬다.
//!
//! 고른 파일은 웹뷰 안에만 있으므로, 업로드할 때 1MB씩 base64로 받아 임시 파일에 쓴 뒤 그 경로로 보낸다.

use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};

use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use dioxus::prelude::*;
use tokio::io::AsyncWriteExt;

use crate::api::{ApiError, ApiResult};

const INPUT_ID: &str = "webview-file-input";

/// 웹뷰에서 고른 파일
#[derive(Debug, Clone, PartialEq, serde::Deserialize)]
pub struct PickedFile {
    pub name: String,
    pub size: u64,
}

/// body 아래에 숨긴 파일 입력을 만들고, 파일을 고르면 이름과 크기를 보낸다
fn setup_script(accept: &str) -> String {
    let id = serde_json::to_string(INPUT_ID).unwrap_or_default();
    let accept = serde_json::to_string(accept).unwrap_or_default();
    format!(
        r#"
        document.getElementById({id})?.remove();
        const input = document.createElement('input');
        input.type = 'file';
        input.id = {id};
        input.accept = {accept};
        // display: none이면 label로 열리지 않는 웹뷰가 있어 화면 밖에 둔다
        input.style.cssText = 'position: fixed; left: -9999px; width: 1px; height: 1px; opacity: 0;';
        input.addEventListener('change', () => {{
          const file = input.files[0];
          if (file) dioxus.send({{ name: file.name, size: file.size }});
        }});
        document.body.appendChild(input);
        await new Promise(() => {{}});
        "#
    )
}

/// 고른 파일을 1MB씩 base64로 보낸다. Rust가 받아 쓸 때까지 다음 조각을 보내지 않는다
fn read_script() -> String {
    let id = serde_json::to_string(INPUT_ID).unwrap_or_default();
    format!(
        r#"
        const file = document.getElementById({id})?.files?.[0];
        if (!file) {{ dioxus.send(null); return; }}
        const CHUNK = 1024 * 1024;
        for (let start = 0; start < file.size; start += CHUNK) {{
          const bytes = new Uint8Array(await file.slice(start, start + CHUNK).arrayBuffer());
          let binary = '';
          for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
          dioxus.send(btoa(binary));
          await dioxus.recv();
        }}
        dioxus.send(null);
        "#
    )
}

/// 파일 선택 버튼. 화면 바깥의 파일 입력과 이어진 label이다
#[component]
pub fn WebViewFilePicker(accept: String, disabled: bool, on_pick: EventHandler<PickedFile>) -> Element {
    use_hook(move || {
        let script = setup_script(&accept);
        spawn(async move {
            let mut picker = document::eval(&script);
            while let Ok(picked) = picker.recv::<PickedFile>().await {
                on_pick.call(picked);
            }
        });
    });
    use_drop(|| {
        let id = serde_json::to_string(INPUT_ID).unwrap_or_default();
        document::eval(&format!("document.getElementById({id})?.remove();"));
    });

    rsx! {
        label {
            r#for: INPUT_ID,
            class: "button file-pick",
            style: if disabled { "pointer-events: none; opacity: 0.5;" } else { "" },
            "파일 선택"
        }
    }
}

/// 고른 파일을 임시 파일로 옮긴다. 옮긴 바이트 수를 `copied`에 더한다. 다 쓰면 지우는 것은 부르는 쪽이 한다
pub async fn copy_to_temp(name: &str, copied: Arc<AtomicU64>) -> ApiResult<PathBuf> {
    let io = |e: std::io::Error| ApiError::Network(format!("파일을 준비하지 못했습니다: {e}"));
    let safe_name: String = name.chars().map(|c| if c.is_alphanumeric() || c == '.' { c } else { '_' }).collect();
    let path = std::env::temp_dir().join(format!("upload-{}-{safe_name}", std::process::id()));
    let mut out = tokio::fs::File::create(&path).await.map_err(io)?;

    let mut reader = document::eval(&read_script());
    loop {
        let chunk = reader
            .recv::<Option<String>>()
            .await
            .map_err(|e| ApiError::Network(format!("파일을 읽지 못했습니다: {e:?}")))?;
        let Some(chunk) = chunk else { break };
        let bytes = BASE64.decode(chunk).map_err(|e| ApiError::Network(format!("파일을 읽지 못했습니다: {e}")))?;
        out.write_all(&bytes).await.map_err(io)?;
        copied.fetch_add(bytes.len() as u64, Ordering::Relaxed);
        let _ = reader.send(());
    }
    out.flush().await.map_err(io)?;
    Ok(path)
}
