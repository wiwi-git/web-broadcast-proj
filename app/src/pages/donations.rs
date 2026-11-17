use dioxus::prelude::*;

use crate::api::{
    self, Broadcast as BroadcastInfo, BroadcastRef, BroadcastStatus, DONATION_AMOUNT_UNIT, DONATION_MAX_AMOUNT,
    DONATION_MESSAGE_MAX_LENGTH, DONATION_MIN_AMOUNT, DONATION_PRESETS, DonationStatus, SentDonation,
};
use crate::auth::{RequireLogin, use_auth};
use crate::route::{Route, query_param};
use crate::time::{format_date, format_number, format_won};

/// 토스페이먼츠 결제위젯 SDK. 웹은 npm 패키지(@tosspayments/tosspayments-sdk)가 이 스크립트를 불러온다
// 웹 빌드에서만 쓴다 (앱은 브라우저로 결제한다)
#[cfg_attr(not(target_arch = "wasm32"), allow(dead_code))]
const TOSS_SDK: &str = "https://js.tosspayments.com/v2/standard";

/// 결제 방법 안내. 앱은 결제창을 앱 안에 띄울 수 없어서 브라우저에서 결제한다
#[cfg(target_arch = "wasm32")]
const PAY_HINT: &str = "결제수단은 다음에 열리는 토스페이먼츠 결제창에서 고릅니다. 테스트 결제라 실제로 돈이 빠져나가지 않습니다.";
#[cfg(not(target_arch = "wasm32"))]
const PAY_HINT: &str = "후원하기를 누르면 브라우저에서 결제 화면이 열립니다. 브라우저에서 로그인할 필요는 없습니다. 테스트 결제라 실제로 돈이 빠져나가지 않습니다.";

fn amount_error(amount: Option<u64>) -> Option<&'static str> {
    match amount {
        None => Some("금액을 입력해 주세요."),
        Some(amount)
            if !(DONATION_MIN_AMOUNT..=DONATION_MAX_AMOUNT).contains(&amount) || amount % DONATION_AMOUNT_UNIT != 0 =>
        {
            Some("1,000원부터 500,000원까지 1,000원 단위로 정할 수 있습니다.")
        }
        Some(_) => None,
    }
}

/// 방송 중인 Broadcast의 Broadcaster에게 후원한다. web/src/pages/DonatePage.tsx와 같다.
/// 1. 금액과 메시지를 고른다
/// 2. 후원하기를 누르면 서버에 후원을 결제 대기로 만든다
/// 3. 웹은 그 id를 orderId로 토스페이먼츠 통합결제창을 열고, 결제 서비스가 /donations/success·fail로 돌려보낸다.
///    앱은 결제 토큰을 받아 기본 브라우저로 웹의 결제 화면을 연다 (`pay_in_browser`, ADR-0005)
#[component]
pub fn Donate(id: String) -> Element {
    rsx! {
        RequireLogin { DonateLoader { id } }
    }
}

#[component]
fn DonateLoader(id: String) -> Element {
    let auth = use_auth();
    let broadcast = use_resource(use_reactive!(|id| async move { api::fetch_broadcast(&id).await }));
    let (broadcast, me) = match (&*broadcast.read(), auth.me()) {
        (Some(Err(error)), _) => return rsx! { p { class: "notice error", "{error}" } },
        (Some(Ok(broadcast)), Some(me)) => (broadcast.clone(), me),
        _ => return rsx! { p { class: "notice", "불러오는 중…" } },
    };
    let back = rsx! {
        Link { to: Route::LiveWatch { id: broadcast.id.clone() }, class: "button", "방송으로 돌아가기" }
    };
    if broadcast.owner.id == me.id {
        return rsx! {
            div { class: "notice",
                p { "자기 방송에는 후원할 수 없습니다." }
                div { class: "actions", {back} }
            }
        };
    }
    if broadcast.status != BroadcastStatus::Live {
        return rsx! {
            div { class: "notice",
                p { "방송 중일 때만 후원할 수 있습니다." }
                div { class: "actions", {back} }
            }
        };
    }
    rsx! {
        DonateForm { broadcast, customer_key: me.id, customer_name: me.nickname }
    }
}

/// "후원하기"를 누른 뒤 결제가 어떻게 됐는지
// 플랫폼마다 쓰는 경우가 다르다
#[allow(dead_code)]
enum PayOutcome {
    /// 웹: 화면 전체가 결제 서비스로 넘어갔다
    Redirected,
    /// 앱: 기본 브라우저로 결제 화면을 열었다
    OpenedBrowser,
    /// 결제창을 닫았다
    Canceled,
    Failed(String),
}

/// 앱(데스크톱·모바일)의 결제 (ADR-0005). 토스페이먼츠 결제창은 http(s) 화면이라 Dioxus 앱 안에 띄울 수 없다
/// (앱이 http(s) 이동을 모두 기본 브라우저로 넘긴다). 그래서 결제 토큰을 받아 기본 브라우저로 웹의 결제 화면을 연다.
/// 결제 화면은 로그인 없이 토큰으로 이 후원 하나만 결제하고, 끝나면 webbroadcast://donations 로 앱을 다시 연다.
#[cfg(not(target_arch = "wasm32"))]
async fn pay_in_browser(donation_id: &str) -> PayOutcome {
    let issued = match api::issue_checkout_token(donation_id).await {
        Ok(issued) => issued,
        Err(e) => return PayOutcome::Failed(e.to_string()),
    };
    let url = format!("{}/checkout?token={}", crate::config::web_origin(), api::encode(&issued.token));
    match webbrowser::open(&url) {
        Ok(()) => PayOutcome::OpenedBrowser,
        Err(e) => PayOutcome::Failed(format!("브라우저를 열지 못했습니다. {e}")),
    }
}

/// 웹: 토스페이먼츠 결제창을 열면 화면 전체가 결제 서비스로 넘어가고, 결과는 /donations/success·fail로 돌아온다.
/// 이 함수가 결과를 돌려주는 건 결제창으로 넘어가지 못했거나 결제창을 닫았을 때뿐이다.
/// 결제창을 닫으면 결제 서비스는 failUrl에 orderId를 붙이지 않으므로 직접 붙여 둔다.
#[cfg(target_arch = "wasm32")]
async fn pay_in_app(
    client_key: &str,
    customer_key: &str,
    customer_name: &str,
    amount: u64,
    order_id: &str,
    order_name: &str,
) -> PayOutcome {
    let args = serde_json::json!({
        "sdk": TOSS_SDK,
        "clientKey": client_key,
        "customerKey": customer_key,
        "amount": amount,
        "orderId": order_id,
        "orderName": order_name,
        "customerName": customer_name,
    });
    let script = format!(
        r#"
        const args = {args};
        try {{
          if (!window.TossPayments) {{
            await new Promise((resolve, reject) => {{
              const script = document.createElement('script');
              script.src = args.sdk;
              script.onload = resolve;
              script.onerror = () => reject(new Error('결제 모듈을 불러오지 못했습니다.'));
              document.head.appendChild(script);
            }});
          }}
          const payment = window.TossPayments(args.clientKey).payment({{ customerKey: args.customerKey }});
          const origin = window.location.origin;
          await payment.requestPayment({{
            method: 'CARD',
            amount: {{ currency: 'KRW', value: args.amount }},
            orderId: args.orderId,
            orderName: args.orderName,
            customerName: args.customerName,
            successUrl: origin + '/donations/success',
            failUrl: origin + '/donations/fail?donationId=' + encodeURIComponent(args.orderId),
          }});
          dioxus.send({{ ok: true }});
        }} catch (e) {{
          dioxus.send({{ code: e.code ?? null, message: e.message ?? null }});
        }}
        "#
    );
    let result = document::eval(&script).recv::<serde_json::Value>().await.unwrap_or_default();
    if result.get("ok").is_some() {
        return PayOutcome::Redirected;
    }
    // PC에서는 결제창을 닫아도 failUrl로 이동하지 않고 USER_CANCEL 오류가 온다
    if result.get("code").and_then(|v| v.as_str()) == Some("USER_CANCEL") {
        return PayOutcome::Canceled;
    }
    let message = result.get("message").and_then(|v| v.as_str());
    PayOutcome::Failed(message.unwrap_or("결제를 요청하지 못했습니다.").to_owned())
}

#[component]
fn DonateForm(broadcast: BroadcastInfo, customer_key: String, customer_name: String) -> Element {
    let mut amount_text = use_signal(|| DONATION_PRESETS[1].to_string());
    let mut message = use_signal(String::new);
    let mut client_key = use_signal(|| None::<String>);
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);
    let mut canceled = use_signal(|| false);
    // 앱: 기본 브라우저로 결제 화면을 열었다
    let mut opened = use_signal(|| false);

    let amount = amount_text().parse::<u64>().ok();
    let invalid_amount = amount_error(amount);

    // 결제창은 "후원하기"를 누를 때 열린다. 결제 키만 미리 받아 둔다
    use_future(move || async move {
        match api::fetch_payment_client_key().await {
            Ok(Some(key)) => client_key.set(Some(key)),
            Ok(None) => error.set(Some("후원 결제가 아직 설정되지 않았습니다. 서버의 토스페이먼츠 키를 확인해 주세요.".to_owned())),
            Err(e) => error.set(Some(format!("결제 모듈을 불러오지 못했습니다. {e}"))),
        }
    });

    let broadcast_id = broadcast.id.clone();
    let pay = move |event: FormEvent| {
        let broadcast_id = broadcast_id.clone();
        let customer_key = customer_key.clone();
        let customer_name = customer_name.clone();
        async move {
            event.prevent_default();
            let (Some(key), Some(amount)) = (client_key(), amount) else { return };
            if invalid_amount.is_some() {
                return;
            }
            busy.set(true);
            error.set(None);
            canceled.set(false);
            let checkout = match api::start_donation(&broadcast_id, amount, message().trim()).await {
                Ok(checkout) => checkout,
                Err(e) => {
                    error.set(Some(e.to_string()));
                    busy.set(false);
                    return;
                }
            };
            let order_id = checkout.order_id.clone();

            #[cfg(not(target_arch = "wasm32"))]
            let outcome = {
                let _ = (key, &customer_key, &customer_name);
                pay_in_browser(&order_id).await
            };
            #[cfg(target_arch = "wasm32")]
            let outcome =
                pay_in_app(&key, &customer_key, &customer_name, amount, &order_id, &checkout.order_name).await;

            match outcome {
                // 웹은 화면이 결제 서비스로 넘어간다
                PayOutcome::Redirected => return,
                PayOutcome::OpenedBrowser => opened.set(true),
                // 결제를 마치지 않았으므로 후원은 취소로 끝낸다
                PayOutcome::Canceled => {
                    let _ = api::abandon_donation(&order_id, Some("PAY_PROCESS_CANCELED"), None).await;
                    canceled.set(true);
                }
                PayOutcome::Failed(message) => {
                    let _ = api::abandon_donation(&order_id, Some("PAY_PROCESS_CANCELED"), Some(&message)).await;
                    error.set(Some(message));
                }
            }
            busy.set(false);
        }
    };

    let message_length = message.read().chars().count();
    let show_amount_error = invalid_amount.is_some() && !amount_text().is_empty();
    let submit_label = match (amount, invalid_amount) {
        _ if busy() => "결제 진행 중…".to_owned(),
        (Some(amount), None) => format!("{} 후원하기", format_won(amount)),
        _ => "후원하기".to_owned(),
    };

    rsx! {
        form { class: "donate-form", onsubmit: pay,
            h1 { "후원하기" }
            p { class: "meta",
                strong { class: "owner-name", "{broadcast.owner.nickname}" }
                "님의 방송 · {broadcast.title}"
            }

            div { class: "field",
                span { class: "label", "금액" }
                div { class: "amount-presets",
                    for preset in DONATION_PRESETS {
                        button {
                            key: "{preset}",
                            r#type: "button",
                            class: if amount == Some(preset) { "button small selected" } else { "button small" },
                            onclick: move |_| amount_text.set(preset.to_string()),
                            {format_won(preset)}
                        }
                    }
                }
                label { class: "amount-input",
                    input {
                        r#type: "text",
                        inputmode: "numeric",
                        class: "text-input",
                        aria_label: "직접 입력한 금액",
                        value: "{amount_text}",
                        oninput: move |e| amount_text.set(e.value().chars().filter(char::is_ascii_digit).collect()),
                    }
                    span { "원" }
                }
                span { class: if show_amount_error { "hint error" } else { "hint" },
                    if show_amount_error {
                        {invalid_amount.unwrap_or_default()}
                    } else {
                        "1,000원 단위, 최대 500,000원"
                    }
                }
            }

            label { class: "field",
                span { class: "label", "메시지 (선택)" }
                textarea {
                    rows: 2,
                    maxlength: DONATION_MESSAGE_MAX_LENGTH as i64,
                    value: "{message}",
                    placeholder: "방송 화면에 함께 표시됩니다",
                    oninput: move |e| message.set(e.value()),
                }
                span { class: "hint", "{message_length}/{DONATION_MESSAGE_MAX_LENGTH} · 후원자 닉네임은 항상 공개됩니다" }
            }

            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            if canceled() {
                p { class: "hint", "후원을 취소했습니다. 결제되지 않았습니다." }
            }
            if opened() {
                p { class: "hint", "브라우저에서 결제를 이어서 진행해 주세요. 결제를 마치면 앱으로 돌아와 후원 내역을 보여 줍니다." }
            }
            p { class: "hint", {PAY_HINT} }
            div { class: "actions",
                Link { to: Route::LiveWatch { id: broadcast.id.clone() }, class: "button", "취소" }
                button {
                    r#type: "submit",
                    class: "button primary",
                    disabled: client_key.read().is_none() || invalid_amount.is_some() || busy(),
                    "{submit_label}"
                }
            }
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
enum Outcome {
    Loading,
    Done(SentDonation),
    Error { message: String, retry: bool },
}

/// 결제 서비스가 결제 인증을 마치고 돌려보내는 곳(successUrl). 서버에 승인을 요청한다.
/// web/src/pages/DonationResultPage.tsx와 같다. 서버는 같은 승인을 다시 받으면 이미 정해진 결과를 돌려준다.
#[component]
pub fn DonationSuccess(query: String) -> Element {
    rsx! {
        RequireLogin { SuccessView { query } }
    }
}

#[component]
fn SuccessView(query: String) -> Element {
    let payment_key = query_param(&query, "paymentKey");
    let order_id = query_param(&query, "orderId");
    let amount = query_param(&query, "amount").and_then(|a| a.parse::<u64>().ok());
    let mut outcome = use_signal(|| Outcome::Loading);
    let mut attempt = use_signal(|| 0u32);

    let request = (payment_key.clone(), order_id.clone(), amount);
    use_resource(move || {
        let (payment_key, order_id, amount) = request.clone();
        let _ = attempt();
        async move {
            let (Some(payment_key), Some(order_id), Some(amount)) = (payment_key, order_id, amount) else { return };
            match api::approve_donation(&order_id, &payment_key, amount).await {
                Ok(donation) => outcome.set(Outcome::Done(donation)),
                // 503이면 결과를 아직 모르는 것이다. 다시 확인하면 같은 승인을 다시 요청한다
                Err(e) => outcome.set(Outcome::Error { retry: e.status() == Some(503), message: e.to_string() }),
            }
        }
    });

    if payment_key.is_none() || order_id.is_none() || amount.is_none() {
        return rsx! { p { class: "notice error", "결제 정보가 올바르지 않습니다." } };
    }
    match outcome() {
        Outcome::Loading => rsx! { p { class: "notice", "결제를 확인하는 중…" } },
        Outcome::Done(donation) => rsx! { DonationOutcome { donation } },
        Outcome::Error { message, retry } => rsx! {
            div { class: "notice",
                p { class: "error", "{message}" }
                div { class: "actions",
                    if retry {
                        button {
                            r#type: "button",
                            class: "button primary",
                            onclick: move |_| {
                                outcome.set(Outcome::Loading);
                                attempt += 1;
                            },
                            "다시 확인"
                        }
                    }
                    Link { to: Route::Donations { tab: String::new() }, class: "button", "후원 내역" }
                }
            }
        },
    }
}

/// 결제창을 닫았거나 결제 인증에 실패했을 때 결제 서비스가 돌려보내는 곳(failUrl)
#[component]
pub fn DonationFail(query: String) -> Element {
    rsx! {
        RequireLogin { FailView { query } }
    }
}

#[component]
fn FailView(query: String) -> Element {
    let donation_id = query_param(&query, "donationId");
    let code = query_param(&query, "code");
    let message = query_param(&query, "message");
    let mut outcome = use_signal(|| Outcome::Loading);

    let request = (donation_id.clone(), code, message.clone());
    use_future(move || {
        let (donation_id, code, message) = request.clone();
        async move {
            let Some(donation_id) = donation_id else { return };
            match api::abandon_donation(&donation_id, code.as_deref(), message.as_deref()).await {
                Ok(donation) => outcome.set(Outcome::Done(donation)),
                Err(e) => outcome.set(Outcome::Error { message: e.to_string(), retry: false }),
            }
        }
    });

    if donation_id.is_none() {
        let text = message.unwrap_or_else(|| "결제에 실패했습니다.".to_owned());
        return rsx! { p { class: "notice error", "{text}" } };
    }
    match outcome() {
        Outcome::Loading => rsx! { p { class: "notice", "결과를 정리하는 중…" } },
        Outcome::Done(donation) => rsx! { DonationOutcome { donation } },
        Outcome::Error { message, .. } => rsx! { p { class: "notice error", "{message}" } },
    }
}

#[component]
fn DonationOutcome(donation: SentDonation) -> Element {
    let who = format!("{}님에게 {}", donation.recipient.nickname, format_won(donation.amount));
    let text = match donation.status {
        DonationStatus::Done => format!("{who}을 후원했습니다. 방송 화면에 후원이 표시됩니다."),
        DonationStatus::Failed => {
            format!("{who} 후원에 실패했습니다. {}", donation.failure_message.clone().unwrap_or_default())
        }
        DonationStatus::Canceled => "후원을 취소했습니다. 결제되지 않았습니다.".to_owned(),
        DonationStatus::Expired => "결제 시간이 지나 후원이 만료되었습니다. 결제되지 않았습니다.".to_owned(),
        DonationStatus::Pending => "결제 결과를 아직 확인하지 못했습니다. 잠시 후 후원 내역에서 확인해 주세요.".to_owned(),
    };
    rsx! {
        div { class: "notice",
            p { class: if donation.status == DonationStatus::Failed { "error" } else { "" }, "{text}" }
            div { class: "actions",
                if let Some(id) = donation.broadcast.id.clone() {
                    Link { to: Route::LiveWatch { id }, class: "button primary", "방송으로 돌아가기" }
                }
                Link { to: Route::Donations { tab: String::new() }, class: "button", "후원 내역" }
            }
        }
    }
}

fn status_label(status: DonationStatus) -> &'static str {
    match status {
        DonationStatus::Pending => "결제 대기",
        DonationStatus::Done => "완료",
        DonationStatus::Failed => "실패",
        DonationStatus::Canceled => "취소",
        DonationStatus::Expired => "만료",
    }
}

fn status_class(status: DonationStatus) -> &'static str {
    match status {
        DonationStatus::Pending => "status-chip pending",
        DonationStatus::Done => "status-chip done",
        DonationStatus::Failed => "status-chip failed",
        DonationStatus::Canceled => "status-chip canceled",
        DonationStatus::Expired => "status-chip expired",
    }
}

/// 로그인한 본인의 후원 내역. 보낸 후원은 모든 상태, 받은 후원은 완료된 것만 보여 준다.
/// web/src/pages/DonationsPage.tsx와 같다. 탭은 주소의 ?tab=received로 둔다.
#[component]
pub fn Donations(tab: String) -> Element {
    let navigator = use_navigator();
    let received = tab == "received";

    rsx! {
        RequireLogin {
            div { class: "donations-page",
                h1 { "후원 내역" }
                div { class: "tabs", role: "tablist",
                    button {
                        r#type: "button",
                        role: "tab",
                        aria_selected: !received,
                        class: if received { "tab" } else { "tab active" },
                        onclick: move |_| {
                            navigator.replace(Route::Donations { tab: String::new() });
                        },
                        "보낸 후원"
                    }
                    button {
                        r#type: "button",
                        role: "tab",
                        aria_selected: received,
                        class: if received { "tab active" } else { "tab" },
                        onclick: move |_| {
                            navigator.replace(Route::Donations { tab: "received".to_owned() });
                        },
                        "받은 후원"
                    }
                }
                if received {
                    ReceivedList {}
                } else {
                    SentList {}
                }
            }
        }
    }
}

#[component]
fn SentList() -> Element {
    let items = use_resource(api::fetch_sent_donations);
    let items = match &*items.read() {
        None => return rsx! { p { class: "notice", "불러오는 중…" } },
        Some(Err(error)) => return rsx! { p { class: "notice error", "{error}" } },
        Some(Ok(items)) => items.clone(),
    };
    if items.is_empty() {
        return rsx! { p { class: "notice", "보낸 후원이 없습니다." } };
    }
    rsx! {
        ul { class: "donation-list",
            for donation in items {
                li { key: "{donation.id}", class: "donation-row",
                    div { class: "donation-row-head",
                        strong { "{donation.recipient.nickname}" }
                        span { class: "donation-amount", {format_won(donation.amount)} }
                        span { class: status_class(donation.status), {status_label(donation.status)} }
                    }
                    BroadcastLink { broadcast: donation.broadcast.clone() }
                    if let Some(message) = donation.message.as_ref().filter(|m| !m.is_empty()) {
                        p { class: "donation-message", "{message}" }
                    }
                    if let Some(message) = donation.failure_message.as_ref() {
                        p { class: "hint error", "{message}" }
                    }
                    p { class: "meta", {format_date(&donation.created_at)} }
                }
            }
        }
    }
}

#[component]
fn ReceivedList() -> Element {
    let data = use_resource(api::fetch_received_donations);
    let data = match &*data.read() {
        None => return rsx! { p { class: "notice", "불러오는 중…" } },
        Some(Err(error)) => return rsx! { p { class: "notice error", "{error}" } },
        Some(Ok(data)) => data.clone(),
    };
    let count = format_number(data.total_count);
    rsx! {
        p { class: "donation-total",
            "지금까지 "
            strong { "{count}건" }
            ", "
            strong { class: "donation-amount", {format_won(data.total_amount)} }
            "을 받았습니다."
        }
        if data.items.is_empty() {
            p { class: "notice", "받은 후원이 없습니다." }
        } else {
            ul { class: "donation-list",
                for donation in data.items {
                    li { key: "{donation.id}", class: "donation-row",
                        div { class: "donation-row-head",
                            strong { "{donation.donor.nickname}" }
                            span { class: "donation-amount", {format_won(donation.amount)} }
                        }
                        BroadcastLink { broadcast: donation.broadcast.clone() }
                        if let Some(message) = donation.message.as_ref().filter(|m| !m.is_empty()) {
                            p { class: "donation-message", "{message}" }
                        }
                        p { class: "meta", {format_date(&donation.completed_at)} }
                    }
                }
            }
        }
    }
}

#[component]
fn BroadcastLink(broadcast: BroadcastRef) -> Element {
    match broadcast.id {
        Some(id) => rsx! {
            Link { to: Route::LiveWatch { id }, class: "meta donation-broadcast", "{broadcast.title}" }
        },
        None => rsx! {
            span { class: "meta donation-broadcast", "삭제된 방송 · {broadcast.title}" }
        },
    }
}
