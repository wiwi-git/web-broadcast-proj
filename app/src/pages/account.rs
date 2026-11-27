use dioxus::prelude::*;

use crate::api::{self, LogInInput, NICKNAME_MAX_LENGTH, NICKNAME_MIN_LENGTH, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, SignUpInput};
use crate::auth::use_auth;
use crate::config;
use crate::route::{Route, safe_next};

#[component]
pub fn Login(next: String) -> Element {
    let mut auth = use_auth();
    let navigator = use_navigator();
    let mut email = use_signal(String::new);
    let mut password = use_signal(String::new);
    let mut error = use_signal(|| None::<String>);
    let mut busy = use_signal(|| false);

    let signup_next = next.clone();
    let submit = move |event: FormEvent| {
        let next = next.clone();
        async move {
            event.prevent_default();
            busy.set(true);
            let input = LogInInput { email: email().trim().to_owned(), password: password() };
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
                span { class: "label", "이메일" }
                input {
                    r#type: "email",
                    class: "text-input",
                    autocomplete: "email",
                    // 모바일 키보드가 첫 글자를 대문자로 바꾸지 않게 한다
                    autocapitalize: "none",
                    value: "{email}",
                    oninput: move |e| email.set(e.value()),
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
                disabled: email().trim().is_empty() || password().is_empty() || busy(),
                if busy() { "로그인 중…" } else { "로그인" }
            }
            p { class: "meta auth-switch",
                // 비밀번호 찾기와 재설정 링크는 웹 화면에서 한다 (기본 브라우저로 열린다)
                a { href: config::forgot_password_url(), target: "_blank", "비밀번호를 잊으셨나요?" }
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
    let mut email = use_signal(String::new);
    let mut password = use_signal(String::new);
    let mut password_confirm = use_signal(String::new);
    let mut nickname = use_signal(String::new);
    let mut agreed = use_signal(|| false);
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
    let email_error = (!email().is_empty() && !api::is_valid_email(email().trim()))
        .then_some("이메일 주소 형식이 아닙니다.".to_owned());
    let password_length = password().chars().count();
    let password_error = (!password().is_empty()
        && !(PASSWORD_MIN_LENGTH..=PASSWORD_MAX_LENGTH).contains(&password_length))
    .then(|| format!("{PASSWORD_MIN_LENGTH}~{PASSWORD_MAX_LENGTH}자"));
    let confirm_error = (!password_confirm().is_empty() && password_confirm() != password())
        .then_some("비밀번호가 서로 다릅니다.".to_owned());
    let nickname_error = (!nickname().is_empty()
        && !(NICKNAME_MIN_LENGTH..=NICKNAME_MAX_LENGTH).contains(&nickname_length))
    .then(|| format!("{NICKNAME_MIN_LENGTH}~{NICKNAME_MAX_LENGTH}자"));
    let valid = !email().is_empty()
        && !password().is_empty()
        && !password_confirm().is_empty()
        && !nickname().is_empty()
        && agreed()
        && email_error.is_none()
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
            let input = SignUpInput {
                email: email().trim().to_owned(),
                password: password(),
                nickname: nickname().trim().to_owned(),
                agree_terms: agreed(),
            };
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
                span { class: "label", "이메일" }
                input {
                    r#type: "email",
                    class: "text-input",
                    autocomplete: "email",
                    autocapitalize: "none",
                    maxlength: api::EMAIL_MAX_LENGTH as i64,
                    value: "{email}",
                    oninput: move |e| email.set(e.value()),
                }
                span { class: if email_error.is_some() { "hint error" } else { "hint" },
                    {email_error.clone().unwrap_or_else(|| "로그인할 때 씁니다. 가입하면 인증 메일을 보냅니다. 다른 회원에게는 보이지 않습니다.".to_owned())}
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
            label { class: "agree-check",
                input {
                    r#type: "checkbox",
                    checked: agreed(),
                    onchange: move |e| agreed.set(e.checked()),
                }
                span {
                    "만 14세 이상이며 "
                    // 웹 문서를 기본 브라우저로 연다
                    a { href: config::terms_url(), target: "_blank", "이용약관" }
                    "과 "
                    a { href: config::privacy_url(), target: "_blank", "개인정보처리방침" }
                    "에 동의합니다. (필수)"
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

/// 내 계정. 이메일·닉네임을 보여 주고 회원 탈퇴를 받는다. web/src/pages/AccountPage.tsx와 같다.
/// 탈퇴하면 로그아웃되므로 RequireLogin으로 감싸지 않고, 탈퇴 완료 안내까지 이 화면이 직접 보여 준다.
#[component]
pub fn Account() -> Element {
    let mut auth = use_auth();
    let navigator = use_navigator();
    let mut password = use_signal(String::new);
    let mut understood = use_signal(|| false);
    let mut busy = use_signal(|| false);
    let mut done = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    // 로그인하지 않았으면 로그인 화면으로 보낸다 (탈퇴를 마친 뒤는 제외)
    let logged_out = auth.ready() && auth.me().is_none() && !done();
    use_effect(use_reactive!(|logged_out| {
        if logged_out {
            navigator.replace(Route::Account {}.login_then());
        }
    }));

    if done() {
        return rsx! {
            div { class: "notice",
                p { "탈퇴했습니다. 계정과 기록을 모두 지웠습니다. 그동안 이용해 주셔서 감사합니다." }
                Link { to: Route::LiveList {}, class: "button", "처음으로" }
            }
        };
    }
    let Some(me) = auth.me() else {
        return rsx! { p { class: "notice", "불러오는 중…" } };
    };

    let submit = move |event: FormEvent| async move {
        event.prevent_default();
        busy.set(true);
        error.set(None);
        match api::withdraw(&password()).await {
            Ok(()) => {
                // 먼저 완료 화면으로 바꿔 두어야 로그아웃된 순간 로그인 화면으로 넘어가지 않는다
                done.set(true);
                auth.set(None);
            }
            Err(e) => {
                error.set(Some(e.to_string()));
                busy.set(false);
            }
        }
    };

    rsx! {
        div { class: "account-page",
            h1 { class: "page-title", "내 계정" }
            dl { class: "account-info",
                dt { "이메일" }
                dd {
                    "{me.email} "
                    if me.email_verified {
                        span { class: "badge verified inline", "인증됨" }
                    } else {
                        span { class: "badge danger inline", "인증 전" }
                    }
                }
                dt { "닉네임" }
                dd { "{me.nickname}" }
            }
            if !me.email_verified {
                ResendVerification {}
            }
            ChangePasswordForm {}
            section { class: "withdraw-box",
                h2 { "회원 탈퇴" }
                if me.admin {
                    p { class: "meta", "운영자 계정은 탈퇴할 수 없습니다. 먼저 운영자 역할을 해제해 주세요." }
                } else {
                    form { onsubmit: submit,
                        p { "탈퇴하면 아래 것들이 바로 지워지고 되돌릴 수 없습니다." }
                        ul {
                            li { "계정(이메일, 닉네임). 같은 이메일로 다시 가입할 수는 있습니다" }
                            li { "올린 동영상과 영상 파일, 내 방송 기록" }
                            li { "내가 쓴 댓글, 그리고 내 동영상·방송에 다른 사람이 단 댓글" }
                            li { "보내거나 받은 후원 기록 (상대방의 후원 내역에서도 사라집니다)" }
                            li { "내가 한 신고, 등록한 유튜브 키" }
                            li { "모든 기기의 로그인" }
                        }
                        p { class: "meta",
                            "자세한 내용은 "
                            a { href: config::privacy_url(), target: "_blank", "개인정보처리방침" }
                            "에 있습니다. 로그인할 수 없다면 {config::CONTACT_EMAIL}로 요청해 주세요."
                        }
                        label { class: "field",
                            span { class: "label", "비밀번호 확인" }
                            input {
                                r#type: "password",
                                class: "text-input",
                                autocomplete: "current-password",
                                value: "{password}",
                                oninput: move |e| password.set(e.value()),
                            }
                        }
                        label { class: "agree-check",
                            input {
                                r#type: "checkbox",
                                checked: understood(),
                                onchange: move |e| understood.set(e.checked()),
                            }
                            span { "위 내용이 모두 지워지고 되돌릴 수 없다는 것을 확인했습니다." }
                        }
                        if let Some(message) = error() {
                            p { class: "hint error", "{message}" }
                        }
                        button {
                            r#type: "submit",
                            class: "button danger",
                            disabled: password().is_empty() || !understood() || busy(),
                            if busy() { "탈퇴하는 중…" } else { "탈퇴하기" }
                        }
                    }
                }
            }
        }
    }
}

/// 인증 전인 회원이 인증 메일을 다시 받는다. 1분에 한 번 (web AccountPage의 ResendVerification과 같다)
#[component]
fn ResendVerification() -> Element {
    let mut auth = use_auth();
    let mut message = use_signal(|| None::<String>);
    let mut busy = use_signal(|| false);
    let resend = move |_| async move {
        busy.set(true);
        message.set(Some(match api::resend_verification().await {
            Ok(()) => "인증 메일을 다시 보냈습니다. 메일의 링크를 누른 뒤 \"인증했어요\"를 눌러 주세요.".to_owned(),
            Err(e) => e.to_string(),
        }));
        busy.set(false);
    };
    rsx! {
        div { class: "account-section",
            p { class: "meta", "인증하면 동영상을 올리고 댓글을 쓸 수 있습니다. 메일이 없으면 스팸함도 확인해 주세요." }
            div { class: "account-actions",
                button { r#type: "button", class: "button small", disabled: busy(), onclick: resend, "인증 메일 다시 받기" }
                button {
                    r#type: "button",
                    class: "button small",
                    onclick: move |_| async move { auth.refresh().await },
                    "인증했어요"
                }
            }
            if let Some(text) = message() {
                p { class: "hint", "{text}" }
            }
        }
    }
}

/// 지금 비밀번호를 알면 바꾼다. 이 기기의 로그인은 그대로, 다른 기기는 로그아웃된다
#[component]
fn ChangePasswordForm() -> Element {
    let mut current = use_signal(String::new);
    let mut next = use_signal(String::new);
    let mut confirm = use_signal(String::new);
    let mut busy = use_signal(|| false);
    let mut message = use_signal(|| None::<(String, bool)>);

    let next_length = next().chars().count();
    let length_error = (!next().is_empty() && !(PASSWORD_MIN_LENGTH..=PASSWORD_MAX_LENGTH).contains(&next_length))
        .then(|| format!("{PASSWORD_MIN_LENGTH}~{PASSWORD_MAX_LENGTH}자"));
    let confirm_error = (!confirm().is_empty() && confirm() != next()).then_some("새 비밀번호가 서로 다릅니다.");
    let ready = !current().is_empty()
        && !next().is_empty()
        && !confirm().is_empty()
        && length_error.is_none()
        && confirm_error.is_none();

    let submit = move |event: FormEvent| async move {
        event.prevent_default();
        busy.set(true);
        message.set(None);
        match api::change_password(&current(), &next()).await {
            Ok(()) => {
                current.set(String::new());
                next.set(String::new());
                confirm.set(String::new());
                message.set(Some(("비밀번호를 바꿨습니다. 다른 기기의 로그인은 끊겼습니다.".to_owned(), false)));
            }
            Err(e) => message.set(Some((e.to_string(), true))),
        }
        busy.set(false);
    };

    rsx! {
        section { class: "account-section",
            h2 { "비밀번호 변경" }
            form { class: "account-form", onsubmit: submit,
                label { class: "field",
                    span { class: "label", "지금 비밀번호" }
                    input {
                        r#type: "password",
                        class: "text-input",
                        autocomplete: "current-password",
                        value: "{current}",
                        oninput: move |e| current.set(e.value()),
                    }
                }
                label { class: "field",
                    span { class: "label", "새 비밀번호" }
                    input {
                        r#type: "password",
                        class: "text-input",
                        autocomplete: "new-password",
                        value: "{next}",
                        oninput: move |e| next.set(e.value()),
                    }
                    span { class: if length_error.is_some() { "hint error" } else { "hint" },
                        {length_error.clone().unwrap_or_else(|| format!("{PASSWORD_MIN_LENGTH}자 이상"))}
                    }
                }
                label { class: "field",
                    span { class: "label", "새 비밀번호 확인" }
                    input {
                        r#type: "password",
                        class: "text-input",
                        autocomplete: "new-password",
                        value: "{confirm}",
                        oninput: move |e| confirm.set(e.value()),
                    }
                    if let Some(text) = confirm_error {
                        span { class: "hint error", "{text}" }
                    }
                }
                if let Some((text, is_error)) = message() {
                    p { class: if is_error { "hint error" } else { "hint" }, "{text}" }
                }
                button {
                    r#type: "submit",
                    class: "button primary",
                    disabled: !ready || busy(),
                    if busy() { "바꾸는 중…" } else { "비밀번호 바꾸기" }
                }
            }
        }
    }
}
