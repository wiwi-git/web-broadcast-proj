//! 유튜브 동시 송출 (ADR-0007). web/src/components/SimulcastPicker.tsx와 같다.

use dioxus::prelude::*;

use crate::api::{self, SimulcastSetting, SimulcastStatus};
use crate::components::ConfirmButton;

/// 방송을 시작하기 전에 고르는 유튜브 동시 송출.
/// YouTube Key가 없으면 등록 칸을, 있으면 끝 네 자리와 "유튜브에도 송출" 체크를 보여 준다.
/// 체크의 처음 값은 지난번 방송에서 고른 값이다. 서버에 동시 송출 설정이 없으면 아무것도 보이지 않는다.
#[component]
pub fn SimulcastPicker(on: Signal<bool>, disabled: bool) -> Element {
    let mut on = on;
    let mut setting = use_signal(|| None::<SimulcastSetting>);
    let mut editing = use_signal(|| false);
    let mut key = use_signal(String::new);
    let mut saving = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    use_hook(move || {
        spawn(async move {
            // 불러오지 못하면 동시 송출 없이 방송한다
            if let Ok(loaded) = api::fetch_simulcast().await {
                on.set(loaded.youtube_key_last4.is_some() && loaded.on_by_default);
                setting.set(Some(loaded));
            }
        });
    });

    let Some(current) = setting() else { return rsx! {} };
    if !current.available {
        return rsx! {};
    }

    // 바깥의 방송 시작 폼이 Enter로 제출되지 않도록 폼 없이 버튼으로 저장한다
    let save = move || {
        spawn(async move {
            saving.set(true);
            error.set(None);
            match api::save_youtube_key(key().trim()).await {
                Ok(saved) => {
                    setting.set(Some(saved));
                    key.set(String::new());
                    editing.set(false);
                    on.set(true);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            saving.set(false);
        });
    };

    let remove = move |_| {
        spawn(async move {
            match api::delete_youtube_key().await {
                Ok(()) => {
                    setting.with_mut(|s| {
                        if let Some(s) = s {
                            s.youtube_key_last4 = None;
                            s.on_by_default = false;
                        }
                    });
                    on.set(false);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
        });
    };

    let Some(last4) = current.youtube_key_last4.clone().filter(|_| !editing()) else {
        return rsx! {
            div { class: "simulcast",
                p { class: "simulcast-title", "유튜브에도 송출" }
                input {
                    r#type: "password",
                    class: "text-input",
                    autocomplete: "off",
                    value: "{key}",
                    disabled: disabled || saving(),
                    placeholder: "유튜브 스트림 키",
                    oninput: move |e| key.set(e.value()),
                    onkeydown: move |e| {
                        if e.key() == Key::Enter {
                            e.prevent_default();
                            if !key().trim().is_empty() {
                                save();
                            }
                        }
                    },
                }
                p { class: "hint",
                    "유튜브 스튜디오 → 만들기 → 라이브 스트리밍 시작 → 스트림 설정의 \"스트림 키\"를 복사해 붙여넣으세요."
                }
                if let Some(message) = error() {
                    p { class: "hint error", "{message}" }
                }
                div { class: "simulcast-actions",
                    button {
                        r#type: "button",
                        class: "button",
                        disabled: disabled || saving() || key().trim().is_empty(),
                        onclick: move |_| save(),
                        if saving() { "등록 중…" } else { "키 등록" }
                    }
                    if editing() {
                        button {
                            r#type: "button",
                            class: "button",
                            disabled: saving(),
                            onclick: move |_| editing.set(false),
                            "취소"
                        }
                    }
                }
            }
        };
    };

    rsx! {
        div { class: "simulcast",
            label { class: "simulcast-check",
                input {
                    r#type: "checkbox",
                    checked: on(),
                    disabled,
                    onchange: move |e| on.set(e.checked()),
                }
                span { "유튜브에도 송출" }
                span { class: "hint", "키 ····{last4}" }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            div { class: "simulcast-actions",
                button { r#type: "button", class: "button", disabled, onclick: move |_| editing.set(true), "키 바꾸기" }
                ConfirmButton {
                    disabled,
                    message: "유튜브 키를 지울까요?",
                    confirm_label: "지우기",
                    on_confirm: remove,
                    "키 지우기"
                }
            }
        }
    }
}

/// 방송 중 Broadcaster에게만 보이는 유튜브 상태
#[component]
pub fn SimulcastStatusLine(status: SimulcastStatus) -> Element {
    let (text, class) = match status {
        SimulcastStatus::Connecting => ("연결 중…", "hint"),
        SimulcastStatus::Live => ("송출 중", "hint"),
        SimulcastStatus::Failed => ("연결 실패 (방송은 계속됩니다)", "hint error"),
    };
    rsx! {
        p { class, "유튜브: {text}" }
    }
}
