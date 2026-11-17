//! HLS 재생기. web/src/components/HlsPlayer.tsx와 같은 역할이다.
//!
//! 재생은 WebView 안의 `<video>`가 한다. 어느 쪽을 먼저 쓰는지는 빌드마다 다르다.
//! - 웹 빌드: 웹과 같이 hls.js를 먼저 쓴다. 최신 Chromium은 자체 HLS 재생을 지원한다고 답하지만
//!   저지연(LL-HLS) 라이브를 재생하지 못한다.
//! - 데스크톱·모바일: `<video>`가 HLS를 바로 재생할 수 있으면(macOS·iOS의 WKWebView) 그대로 쓴다.
//!   hls.js는 영상 조각을 JS로 받으므로 앱 origin(dioxus://)에서 서버로 가는 교차 출처 요청이 되는데,
//!   서버의 /media는 CORS를 허용하지 않아 재생이 멈춘다. 자체 재생은 CORS가 필요 없다.
//! hls.js로 재생하면 웹처럼 화질을 고를 수 있다. 자체 HLS 재생일 때는 화질 선택이 없다.

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
    let prefer_native = cfg!(not(target_arch = "wasm32"));
    format!(
        r#"
        const video = document.getElementById({id});
        if (!video) return;
        const src = {src};
        if ({prefer_native} && video.canPlayType('application/vnd.apple.mpegurl')) {{
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
        // 화질 목록을 Rust로 보낸다. 높이는 세로 영상도 고려해 짧은 변으로 잡는다
        hls.on(window.Hls.Events.MANIFEST_PARSED, (_, data) => {{
          dioxus.send(data.levels.map((level, index) => ({{ index, height: Math.min(level.width, level.height) }})));
        }});
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

/// hls.js가 알려 준 화질 하나
#[derive(Debug, Clone, PartialEq, serde::Deserialize)]
struct Level {
    index: i64,
    height: i64,
}

/// hls.js에서 -1은 네트워크에 맞춰 고르는 자동 화질이다
const AUTO: i64 = -1;

/// `src`가 바뀌면 다시 붙도록 부모에서 `key`를 src나 동영상 id로 준다.
#[component]
pub fn HlsPlayer(src: String, poster: Option<String>) -> Element {
    let element_id = use_hook(next_element_id);
    let mut levels = use_signal(Vec::<Level>::new);
    let mut selected = use_signal(|| AUTO);
    let src = config::resolve(&src);
    let poster = poster.map(|poster| config::resolve(&poster));

    let mounted_id = element_id.clone();
    let mounted_src = src.clone();
    let drop_id = element_id.clone();
    let quality_id = element_id.clone();
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
                        let mut player = document::eval(&script);
                        while let Ok(mut parsed) = player.recv::<Vec<Level>>().await {
                            parsed.sort_by(|a, b| b.height.cmp(&a.height));
                            levels.set(parsed);
                        }
                    }
                },
            }
            if levels.read().len() > 1 {
                label { class: "quality",
                    "화질"
                    select {
                        value: "{selected}",
                        onchange: move |e| {
                            let index = e.value().parse().unwrap_or(AUTO);
                            selected.set(index);
                            let id = serde_json::to_string(&quality_id).unwrap_or_default();
                            document::eval(&format!("const p = window.__hlsPlayers?.[{id}]; if (p) p.currentLevel = {index};"));
                        },
                        option { value: "{AUTO}", "자동" }
                        for level in levels() {
                            option { key: "{level.index}", value: "{level.index}", "{level.height}p" }
                        }
                    }
                }
            }
        }
    }
}
