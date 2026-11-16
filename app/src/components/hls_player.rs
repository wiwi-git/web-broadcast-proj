//! HLS 재생기. web/src/components/HlsPlayer.tsx와 같은 역할이다.
//!
//! 재생은 WebView 안의 `<video>`가 한다. macOS·iOS의 WebView(WKWebView)처럼 HLS를 바로 재생할 수 있으면
//! 주소만 넣고, 그렇지 않은 WebView(Windows의 WebView2, Android 등)와 브라우저에서는 hls.js를 불러 재생한다.
//! 화질 선택은 아직 없다 (hls.js가 네트워크에 맞춰 고른다).

use std::sync::atomic::{AtomicU64, Ordering};

use dioxus::prelude::*;

use crate::config;

const HLS_JS: Asset = asset!("/assets/hls.light.min.js");

/// 같은 화면에 재생기가 여럿 있어도 서로 구분되도록 붙이는 번호
fn next_element_id() -> String {
    static NEXT: AtomicU64 = AtomicU64::new(0);
    format!("hls-player-{}", NEXT.fetch_add(1, Ordering::Relaxed))
}

/// 재생기를 붙이는 스크립트. 문자열은 JSON으로 넣어 따옴표 등이 깨지지 않게 한다.
fn attach_script(element_id: &str, src: &str) -> String {
    let id = serde_json::to_string(element_id).unwrap_or_default();
    let src = serde_json::to_string(src).unwrap_or_default();
    let hls_js = serde_json::to_string(&HLS_JS.to_string()).unwrap_or_default();
    format!(
        r#"
        const video = document.getElementById({id});
        if (!video) return;
        const src = {src};
        if (video.canPlayType('application/vnd.apple.mpegurl')) {{
          video.src = src;
          return;
        }}
        if (!window.Hls) {{
          await new Promise((resolve, reject) => {{
            const script = document.createElement('script');
            script.src = {hls_js};
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
          }});
        }}
        if (!window.Hls.isSupported()) {{
          video.src = src;
          return;
        }}
        const hls = new window.Hls();
        // 네트워크가 잠깐 흔들려도 다시 시도한다
        hls.on(window.Hls.Events.ERROR, (_, data) => {{
          if (!data.fatal) return;
          if (data.type === window.Hls.ErrorTypes.NETWORK_ERROR) setTimeout(() => hls.startLoad(), 2000);
          else if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
        }});
        hls.loadSource(src);
        hls.attachMedia(video);
        (window.__hlsPlayers ??= {{}})[{id}] = hls;
        "#
    )
}

fn detach_script(element_id: &str) -> String {
    let id = serde_json::to_string(element_id).unwrap_or_default();
    format!("window.__hlsPlayers?.[{id}]?.destroy(); if (window.__hlsPlayers) delete window.__hlsPlayers[{id}];")
}

/// `src`가 바뀌면 다시 붙도록 부모에서 `key`를 src나 동영상 id로 준다.
#[component]
pub fn HlsPlayer(src: String, poster: Option<String>) -> Element {
    let element_id = use_hook(next_element_id);
    let src = config::resolve(&src);
    let poster = poster.map(|poster| config::resolve(&poster));

    let mounted_id = element_id.clone();
    let mounted_src = src.clone();
    let drop_id = element_id.clone();
    use_drop(move || {
        document::eval(&detach_script(&drop_id));
    });

    rsx! {
        div { class: "player",
            video {
                id: "{element_id}",
                controls: true,
                playsinline: true,
                poster,
                onmounted: move |_| {
                    let script = attach_script(&mounted_id, &mounted_src);
                    async move {
                        let _ = document::eval(&script).await;
                    }
                },
            }
        }
    }
}
