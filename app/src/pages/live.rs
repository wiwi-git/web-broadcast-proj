use std::time::Duration;

use chrono::Utc;
use dioxus::prelude::*;

use crate::api::{self, ApiError, BROADCAST_TITLE_MAX_LENGTH, Broadcast as BroadcastInfo, BroadcastList, BroadcastStatus};
use crate::auth::{RequireLogin, use_auth};
use crate::components::{BroadcastDonations, ConfirmButton, DonationAlert, HlsPlayer, LiveComments, use_broadcast_donations};
use crate::config;
use crate::route::Route;
use crate::time::{format_date, format_elapsed, format_relative, sleep};

const REFRESH_INTERVAL: Duration = Duration::from_secs(10);

/// web/src/pages/LiveListPage.tsx와 같은 화면
#[component]
pub fn LiveList() -> Element {
    let mut list = use_signal(|| None::<BroadcastList>);
    let mut error = use_signal(|| None::<String>);

    // 새로 시작하거나 끝난 방송이 반영되도록 주기적으로 다시 읽는다.
    // 화면을 벗어나면 이 future도 함께 멈춘다.
    use_future(move || async move {
        loop {
            match api::fetch_broadcasts().await {
                Ok(data) => {
                    list.set(Some(data));
                    error.set(None);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            sleep(REFRESH_INTERVAL).await;
        }
    });

    let Some(list) = list() else {
        return match error() {
            Some(message) => rsx! { p { class: "notice error", "{message}" } },
            None => rsx! { p { class: "notice", "불러오는 중…" } },
        };
    };

    rsx! {
        h1 { class: "page-title", "라이브" }
        if list.live.is_empty() {
            div { class: "notice compact",
                p { "지금 방송 중인 방송이 없습니다." }
                Link { to: Route::Broadcast {}, class: "button primary", "방송하기" }
            }
        } else {
            ul { class: "video-grid",
                for broadcast in list.live {
                    li { key: "{broadcast.id}",
                        BroadcastCard { broadcast }
                    }
                }
            }
        }
        if !list.recently_ended.is_empty() {
            h2 { class: "section-title", "종료된 방송" }
            ul { class: "video-grid",
                for broadcast in list.recently_ended {
                    li { key: "{broadcast.id}",
                        BroadcastCard { broadcast }
                    }
                }
            }
        }
    }
}

#[component]
fn BroadcastCard(broadcast: BroadcastInfo) -> Element {
    let live = broadcast.status == BroadcastStatus::Live;
    let when = match (live, &broadcast.started_at, &broadcast.ended_at) {
        (true, Some(started), _) => format!("{} 시작", format_relative(started)),
        (_, _, Some(ended)) => format!("{} 종료", format_date(ended)),
        _ => String::new(),
    };

    rsx! {
        Link {
            to: Route::LiveWatch { id: broadcast.id.clone() },
            class: if live { "video-card" } else { "video-card ended" },
            div { class: "thumbnail live-thumbnail",
                span { class: if live { "badge live" } else { "badge" },
                    if live { "LIVE" } else { "종료" }
                }
            }
            h3 { class: "video-card-title", "{broadcast.title}" }
            p { class: "meta", "{broadcast.owner.nickname}" }
            p { class: "meta", "{when}" }
        }
    }
}

/// 서버는 미디어 서버 상태를 2초마다 반영하므로(ADR-0002) 3초면 충분히 따라간다
const POLL_INTERVAL: Duration = Duration::from_secs(3);

/// Broadcast를 주기적으로 다시 읽는다. 종료되면 멈춘다. id가 None이면 읽지 않는다.
/// web/src/live/useBroadcast.ts와 같다.
fn use_broadcast(id: Option<String>) -> (Option<BroadcastInfo>, Option<String>) {
    let mut loaded = use_signal(|| None::<BroadcastInfo>);
    let mut error = use_signal(|| None::<String>);

    let watched = id.clone();
    use_resource(use_reactive!(|watched| async move {
        let Some(id) = watched else { return };
        loop {
            match api::fetch_broadcast(&id).await {
                Ok(data) => {
                    let ended = data.status == BroadcastStatus::Ended;
                    loaded.set(Some(data));
                    error.set(None);
                    if ended {
                        return;
                    }
                }
                Err(ApiError::Server { status: 404, .. }) => {
                    error.set(Some("방송을 찾을 수 없습니다.".to_owned()));
                    return;
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            sleep(POLL_INTERVAL).await;
        }
    }));

    // 다른 방송으로 이동한 직후에는 이전 방송을 보여주지 않는다
    (loaded().filter(|b| Some(&b.id) == id.as_ref()), error())
}

/// since부터 지금까지의 경과 시간을 1초마다 갱신한다
fn use_elapsed(since: Option<String>) -> String {
    let mut now = use_signal(Utc::now);
    use_future(move || async move {
        loop {
            sleep(Duration::from_secs(1)).await;
            now.set(Utc::now());
        }
    });
    since.map(|since| format_elapsed(&since, now())).unwrap_or_default()
}

/// web/src/pages/LiveWatchPage.tsx와 같은 화면
#[component]
pub fn LiveWatch(id: String) -> Element {
    let auth = use_auth();
    let (polled, error) = use_broadcast(Some(id.clone()));
    // Owner가 방금 바꾼 제목을 다음 조회 전까지 보여 준다
    let mut renamed = use_signal(|| None::<BroadcastInfo>);
    let broadcast = match (renamed(), polled) {
        (Some(r), Some(p)) if r.id == p.id && r.status == p.status => Some(r),
        (_, polled) => polled,
    };
    let status = broadcast.as_ref().map(|b| b.status);
    let elapsed = use_elapsed(broadcast.as_ref().filter(|b| b.status == BroadcastStatus::Live).and_then(|b| b.started_at.clone()));
    let BroadcastDonations { donations, mut arrived } =
        use_broadcast_donations(Some(id.clone()), status.is_some_and(|s| s != BroadcastStatus::Ended));

    let Some(broadcast) = broadcast else {
        return match error {
            Some(message) => rsx! { p { class: "notice error", "{message}" } },
            None => rsx! { p { class: "notice", "불러오는 중…" } },
        };
    };
    let live = broadcast.status == BroadcastStatus::Live;
    let me = auth.me();
    let owned = me.as_ref().is_some_and(|me| me.id == broadcast.owner.id);
    let donate = Route::Donate { id: broadcast.id.clone() };
    let donate = if me.is_some() { donate } else { donate.login_then() };
    let alert = arrived.read().front().cloned();

    rsx! {
        div { class: "live-layout",
            article { class: "live-main",
                div { class: "stage",
                    match (&broadcast.status, &broadcast.playback_url) {
                        (BroadcastStatus::Live, Some(src)) => rsx! {
                            HlsPlayer { key: "{broadcast.id}", src: src.clone(), webrtc_url: broadcast.webrtc_url.clone(), autoplay: true }
                        },
                        (BroadcastStatus::Pending, _) => rsx! {
                            div { class: "player-placeholder",
                                div { class: "spinner" }
                                p { "방송 준비 중입니다." }
                            }
                        },
                        _ => rsx! {
                            div { class: "player-placeholder", p { "방송이 종료되었습니다." } }
                        },
                    }
                    if let Some(donation) = alert.filter(|_| live) {
                        DonationAlert {
                            key: "{donation.id}",
                            donation,
                            on_done: move |_| {
                                arrived.write().pop_front();
                            },
                        }
                    }
                }

                div { class: "title-row",
                    h1 { class: "video-title", "{broadcast.title}" }
                    if live && !owned {
                        Link { to: donate, class: "button primary", "후원하기" }
                    }
                }
                p { class: "meta",
                    span { class: "owner-name", "{broadcast.owner.nickname}" }
                    if live {
                        " · "
                        span { class: "badge live inline", "LIVE" }
                        " {elapsed} 방송 중"
                    }
                    if let Some(ended) = broadcast.ended_at.as_ref().filter(|_| broadcast.status == BroadcastStatus::Ended) {
                        " · {format_date(ended)} 종료"
                    }
                }

                if owned {
                    OwnerControls { broadcast: broadcast.clone(), on_renamed: move |b| renamed.set(Some(b)) }
                }
            }

            LiveComments {
                key: "{broadcast.id}",
                broadcast_id: broadcast.id.clone(),
                active: broadcast.status != BroadcastStatus::Ended,
                donations: donations(),
            }
        }
    }
}

/// Owner에게만 보인다. 서버도 Owner인지 다시 확인한다.
#[component]
fn OwnerControls(broadcast: BroadcastInfo, on_renamed: EventHandler<BroadcastInfo>) -> Element {
    let navigator = use_navigator();
    let mut editing = use_signal(|| false);
    let mut title = use_signal(|| broadcast.title.clone());
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    let rename_id = broadcast.id.clone();
    let rename = move |event: FormEvent| {
        let id = rename_id.clone();
        async move {
            event.prevent_default();
            busy.set(true);
            error.set(None);
            match api::rename_broadcast(&id, title().trim()).await {
                Ok(saved) => {
                    on_renamed.call(saved);
                    editing.set(false);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            busy.set(false);
        }
    };

    let end_id = broadcast.id.clone();
    let end = move |_| {
        let id = end_id.clone();
        spawn(async move {
            busy.set(true);
            error.set(None);
            if let Err(e) = api::end_broadcast(&id).await {
                error.set(Some(e.to_string()));
            }
            busy.set(false);
        });
    };

    let delete_id = broadcast.id.clone();
    let delete = move |_| {
        let id = delete_id.clone();
        spawn(async move {
            busy.set(true);
            error.set(None);
            match api::delete_broadcast(&id).await {
                Ok(()) => {
                    navigator.replace(Route::LiveList {});
                }
                Err(e) => {
                    error.set(Some(e.to_string()));
                    busy.set(false);
                }
            }
        });
    };

    let current_title = broadcast.title.clone();
    rsx! {
        div { class: "owner-panel",
            if editing() {
                form { class: "owner-actions", onsubmit: rename,
                    input {
                        r#type: "text",
                        class: "text-input",
                        maxlength: BROADCAST_TITLE_MAX_LENGTH as i64,
                        value: "{title}",
                        oninput: move |e| title.set(e.value()),
                        onmounted: move |e| async move {
                            let _ = e.set_focus(true).await;
                        },
                    }
                    button { r#type: "button", class: "button small", onclick: move |_| editing.set(false), "취소" }
                    button {
                        r#type: "submit",
                        class: "button primary small",
                        disabled: busy() || title().trim().is_empty(),
                        "저장"
                    }
                }
            } else {
                div { class: "owner-actions",
                    button {
                        r#type: "button",
                        class: "button small",
                        onclick: move |_| {
                            title.set(current_title.clone());
                            editing.set(true);
                        },
                        "제목 수정"
                    }
                    if broadcast.status != BroadcastStatus::Ended {
                        ConfirmButton {
                            class: "button small",
                            disabled: busy(),
                            message: "방송을 종료할까요?",
                            confirm_label: "종료",
                            on_confirm: end,
                            "방송 종료"
                        }
                    }
                    ConfirmButton {
                        class: "button small danger",
                        disabled: busy(),
                        message: "방송을 삭제할까요? 댓글도 함께 삭제되며 되돌릴 수 없습니다. 받은 후원 기록은 남습니다.",
                        confirm_label: "삭제",
                        on_confirm: delete,
                        "삭제"
                    }
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
        }
    }
}

const BROADCASTER_JS: Asset = asset!("/assets/broadcaster.js");
const CAMERA_ID: &str = "broadcaster-camera";

/// assets/broadcaster.js의 window.__broadcaster를 부르고 결과를 받는다.
/// 카메라 영상(MediaStream)은 WebView 안에만 있으므로 카메라와 송출은 JS가 맡는다.
async fn broadcaster(call: &str) -> serde_json::Value {
    let src = serde_json::to_string(&BROADCASTER_JS.to_string()).unwrap_or_default();
    let script = format!(
        r#"
        if (!window.__broadcaster) {{
          await new Promise((resolve, reject) => {{
            const script = document.createElement('script');
            script.src = {src};
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
          }});
        }}
        dioxus.send((await window.__broadcaster.{call}) ?? null);
        "#
    );
    document::eval(&script).recv::<serde_json::Value>().await.unwrap_or_default()
}

/// {error: "..."}이면 그 문구
fn bridge_error(result: &serde_json::Value) -> Option<String> {
    result.get("error").and_then(|e| e.as_str()).map(str::to_owned)
}

/// 송출할 대상
#[derive(Debug, Clone, Copy, PartialEq)]
enum Source {
    Camera,
    /// 데스크톱에서 화면(창·전체 화면)을 공유한다. 소리는 마이크로 보낸다
    Screen,
}

/// - Preview: 카메라 미리보기, 제목 입력
/// - Starting: Broadcast를 만들고 미디어 서버에 연결하는 중
/// - OnAir: 송출 중. 서버 상태가 준비 → 방송 중으로 바뀌는 것을 지켜본다
/// - Ended: Broadcaster가 직접 종료함
#[derive(Debug, Clone, PartialEq)]
enum Phase {
    Preview,
    Starting,
    OnAir { id: String, title: String },
    Ended,
}

/// web/src/pages/BroadcastPage.tsx와 같은 화면
#[component]
pub fn Broadcast() -> Element {
    rsx! {
        RequireLogin { Broadcaster {} }
    }
}

#[component]
fn Broadcaster() -> Element {
    let mut facing = use_signal(|| "user");
    // 무엇을 송출할지. 화면 공유는 지원하는 기기(데스크톱)에서만 고를 수 있다
    let mut source = use_signal(|| Source::Camera);
    let can_share_screen = use_resource(|| async { broadcaster("canShareScreen()").await.as_bool() == Some(true) });
    let can_share_screen = can_share_screen().unwrap_or(false);
    let mut camera_ready = use_signal(|| false);
    let mut camera_error = use_signal(|| None::<String>);
    let mut title = use_signal(String::new);
    let mut phase = use_signal(|| Phase::Preview);
    let mut error = use_signal(|| None::<String>);
    // 이미 진행 중인 내 방송이 있어 시작하지 못했을 때 그 방송
    let mut blocking_id = use_signal(|| None::<String>);

    let on_air_id = match phase() {
        Phase::OnAir { id, .. } => Some(id),
        _ => None,
    };
    let (broadcast, _) = use_broadcast(on_air_id.clone());
    // 송출이 끊기면 서버가 먼저 종료로 바꾼다
    let dropped = broadcast.as_ref().is_some_and(|b| b.status == BroadcastStatus::Ended);
    let live_since = broadcast.as_ref().filter(|b| b.status == BroadcastStatus::Live).and_then(|b| b.started_at.clone());
    let is_live = live_since.is_some();
    let elapsed = use_elapsed(live_since);
    let transmitting = matches!(phase(), Phase::Starting | Phase::OnAir { .. }) && !dropped;
    // 방송자도 휴대폰 화면에서 바로 후원을 볼 수 있게 미리보기 위에 띄운다
    let BroadcastDonations { donations, mut arrived } = use_broadcast_donations(on_air_id.clone(), is_live);
    let camera_wanted = phase() != Phase::Ended && !dropped;

    // 카메라나 화면 열기. 전환하면 기존 트랙을 닫고 새로 연다
    use_effect(use_reactive!(|(camera_wanted, facing, source)| {
        let facing = facing();
        let source = source();
        spawn(async move {
            camera_ready.set(false);
            if !camera_wanted {
                broadcaster("closeCamera()").await;
                return;
            }
            let call = match source {
                Source::Camera => format!("openCamera({CAMERA_ID:?}, {facing:?})"),
                Source::Screen => format!("openScreen({CAMERA_ID:?})"),
            };
            let result = broadcaster(&call).await;
            match bridge_error(&result) {
                Some(message) => camera_error.set(Some(message)),
                None => {
                    camera_error.set(None);
                    camera_ready.set(true);
                }
            }
        });
    }));

    use_effect(use_reactive!(|transmitting| {
        spawn(async move {
            broadcaster(&format!("keepAwake({transmitting})")).await;
        });
    }));

    use_effect(use_reactive!(|dropped| {
        if dropped {
            spawn(async move {
                broadcaster("stopPublish()").await;
            });
        }
    }));

    // 송출 연결이 끊기면 알린다
    use_resource(use_reactive!(|on_air_id| async move {
        if on_air_id.is_none() {
            return;
        }
        loop {
            sleep(Duration::from_secs(2)).await;
            if broadcaster("failed()").await.as_bool() == Some(true) {
                error.set(Some("송출이 끊겼습니다. 연결이 끊겼거나 화면 공유를 멈췄습니다.".to_owned()));
                return;
            }
        }
    }));

    // 화면을 떠나면 송출과 카메라를 끊는다. 서버는 끊김을 감지해 종료로 바꾼다
    use_drop(|| {
        document::eval("window.__broadcaster?.stopPublish(); window.__broadcaster?.closeCamera(); window.__broadcaster?.keepAwake(false);");
    });

    let start = move |event: FormEvent| async move {
        event.prevent_default();
        let name = title().trim().to_owned();
        if !camera_ready() || name.is_empty() {
            return;
        }
        error.set(None);
        blocking_id.set(None);
        phase.set(Phase::Starting);
        let started = match api::start_broadcast(&name).await {
            Ok(started) => started,
            Err(e) => {
                if let ApiError::Server { problem, .. } = &e {
                    blocking_id.set(problem.get("activeBroadcastId").and_then(|v| v.as_str()).map(str::to_owned));
                }
                error.set(Some(e.to_string()));
                phase.set(Phase::Preview);
                return;
            }
        };
        let whip = format!("{}?key={}", config::resolve(&started.whip_url), api::encode(&started.stream_key));
        let whip = serde_json::to_string(&whip).unwrap_or_default();
        let result = broadcaster(&format!("publish({whip})")).await;
        if let Some(message) = bridge_error(&result) {
            // 만들어 둔 Broadcast는 바로 끝낸다. 실패해도 준비 시간이 지나면 서버가 종료한다
            let _ = api::end_broadcast(&started.broadcast.id).await;
            error.set(Some(message));
            phase.set(Phase::Preview);
            return;
        }
        phase.set(Phase::OnAir { id: started.broadcast.id, title: name });
    };

    // 다른 탭이나 기기에서 진행 중이던 내 방송을 끝낸다
    let end_blocking = move |_| {
        spawn(async move {
            let Some(id) = blocking_id() else { return };
            match api::end_broadcast(&id).await {
                Ok(()) => {
                    blocking_id.set(None);
                    error.set(None);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
        });
    };

    let end = move |_| {
        spawn(async move {
            let Phase::OnAir { id, .. } = phase() else { return };
            broadcaster("stopPublish()").await;
            // 송출은 이미 끊었으므로 실패해도 서버가 곧 종료로 처리한다
            let _ = api::end_broadcast(&id).await;
            phase.set(Phase::Ended);
        });
    };

    if phase() == Phase::Ended || dropped {
        let message = if dropped {
            "송출이 끊겼거나 다른 화면에서 종료해 방송이 끝났습니다."
        } else {
            "방송을 종료했습니다."
        };
        return rsx! {
            div { class: "notice",
                p { "{message}" }
                div { class: "actions",
                    button {
                        class: "button primary",
                        onclick: move |_| {
                            title.set(String::new());
                            error.set(None);
                            phase.set(Phase::Preview);
                        },
                        "새 방송 시작"
                    }
                    Link { to: Route::LiveList {}, class: "button", "라이브 목록" }
                }
            }
        };
    }

    let alert = arrived.read().front().cloned().filter(|_| is_live);
    let starting = phase() == Phase::Starting;
    rsx! {
        div { class: "broadcaster",
            div { class: "camera",
                video {
                    id: CAMERA_ID,
                    // 전면 카메라 미리보기만 거울처럼 보여 준다
                    class: if source() == Source::Camera && facing() == "user" { "mirrored" } else { "" },
                    autoplay: true,
                    muted: true,
                    playsinline: true,
                }
                if let Some(message) = camera_error() {
                    p { class: "camera-message error", "{message}" }
                }
                if transmitting {
                    span { class: if is_live { "badge live camera-badge" } else { "badge camera-badge" },
                        if is_live { "LIVE {elapsed}" } else { "연결 중…" }
                    }
                }
                if let Some(donation) = alert {
                    DonationAlert {
                        key: "{donation.id}",
                        donation,
                        on_done: move |_| {
                            arrived.write().pop_front();
                        },
                    }
                }
                if phase() == Phase::Preview && source() == Source::Camera {
                    button {
                        r#type: "button",
                        class: "button camera-switch",
                        onclick: move |_| facing.set(if facing() == "user" { "environment" } else { "user" }),
                        "카메라 전환"
                    }
                }
            }

            if let Phase::OnAir { id: on_air_id, title: on_air_title } = phase() {
                div { class: "broadcaster-controls",
                    p { class: "broadcaster-title", "{on_air_title}" }
                    if let Some(message) = error() {
                        p { class: "hint error", "{message}" }
                    }
                    ConfirmButton {
                        class: "button danger",
                        message: "방송을 종료할까요?",
                        confirm_label: "종료",
                        on_confirm: end,
                        "방송 종료"
                    }
                }
                // 방송하면서 시청자 댓글과 후원을 본다
                LiveComments { broadcast_id: on_air_id.clone(), active: true, donations: donations() }
            } else {
                form { class: "broadcaster-controls", onsubmit: start,
                    if can_share_screen {
                        div { class: "source-switch", role: "group", "aria-label": "송출할 대상",
                            for (option , label) in [(Source::Camera, "카메라"), (Source::Screen, "화면")] {
                                button {
                                    r#type: "button",
                                    class: if source() == option { "button primary" } else { "button" },
                                    "aria-pressed": source() == option,
                                    disabled: starting,
                                    onclick: move |_| source.set(option),
                                    "{label}"
                                }
                            }
                        }
                    }
                    input {
                        r#type: "text",
                        class: "text-input",
                        value: "{title}",
                        maxlength: BROADCAST_TITLE_MAX_LENGTH as i64,
                        disabled: starting,
                        placeholder: "방송 제목",
                        oninput: move |e| title.set(e.value()),
                    }
                    if let Some(message) = error() {
                        p { class: "hint error", "{message}" }
                    }
                    if blocking_id.read().is_some() {
                        ConfirmButton {
                            message: "진행 중인 이전 방송을 종료할까요?",
                            confirm_label: "종료",
                            on_confirm: end_blocking,
                            "이전 방송 종료"
                        }
                    }
                    button {
                        r#type: "submit",
                        class: "button primary",
                        disabled: !camera_ready() || title().trim().is_empty() || starting,
                        if starting { "연결 중…" } else { "방송 시작" }
                    }
                }
            }
        }
    }
}
