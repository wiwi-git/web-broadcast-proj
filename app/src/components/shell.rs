//! 모든 화면을 감싸는 머리글과 본문 영역. web/src/App.tsx의 header와 같은 구성이다.

use dioxus::prelude::*;

use super::icons::{
    CameraIcon, CloseIcon, DownloadIcon, HeartIcon, LiveIcon, LoginIcon, LogoutIcon, UploadIcon, UserIcon, VideoIcon,
};
use crate::auth::use_auth;
use crate::route::Route;

/// 데스크톱 앱 다운로드 페이지. 운영 서버의 Caddy가 내준다 (deploy/download/)
const APP_DOWNLOAD_URL: &str = "https://live.duse.dev/download/";

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
            HeaderActions {}
        }
        main { class: "app-main", Outlet::<Route> {} }
    }
}

/// 앱 다운로드 버튼. 이미 앱 안이면 필요 없으므로 웹 빌드에만 둔다
#[component]
fn DownloadButton() -> Element {
    if !cfg!(target_arch = "wasm32") {
        return rsx! {};
    }
    rsx! {
        a { href: APP_DOWNLOAD_URL, class: "button download-button", title: "앱 다운로드",
            DownloadIcon {}
            span { class: "nav-label", "앱 다운로드" }
        }
    }
}

/// 머리글 오른쪽. 로그인하지 않았으면 로그인만, 로그인했으면 방송하기·업로드·닉네임을 보인다
#[component]
fn HeaderActions() -> Element {
    let auth = use_auth();
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
            div { class: "header-actions",
                DownloadButton {}
                Link { to, class: "button", title: "로그인",
                    LoginIcon {}
                    span { class: "nav-label", "로그인" }
                }
            }
        };
    };
    rsx! {
        div { class: "header-actions",
            Link { to: Route::Broadcast {}, class: "button primary", title: "방송하기",
                CameraIcon {}
                span { class: "nav-label", "방송하기" }
            }
            Link { to: Route::Upload {}, class: "button", title: "업로드",
                UploadIcon {}
                span { class: "nav-label", "업로드" }
            }
            DownloadButton {}
            AccountMenu { nickname: me.nickname }
        }
    }
}

/// 닉네임 버튼. 누르면 화면 오른쪽에서 후원 내역·로그아웃이 있는 패널이 열린다
#[component]
fn AccountMenu(nickname: String) -> Element {
    let mut auth = use_auth();
    let route = use_route::<Route>();
    // 연 화면을 기억한다. 다른 화면으로 가면(후원 내역, 뒤로 가기) 저절로 닫힌다
    let mut opened_at = use_signal(|| None::<Route>);
    let open = opened_at.read().as_ref() == Some(&route);

    rsx! {
        button {
            r#type: "button",
            class: "button account-button",
            title: "{nickname}",
            "aria-haspopup": "dialog",
            "aria-expanded": "{open}",
            onclick: {
                let route = route.clone();
                move |_| opened_at.set(Some(route.clone()))
            },
            UserIcon {}
            span { class: "nav-label", "{nickname}" }
        }
        if open {
            div { class: "drawer-backdrop", onclick: move |_| opened_at.set(None),
                aside {
                    class: "drawer",
                    role: "dialog",
                    "aria-label": "내 계정",
                    onclick: move |event| event.stop_propagation(),
                    div { class: "drawer-head",
                        span { class: "drawer-name",
                            UserIcon {}
                            span { "{nickname}" }
                        }
                        button {
                            r#type: "button",
                            class: "drawer-close",
                            title: "닫기",
                            onclick: move |_| opened_at.set(None),
                            CloseIcon {}
                        }
                    }
                    nav { class: "drawer-menu",
                        Link { to: Route::Donations { tab: String::new() }, class: "drawer-item",
                            HeartIcon {}
                            "후원 내역"
                        }
                        button {
                            r#type: "button",
                            class: "drawer-item",
                            onclick: move |_| async move {
                                opened_at.set(None);
                                let _ = auth.log_out().await;
                            },
                            LogoutIcon {}
                            "로그아웃"
                        }
                    }
                }
            }
        }
    }
}
