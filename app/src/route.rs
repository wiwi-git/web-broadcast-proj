//! 화면 주소. web/src/App.tsx의 라우트와 같은 경로를 쓴다.

use dioxus::prelude::*;

use crate::components::Shell;
use crate::pages::*;

#[derive(Debug, Clone, PartialEq, Routable)]
#[rustfmt::skip]
pub enum Route {
    #[layout(Shell)]
        #[route("/")]
        LiveList {},
        #[route("/live/:id")]
        LiveWatch { id: String },
        #[route("/live/:id/donate")]
        Donate { id: String },
        #[route("/donations?:tab")]
        Donations { tab: String },
        // 결제 서비스가 결제 인증을 마치고 돌려보내는 곳. 쿼리 이름(paymentKey 등)은 결제 서비스가 정한다
        #[route("/donations/success?:..query")]
        DonationSuccess { query: String },
        #[route("/donations/fail?:..query")]
        DonationFail { query: String },
        #[route("/broadcast")]
        Broadcast {},
        #[route("/videos?:page")]
        VideoList { page: u32 },
        #[route("/videos/:id")]
        VideoDetail { id: String },
        #[route("/upload")]
        Upload {},
        #[route("/login?:next")]
        Login { next: String },
        #[route("/signup?:next")]
        SignUp { next: String },
        #[route("/:..segments")]
        NotFound { segments: Vec<String> },
}

impl Route {
    /// 로그인 뒤 이 화면으로 돌아오는 로그인 화면 주소
    pub fn login_then(self) -> Route {
        Route::Login { next: self.to_string() }
    }
}

/// 로그인 뒤 돌아갈 주소. 이 앱의 경로만 받고, 알 수 없으면 첫 화면으로 보낸다.
pub fn safe_next(next: &str) -> Route {
    if next.starts_with('/') && !next.starts_with("//") {
        next.parse().unwrap_or(Route::LiveList {})
    } else {
        Route::LiveList {}
    }
}

/// 딥링크(`webbroadcast://donations?tab=received`)를 앱 화면 주소로 바꾼다.
/// 스킴 뒤의 첫 부분이 경로의 첫 부분이 된다. 알 수 없으면 첫 화면으로 보낸다.
// 앱(데스크톱·모바일)에서만 쓴다
#[cfg_attr(target_arch = "wasm32", allow(dead_code))]
pub fn app_link_route(link: &str) -> Route {
    match link.split_once("://") {
        Some((scheme, rest)) if scheme == crate::config::APP_SCHEME => safe_next(&format!("/{rest}")),
        _ => Route::LiveList {},
    }
}

/// "a=1&b=%ED%95%9C" 같은 쿼리 문자열에서 name의 값을 꺼낸다
pub fn query_param(query: &str, name: &str) -> Option<String> {
    query.trim_start_matches('?').split('&').find_map(|pair| {
        let (key, value) = pair.split_once('=').unwrap_or((pair, ""));
        (key == name).then(|| decode_component(value))
    })
}

fn decode_component(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'+' => out.push(b' '),
            b'%' => {
                let hex = bytes.get(i + 1..i + 3).and_then(|h| std::str::from_utf8(h).ok());
                if let Some(byte) = hex.and_then(|h| u8::from_str_radix(h, 16).ok()) {
                    out.push(byte);
                    i += 3;
                    continue;
                }
                out.push(b'%');
            }
            other => out.push(other),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_app_links_to_routes() {
        assert_eq!(app_link_route("webbroadcast://donations"), Route::Donations { tab: String::new() });
        assert_eq!(app_link_route("webbroadcast://donations?tab=received"), Route::Donations { tab: "received".to_owned() });
        assert_eq!(app_link_route("https://example.com/donations"), Route::LiveList {});
        assert_eq!(app_link_route("webbroadcast:////evil.example"), Route::LiveList {});
    }

    #[test]
    fn reads_query_params() {
        let query = "paymentKey=tk_1&orderId=abc&amount=5000&message=%EC%B7%A8%EC%86%8C+%EB%90%A8";
        assert_eq!(query_param(query, "paymentKey").as_deref(), Some("tk_1"));
        assert_eq!(query_param(query, "amount").as_deref(), Some("5000"));
        assert_eq!(query_param(query, "message").as_deref(), Some("취소 됨"));
        assert_eq!(query_param(query, "code"), None);
    }
}
