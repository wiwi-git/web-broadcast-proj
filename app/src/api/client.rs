//! 서버와 주고받는 공통 HTTP 처리.
//!
//! 로그인은 세션 쿠키로 유지한다 (ADR-0003). 상태를 바꾸는 요청에는 서버가 `XSRF-TOKEN` 쿠키로 준
//! CSRF 토큰을 `X-XSRF-TOKEN` 헤더로 돌려보낸다. 웹은 브라우저가 쿠키를 관리하고,
//! 데스크톱·모바일은 앱이 가진 쿠키 저장소가 같은 일을 한다.

use reqwest::{Method, RequestBuilder, Response, StatusCode};
use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::config;

#[derive(Debug, Clone, PartialEq, thiserror::Error)]
pub enum ApiError {
    /// 서버가 ProblemDetail로 보낸 오류. message는 사용자에게 보여줄 문구다.
    #[error("{message}")]
    Server {
        status: u16,
        message: String,
        /// ProblemDetail의 나머지 필드 (예: activeBroadcastId)
        problem: serde_json::Value,
    },
    #[error("서버에 연결하지 못했습니다.")]
    Network(String),
    /// 응답은 왔지만 앱이 아는 모양이 아니다. 대개 서버 응답과 api 타입이 어긋난 것이다.
    #[error("서버 응답을 읽지 못했습니다.")]
    Decode(String),
}

impl ApiError {
    pub fn status(&self) -> Option<u16> {
        match self {
            ApiError::Server { status, .. } => Some(*status),
            ApiError::Network(_) | ApiError::Decode(_) => None,
        }
    }
}

pub type ApiResult<T> = Result<T, ApiError>;

impl From<reqwest::Error> for ApiError {
    fn from(error: reqwest::Error) -> Self {
        if error.is_decode() {
            ApiError::Decode(error.to_string())
        } else {
            ApiError::Network(error.to_string())
        }
    }
}

#[cfg(not(target_arch = "wasm32"))]
mod native {
    use std::sync::{Arc, LazyLock};

    use reqwest::cookie::{CookieStore, Jar};

    pub struct Http {
        pub client: reqwest::Client,
        jar: Arc<Jar>,
    }

    pub static HTTP: LazyLock<Http> = LazyLock::new(|| {
        let jar = Arc::new(Jar::default());
        let client = reqwest::Client::builder()
            .cookie_provider(jar.clone())
            .build()
            .expect("HTTP 클라이언트를 만들 수 없습니다.");
        Http { client, jar }
    });

    impl Http {
        pub fn xsrf_token(&self) -> Option<String> {
            let url = crate::config::resolve("/api").parse().ok()?;
            let cookies = self.jar.cookies(&url)?;
            super::find_cookie(cookies.to_str().ok()?, "XSRF-TOKEN")
        }
    }
}

fn find_cookie(cookies: &str, name: &str) -> Option<String> {
    cookies
        .split("; ")
        .find_map(|cookie| cookie.strip_prefix(name)?.strip_prefix('='))
        .map(percent_decode)
}

/// 쿠키 값은 URL 인코딩되어 있을 수 있다. 토큰에 쓰이는 문자만 다루면 되므로 %XX만 푼다.
fn percent_decode(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        let hex = bytes.get(i + 1..i + 3).and_then(|hex| std::str::from_utf8(hex).ok());
        if let (b'%', Some(Ok(byte))) = (bytes[i], hex.map(|hex| u8::from_str_radix(hex, 16))) {
            out.push(byte);
            i += 3;
            continue;
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

#[cfg(not(target_arch = "wasm32"))]
fn builder(method: Method, url: &str) -> RequestBuilder {
    native::HTTP.client.request(method, url)
}

#[cfg(not(target_arch = "wasm32"))]
fn xsrf_token() -> Option<String> {
    native::HTTP.xsrf_token()
}

#[cfg(target_arch = "wasm32")]
fn builder(method: Method, url: &str) -> RequestBuilder {
    // reqwest는 wasm에서 상대 경로를 받지 않으므로 현재 origin을 붙인다
    let origin = web_sys::window()
        .and_then(|window| window.location().origin().ok())
        .unwrap_or_default();
    reqwest::Client::new().request(method, format!("{origin}{url}"))
}

#[cfg(target_arch = "wasm32")]
fn xsrf_token() -> Option<String> {
    use wasm_bindgen::JsCast;
    let document = web_sys::window()?.document()?.dyn_into::<web_sys::HtmlDocument>().ok()?;
    find_cookie(&document.cookie().ok()?, "XSRF-TOKEN")
}

fn request(method: Method, path: &str) -> RequestBuilder {
    let changes_state = method != Method::GET;
    let builder = builder(method, &config::resolve(path));
    match xsrf_token() {
        Some(token) if changes_state => builder.header("X-XSRF-TOKEN", token),
        _ => builder,
    }
}

/// 서버는 오류를 ProblemDetail로 보내며, detail이 사용자에게 보여줄 문구다.
async fn check(response: Response) -> ApiResult<Response> {
    let status = response.status();
    if status.is_success() {
        return Ok(response);
    }
    let body = response.text().await.unwrap_or_default();
    let problem: serde_json::Value = serde_json::from_str(&body).unwrap_or_default();
    let message = problem
        .get("detail")
        .and_then(|detail| detail.as_str())
        .map_or_else(|| format!("요청에 실패했습니다. ({})", status.as_u16()), str::to_owned);
    Err(ApiError::Server { status: status.as_u16(), message, problem })
}

pub async fn get_json<T: DeserializeOwned>(path: &str) -> ApiResult<T> {
    let response = check(request(Method::GET, path).send().await?).await?;
    Ok(response.json().await?)
}

pub async fn send_json<T: DeserializeOwned>(method: Method, path: &str, body: &impl Serialize) -> ApiResult<T> {
    let response = check(request(method, path).json(body).send().await?).await?;
    Ok(response.json().await?)
}

/// 본문 없이 보내고 응답 본문도 쓰지 않는 요청 (로그아웃, 삭제 등)
pub async fn send_empty(method: Method, path: &str) -> ApiResult<()> {
    let response = check(request(method, path).send().await?).await?;
    debug_assert!(response.status() == StatusCode::NO_CONTENT || response.status().is_success());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_and_decodes_cookie() {
        let cookies = "SESSION=abc; XSRF-TOKEN=a%2Bb%3D";
        assert_eq!(find_cookie(cookies, "XSRF-TOKEN").as_deref(), Some("a+b="));
        assert_eq!(find_cookie(cookies, "OTHER"), None);
    }
}
