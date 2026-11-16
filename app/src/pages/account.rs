use dioxus::prelude::*;

use super::Todo;
use crate::api::{self, LogInInput};
use crate::auth::use_auth;
use crate::route::{Route, safe_next};

#[component]
pub fn Login(next: String) -> Element {
    let mut auth = use_auth();
    let navigator = use_navigator();
    let mut username = use_signal(String::new);
    let mut password = use_signal(String::new);
    let mut error = use_signal(|| None::<String>);
    let mut busy = use_signal(|| false);

    let signup_next = next.clone();
    let submit = move |event: FormEvent| {
        let next = next.clone();
        async move {
            event.prevent_default();
            busy.set(true);
            let input = LogInInput { username: username(), password: password() };
            match api::log_in(&input).await {
                Ok(me) => {
                    auth.set(Some(me));
                    navigator.replace(safe_next(&next));
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            busy.set(false);
        }
    };

    // 마크업과 클래스는 web/src/pages/LoginPage.tsx와 같다
    rsx! {
        form { class: "auth-form", onsubmit: submit,
            h1 { class: "page-title", "로그인" }
            label { class: "field",
                span { class: "label", "아이디" }
                input {
                    r#type: "text",
                    class: "text-input",
                    autocomplete: "username",
                    // 아이디는 소문자만 받으므로 모바일 키보드가 첫 글자를 대문자로 바꾸지 않게 한다
                    autocapitalize: "none",
                    value: "{username}",
                    oninput: move |e| username.set(e.value()),
                }
            }
            label { class: "field",
                span { class: "label", "비밀번호" }
                input {
                    r#type: "password",
                    class: "text-input",
                    autocomplete: "current-password",
                    value: "{password}",
                    oninput: move |e| password.set(e.value()),
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            button {
                r#type: "submit",
                class: "button primary",
                disabled: username().trim().is_empty() || password().is_empty() || busy(),
                if busy() { "로그인 중…" } else { "로그인" }
            }
            p { class: "meta auth-switch",
                "아직 회원이 아니신가요? "
                Link { to: Route::SignUp { next: signup_next }, "회원가입" }
            }
        }
    }
}

#[component]
pub fn SignUp(next: String) -> Element {
    rsx! {
        Todo { title: "회원가입", note: "아이디·비밀번호·닉네임 입력과 검증 자리." }
    }
}
