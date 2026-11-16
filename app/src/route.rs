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
        #[route("/donations")]
        Donations {},
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
