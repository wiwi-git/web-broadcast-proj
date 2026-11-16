use dioxus::prelude::*;

#[component]
pub fn NotFound(segments: Vec<String>) -> Element {
    rsx! { p { class: "notice", "페이지를 찾을 수 없습니다." } }
}
