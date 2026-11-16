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

    rsx! {
        h1 { "로그인" }
        form { onsubmit: submit,
            label { "아이디"
                input { value: "{username}", autocomplete: "username",
                    oninput: move |e| username.set(e.value()) }
            }
            label { "비밀번호"
                input { r#type: "password", value: "{password}", autocomplete: "current-password",
                    oninput: move |e| password.set(e.value()) }
            }
            if let Some(message) = error() {
                p { class: "error", "{message}" }
            }
            button { r#type: "submit", class: "button primary", disabled: busy(), "로그인" }
        }
        Link { to: Route::SignUp { next: String::new() }, "회원가입" }
    }
}

#[component]
pub fn SignUp(next: String) -> Element {
    rsx! {
        Todo { title: "회원가입", note: "아이디·비밀번호·닉네임 입력과 검증 자리." }
    }
}
