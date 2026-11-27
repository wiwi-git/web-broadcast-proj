//! 로그인 상태. 앱 전체가 하나의 [`Auth`]를 context로 공유한다.

use dioxus::prelude::*;

use crate::api::{self, Me};
use crate::route::Route;

#[derive(Clone, Copy)]
pub struct Auth {
    /// 첫 확인이 끝나기 전에는 None, 끝나면 Some(로그인한 회원 또는 None)
    state: Signal<Option<Option<Me>>>,
}

impl Auth {
    pub fn ready(&self) -> bool {
        self.state.read().is_some()
    }

    pub fn me(&self) -> Option<Me> {
        self.state.read().clone().flatten()
    }

    pub fn set(&mut self, me: Option<Me>) {
        self.state.set(Some(me));
    }

    /// 로그인 상태를 서버에서 다시 읽는다 (브라우저에서 이메일을 인증한 뒤 등)
    pub async fn refresh(&mut self) {
        if let Ok(me) = api::fetch_me().await {
            self.set(me);
        }
    }

    pub async fn log_out(&mut self) -> api::ApiResult<()> {
        api::log_out().await?;
        self.set(None);
        Ok(())
    }
}

/// 앱 시작 시 한 번 호출해 로그인 상태를 확인하고 context에 넣는다.
pub fn use_auth_provider() {
    let mut state = use_signal(|| None);
    use_context_provider(|| Auth { state });
    use_future(move || async move {
        // 확인에 실패해도 화면은 로그아웃 상태로 띄운다
        state.set(Some(api::fetch_me().await.ok().flatten()));
    });
}

pub fn use_auth() -> Auth {
    use_context()
}

/// 로그인하지 않았으면 로그인 화면으로 보내고, 로그인하면 이 화면으로 돌아온다.
#[component]
pub fn RequireLogin(children: Element) -> Element {
    let auth = use_auth();
    let route = use_route::<Route>();
    let navigator = use_navigator();
    let logged_out = auth.ready() && auth.me().is_none();

    // 렌더링 중에는 이동하지 않고, 화면을 그린 뒤 로그인 화면으로 보낸다
    use_effect(use_reactive!(|logged_out| {
        if logged_out {
            navigator.replace(route.clone().login_then());
        }
    }));

    if !auth.ready() || logged_out {
        return rsx! { p { class: "notice", "불러오는 중…" } };
    }
    children
}
