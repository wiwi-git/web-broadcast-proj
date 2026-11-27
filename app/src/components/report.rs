//! 남의 Broadcast·Video·Comment를 신고하는 버튼. web/src/components/ReportButton.tsx와 같다.

use dioxus::prelude::*;

use crate::api::{self, ApiError, REPORT_DETAIL_MAX_LENGTH, REPORT_REASONS, ReportReason, ReportTargetType};
use crate::auth::use_auth;
use crate::route::Route;

#[derive(Debug, Clone, Copy, PartialEq)]
enum State {
    Closed,
    Open,
    Done,
}

/// 사유를 고르고 내용을 덧붙일 수 있다. 내 것에는 보이지 않고(서버도 거절한다),
/// 로그인하지 않았으면 로그인 화면으로 보낸다.
#[component]
pub fn ReportButton(
    target_type: ReportTargetType,
    target_id: String,
    /// 대상을 만든 User의 ID
    owner_id: String,
) -> Element {
    let auth = use_auth();
    let route = use_route::<Route>();
    let mut state = use_signal(|| State::Closed);
    let mut reason = use_signal(|| None::<ReportReason>);
    let mut detail = use_signal(String::new);
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);
    let mut done_message = use_signal(String::new);

    let me = auth.me();
    if me.as_ref().is_some_and(|me| me.id == owner_id) {
        return rsx! {};
    }
    if me.is_none() {
        return rsx! {
            Link { class: "link-button report-button", to: route.login_then(), "신고" }
        };
    }

    let submit_id = target_id.clone();
    let submit = move |event: FormEvent| {
        let id = submit_id.clone();
        async move {
            event.prevent_default();
            let Some(chosen) = reason() else { return };
            busy.set(true);
            error.set(None);
            match api::file_report(target_type, &id, chosen, detail().trim()).await {
                Ok(()) => {
                    done_message.set("신고했습니다. 운영자가 확인할게요.".to_owned());
                    state.set(State::Done);
                }
                Err(ApiError::Server { status: 409, .. }) => {
                    done_message.set("이미 신고했습니다.".to_owned());
                    state.set(State::Done);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            busy.set(false);
        }
    };

    match state() {
        State::Done => rsx! { span { class: "report-done", "{done_message}" } },
        State::Closed => rsx! {
            button {
                r#type: "button",
                class: "link-button report-button",
                onclick: move |_| state.set(State::Open),
                "신고"
            }
        },
        State::Open => rsx! {
            form { class: "report-form", aria_label: "신고하기", onsubmit: submit,
                p { class: "report-title", "신고 사유를 골라 주세요" }
                div { class: "report-reasons", role: "radiogroup",
                    for (value, label) in REPORT_REASONS {
                        label { key: "{label}", class: "report-reason",
                            input {
                                r#type: "radio",
                                name: "report-reason-{target_id}",
                                checked: reason() == Some(value),
                                onchange: move |_| reason.set(Some(value)),
                            }
                            "{label}"
                        }
                    }
                }
                textarea {
                    class: "text-input",
                    rows: 2,
                    maxlength: REPORT_DETAIL_MAX_LENGTH as i64,
                    placeholder: "자세한 내용 (선택)",
                    value: "{detail}",
                    oninput: move |e| detail.set(e.value()),
                }
                if let Some(message) = error() {
                    p { class: "hint error", "{message}" }
                }
                div { class: "report-actions",
                    button { r#type: "button", class: "button small", onclick: move |_| state.set(State::Closed), "취소" }
                    button {
                        r#type: "submit",
                        class: "button small danger",
                        disabled: busy() || reason().is_none(),
                        "신고하기"
                    }
                }
            }
        },
    }
}
