use dioxus::prelude::*;

use crate::api::{self, LogInInput, NICKNAME_MAX_LENGTH, NICKNAME_MIN_LENGTH, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, SignUpInput};
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

/// web/src/pages/SignUpPage.tsx와 같은 화면. 입력하는 동안 서버와 같은 규칙으로 미리 검사한다.
#[component]
pub fn SignUp(next: String) -> Element {
    let mut auth = use_auth();
    let navigator = use_navigator();
    let mut username = use_signal(String::new);
    let mut password = use_signal(String::new);
    let mut password_confirm = use_signal(String::new);
    let mut nickname = use_signal(String::new);
    let mut submitting = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    // 이미 로그인했으면 돌아갈 곳으로 보낸다
    let already = auth.me().is_some() && !submitting();
    let redirect_next = next.clone();
    use_effect(move || {
        if auth.me().is_some() && !submitting() {
            navigator.replace(safe_next(&redirect_next));
        }
    });

    let nickname_length = nickname().trim().chars().count();
    let username_error = (!username().is_empty() && !api::is_valid_username(&username()))
        .then_some("영문 소문자와 숫자로 4~20자".to_owned());
    let password_length = password().chars().count();
    let password_error = (!password().is_empty()
        && !(PASSWORD_MIN_LENGTH..=PASSWORD_MAX_LENGTH).contains(&password_length))
    .then(|| format!("{PASSWORD_MIN_LENGTH}~{PASSWORD_MAX_LENGTH}자"));
    let confirm_error = (!password_confirm().is_empty() && password_confirm() != password())
        .then_some("비밀번호가 서로 다릅니다.".to_owned());
    let nickname_error = (!nickname().is_empty()
        && !(NICKNAME_MIN_LENGTH..=NICKNAME_MAX_LENGTH).contains(&nickname_length))
    .then(|| format!("{NICKNAME_MIN_LENGTH}~{NICKNAME_MAX_LENGTH}자"));
    let valid = !username().is_empty()
        && !password().is_empty()
        && !password_confirm().is_empty()
        && !nickname().is_empty()
        && username_error.is_none()
        && password_error.is_none()
        && confirm_error.is_none()
        && nickname_error.is_none();

    let login_next = next.clone();
    let submit = move |event: FormEvent| {
        let next = next.clone();
        async move {
            event.prevent_default();
            if !valid {
                return;
            }
            submitting.set(true);
            error.set(None);
            let input = SignUpInput { username: username(), password: password(), nickname: nickname().trim().to_owned() };
            match api::sign_up(&input).await {
                Ok(me) => {
                    auth.set(Some(me));
                    navigator.replace(safe_next(&next));
                }
                Err(e) => {
                    error.set(Some(e.to_string()));
                    submitting.set(false);
                }
            }
        }
    };

    if already {
        return rsx! {};
    }

    rsx! {
        form { class: "auth-form", onsubmit: submit,
            h1 { class: "page-title", "회원가입" }
            label { class: "field",
                span { class: "label", "아이디" }
                input {
                    r#type: "text",
                    class: "text-input",
                    autocomplete: "username",
                    autocapitalize: "none",
                    maxlength: 20,
                    value: "{username}",
                    oninput: move |e| username.set(e.value()),
                }
                span { class: if username_error.is_some() { "hint error" } else { "hint" },
                    {username_error.clone().unwrap_or_else(|| "로그인할 때 씁니다. 영문 소문자와 숫자로 4~20자, 나중에 바꿀 수 없습니다.".to_owned())}
                }
            }
            label { class: "field",
                span { class: "label", "비밀번호" }
                input {
                    r#type: "password",
                    class: "text-input",
                    autocomplete: "new-password",
                    value: "{password}",
                    oninput: move |e| password.set(e.value()),
                }
                span { class: if password_error.is_some() { "hint error" } else { "hint" },
                    {password_error.clone().unwrap_or_else(|| format!("{PASSWORD_MIN_LENGTH}자 이상"))}
                }
            }
            label { class: "field",
                span { class: "label", "비밀번호 확인" }
                input {
                    r#type: "password",
                    class: "text-input",
                    autocomplete: "new-password",
                    value: "{password_confirm}",
                    oninput: move |e| password_confirm.set(e.value()),
                }
                if let Some(message) = confirm_error {
                    span { class: "hint error", "{message}" }
                }
            }
            label { class: "field",
                span { class: "label", "닉네임" }
                input {
                    r#type: "text",
                    class: "text-input",
                    maxlength: NICKNAME_MAX_LENGTH as i64,
                    value: "{nickname}",
                    oninput: move |e| nickname.set(e.value()),
                }
                span { class: if nickname_error.is_some() { "hint error" } else { "hint" },
                    {nickname_error.clone().unwrap_or_else(|| "방송과 댓글에 보이는 이름입니다. 다른 회원과 겹칠 수 없습니다.".to_owned())}
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            button {
                r#type: "submit",
                class: "button primary",
                disabled: !valid || submitting(),
                if submitting() { "가입 중…" } else { "가입하기" }
            }
            p { class: "meta auth-switch",
                "이미 회원이신가요? "
                Link { to: Route::Login { next: login_next }, "로그인" }
            }
        }
    }
}
