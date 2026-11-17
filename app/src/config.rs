//! 서버 주소.
//!
//! 데스크톱·모바일 앱에는 origin이 없으므로, 서버가 주는 상대 경로(`/api`, `/media`, `/hls`, `/whip`)를
//! 각 서버의 절대 주소로 바꿔야 한다. 이 규칙은 web/vite.config.ts의 proxy와 같다.
//!
//! 웹 빌드는 `/api`, `/media`를 화면과 같은 origin으로 보낸다 (Dioxus.toml의 proxy).
//! dx의 proxy는 경로 앞부분을 떼어 주지 못하므로 `/hls`, `/whip`은 웹 빌드에서도 MediaMTX로 바로 보낸다.
//!
//! 기본값은 로컬 개발용이다. 빌드할 때 환경 변수로 바꿀 수 있다.
//! - `API_ORIGIN`: Spring 서버 (`/api`, `/media`)
//! - `HLS_ORIGIN`: MediaMTX HLS (`/hls` 접두사를 떼고 넘긴다)
//! - `WHIP_ORIGIN`: MediaMTX WHIP (`/whip` 접두사를 떼고 넘긴다)
//! - `WEB_ORIGIN`: 웹(../web). 앱은 후원 결제를 기본 브라우저의 웹 화면에서 한다

/// 안드로이드 에뮬레이터에서 개발 PC의 localhost는 10.0.2.2로 보인다
#[cfg(target_os = "android")]
const DEV_HOST: &str = "http://10.0.2.2";
#[cfg(not(target_os = "android"))]
const DEV_HOST: &str = "http://localhost";

fn api_origin() -> String {
    option_env!("API_ORIGIN").map_or_else(|| format!("{DEV_HOST}:8180"), str::to_owned)
}

fn hls_origin() -> String {
    option_env!("HLS_ORIGIN").map_or_else(|| format!("{DEV_HOST}:8888"), str::to_owned)
}

fn whip_origin() -> String {
    option_env!("WHIP_ORIGIN").map_or_else(|| format!("{DEV_HOST}:8889"), str::to_owned)
}

/// 웹(React) 주소. 앱의 후원 결제는 이 주소의 후원 화면을 기본 브라우저로 열어 진행한다
// 앱(데스크톱·모바일)에서만 쓴다
#[cfg_attr(target_arch = "wasm32", allow(dead_code))]
pub fn web_origin() -> String {
    option_env!("WEB_ORIGIN").map_or_else(|| format!("{DEV_HOST}:5173"), str::to_owned)
}

/// 앱을 여는 딥링크 스킴 (Dioxus.toml의 [deep_links])
// 앱(데스크톱·모바일)에서만 쓴다
#[cfg_attr(target_arch = "wasm32", allow(dead_code))]
pub const APP_SCHEME: &str = "webbroadcast";

/// 서버가 준 경로를 요청할 수 있는 주소로 바꾼다. 이미 절대 주소면 그대로 둔다.
pub fn resolve(path: &str) -> String {
    if !path.starts_with('/') {
        return path.to_owned();
    }
    if let Some(rest) = path.strip_prefix("/hls") {
        format!("{}{rest}", hls_origin())
    } else if let Some(rest) = path.strip_prefix("/whip") {
        format!("{}{rest}", whip_origin())
    } else if cfg!(target_arch = "wasm32") {
        path.to_owned()
    } else {
        format!("{}{path}", api_origin())
    }
}
