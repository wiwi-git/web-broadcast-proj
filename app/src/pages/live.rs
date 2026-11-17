use std::time::Duration;

use dioxus::prelude::*;

use super::Todo;
use crate::api::{self, Broadcast as BroadcastInfo, BroadcastList, BroadcastStatus};
use crate::auth::RequireLogin;
use crate::route::Route;
use crate::time::{format_date, format_relative, sleep};

const REFRESH_INTERVAL: Duration = Duration::from_secs(10);

/// web/src/pages/LiveListPage.tsx와 같은 화면
#[component]
pub fn LiveList() -> Element {
    let mut list = use_signal(|| None::<BroadcastList>);
    let mut error = use_signal(|| None::<String>);

    // 새로 시작하거나 끝난 방송이 반영되도록 주기적으로 다시 읽는다.
    // 화면을 벗어나면 이 future도 함께 멈춘다.
    use_future(move || async move {
        loop {
            match api::fetch_broadcasts().await {
                Ok(data) => {
                    list.set(Some(data));
                    error.set(None);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            sleep(REFRESH_INTERVAL).await;
        }
    });

    let Some(list) = list() else {
        return match error() {
            Some(message) => rsx! { p { class: "notice error", "{message}" } },
            None => rsx! { p { class: "notice", "불러오는 중…" } },
        };
    };

    rsx! {
        h1 { class: "page-title", "라이브" }
        if list.live.is_empty() {
            div { class: "notice compact",
                p { "지금 방송 중인 방송이 없습니다." }
                Link { to: Route::Broadcast {}, class: "button primary", "방송하기" }
            }
        } else {
            ul { class: "video-grid",
                for broadcast in list.live {
                    li { key: "{broadcast.id}",
                        BroadcastCard { broadcast }
                    }
                }
            }
        }
        if !list.recently_ended.is_empty() {
            h2 { class: "section-title", "종료된 방송" }
            ul { class: "video-grid",
                for broadcast in list.recently_ended {
                    li { key: "{broadcast.id}",
                        BroadcastCard { broadcast }
                    }
                }
            }
        }
    }
}

#[component]
fn BroadcastCard(broadcast: BroadcastInfo) -> Element {
    let live = broadcast.status == BroadcastStatus::Live;
    let when = match (live, &broadcast.started_at, &broadcast.ended_at) {
        (true, Some(started), _) => format!("{} 시작", format_relative(started)),
        (_, _, Some(ended)) => format!("{} 종료", format_date(ended)),
        _ => String::new(),
    };

    rsx! {
        Link {
            to: Route::LiveWatch { id: broadcast.id.clone() },
            class: if live { "video-card" } else { "video-card ended" },
            div { class: "thumbnail live-thumbnail",
                span { class: if live { "badge live" } else { "badge" },
                    if live { "LIVE" } else { "종료" }
                }
            }
            h3 { class: "video-card-title", "{broadcast.title}" }
            p { class: "meta", "{broadcast.owner.nickname}" }
            p { class: "meta", "{when}" }
        }
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
