use dioxus::prelude::*;

use super::Todo;
use crate::api;
use crate::auth::RequireLogin;
use crate::route::Route;

#[component]
pub fn LiveList() -> Element {
    let broadcasts = use_resource(api::fetch_broadcasts);

    match &*broadcasts.read() {
        None => rsx! { p { class: "notice", "불러오는 중…" } },
        Some(Err(error)) => rsx! { p { class: "notice", "{error}" } },
        Some(Ok(list)) => rsx! {
            h1 { "라이브" }
            if list.live.is_empty() {
                p { class: "notice", "지금 방송 중인 채널이 없습니다." }
            }
            ul {
                for broadcast in list.live.iter() {
                    li { key: "{broadcast.id}",
                        Link { to: Route::LiveWatch { id: broadcast.id.clone() },
                            "{broadcast.title} · {broadcast.owner.nickname}"
                        }
                    }
                }
            }
        },
    }
}

#[component]
pub fn LiveWatch(id: String) -> Element {
    rsx! {
        Todo {
            title: "라이브 시청",
            note: "HLS 재생과 실시간 댓글·후원 알림 자리. 방송 id: {id}",
        }
    }
}

#[component]
pub fn Broadcast() -> Element {
    rsx! {
        RequireLogin {
            Todo {
                title: "방송하기",
                note: "카메라·마이크를 열어 WHIP으로 송출하는 자리. 플랫폼마다 권한 설정이 필요하다.",
            }
        }
    }
}
