//! 모든 화면을 감싸는 머리글과 본문 영역. web/src/App.tsx의 header와 같은 구성이다.

use dioxus::prelude::*;

use super::icons::{CameraIcon, HeartIcon, LiveIcon, LoginIcon, LogoutIcon, UploadIcon, VideoIcon};
use crate::auth::use_auth;
use crate::route::Route;

#[component]
pub fn Shell() -> Element {
    #[cfg(not(target_arch = "wasm32"))]
    crate::app_link::use_app_links();

    rsx! {
        header { class: "app-header",
            nav { class: "nav",
                Link { to: Route::LiveList {}, class: "logo", "Web Broadcast" }
                Link { to: Route::LiveList {}, class: "nav-link", active_class: "active", title: "라이브",
                    LiveIcon {}
                    span { class: "nav-label", "라이브" }
                }
                Link { to: Route::VideoList { page: 0 }, class: "nav-link", active_class: "active", title: "동영상",
                    VideoIcon {}
                    span { class: "nav-label", "동영상" }
                }
            }
            div { class: "header-actions",
                Link { to: Route::Broadcast {}, class: "button primary", title: "방송하기",
                    CameraIcon {}
                    span { class: "nav-label", "방송하기" }
                }
                Link { to: Route::Upload {}, class: "button", title: "업로드",
                    UploadIcon {}
                    span { class: "nav-label", "업로드" }
                }
                AccountMenu {}
            }
        }
        main { class: "app-main", Outlet::<Route> {} }
    }
}

#[component]
fn AccountMenu() -> Element {
    let mut auth = use_auth();
    let route = use_route::<Route>();

    if !auth.ready() {
        return rsx! {};
    }
    let Some(me) = auth.me() else {
        // 로그인·회원가입 화면에서는 돌아갈 곳을 그대로 둔다
        let to = match route {
            Route::Login { next } | Route::SignUp { next } => Route::Login { next },
            other => other.login_then(),
        };
        return rsx! {
            Link { to, class: "button", title: "로그인",
                LoginIcon {}
                span { class: "nav-label", "로그인" }
            }
        };
    };
    rsx! {
        div { class: "account",
            span { class: "account-name", "{me.nickname}" }
            Link { to: Route::Donations { tab: String::new() }, class: "button", title: "후원 내역",
                HeartIcon {}
                span { class: "nav-label", "후원 내역" }
            }
            button {
                r#type: "button",
                class: "button",
                title: "로그아웃",
                onclick: move |_| async move {
                    let _ = auth.log_out().await;
                },
                LogoutIcon {}
                span { class: "nav-label", "로그아웃" }
            }
        }
    }
}
