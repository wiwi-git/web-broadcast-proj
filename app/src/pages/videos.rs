use dioxus::prelude::*;

use super::Todo;
use crate::api;
use crate::auth::RequireLogin;
use crate::route::Route;

#[component]
pub fn VideoList() -> Element {
    let videos = use_resource(|| api::fetch_videos(0));

    match &*videos.read() {
        None => rsx! { p { class: "notice", "불러오는 중…" } },
        Some(Err(error)) => rsx! { p { class: "notice", "{error}" } },
        Some(Ok(page)) => rsx! {
            h1 { "동영상" }
            if page.items.is_empty() {
                p { class: "notice", "아직 올라온 동영상이 없습니다." }
            }
            ul {
                for video in page.items.iter() {
                    li { key: "{video.id}",
                        Link { to: Route::VideoDetail { id: video.id.clone() },
                            "{video.title} · {video.owner.nickname}"
                        }
                    }
                }
            }
        },
    }
}

#[component]
pub fn VideoDetail(id: String) -> Element {
    rsx! {
        Todo { title: "동영상", note: "HLS 재생과 댓글 자리. 동영상 id: {id}" }
    }
}

#[component]
pub fn Upload() -> Element {
    rsx! {
        RequireLogin {
            Todo { title: "업로드", note: "파일 선택과 진행률 표시 자리." }
        }
    }
}
