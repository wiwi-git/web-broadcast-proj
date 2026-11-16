//! 되돌릴 수 없는 동작(종료·삭제)을 한 번 더 확인받는 버튼. web/src/components/ConfirmButton.tsx와 같다.
//! 앱 WebView에서는 window.confirm 창이 뜨지 않을 수 있으므로 화면 안에서 묻는다.

use dioxus::prelude::*;

#[component]
pub fn ConfirmButton(
    children: Element,
    /// 두 번째 단계에서 보여 줄 질문
    message: String,
    /// 실행 버튼 문구
    confirm_label: String,
    on_confirm: EventHandler<()>,
    #[props(default = "button".to_owned())] class: String,
    #[props(default)] disabled: bool,
) -> Element {
    let mut asking = use_signal(|| false);

    if !asking() {
        return rsx! {
            button { r#type: "button", class, disabled, onclick: move |_| asking.set(true), {children} }
        };
    }
    rsx! {
        span { class: "confirm-inline", role: "group", aria_label: "{message}",
            span { class: "confirm-message", "{message}" }
            button {
                r#type: "button",
                class: "button small danger",
                disabled,
                onclick: move |_| {
                    asking.set(false);
                    on_confirm.call(());
                },
                "{confirm_label}"
            }
            button {
                r#type: "button",
                class: "button small",
                // 실수로 실행하지 않도록 취소에 포커스를 둔다 (autofocus는 나중에 그려진 요소에 적용되지 않는다)
                onmounted: move |e| async move {
                    let _ = e.set_focus(true).await;
                },
                onclick: move |_| asking.set(false),
                "취소"
            }
        }
    }
}
