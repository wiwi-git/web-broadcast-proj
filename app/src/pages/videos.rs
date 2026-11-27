use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use dioxus::html::FileData;
use dioxus::prelude::*;

use crate::api::{
    self, ApiError, DESCRIPTION_MAX_LENGTH, ReportTargetType, TITLE_MAX_LENGTH, Video, VideoEdit, VideoStatus,
};
use crate::auth::{RequireLogin, use_auth};
use crate::components::{ConfirmButton, HlsPlayer, ReportButton, VideoComments};
use crate::config;
use crate::route::Route;
use crate::time::{format_bytes, format_date, format_duration, sleep};

const POLL_INTERVAL: Duration = Duration::from_secs(3);

/// web/src/pages/VideoListPage.tsx와 같은 화면. 페이지 번호는 주소의 ?page=로 둔다.
#[component]
pub fn VideoList(page: u32) -> Element {
    let navigator = use_navigator();
    let videos = use_resource(use_reactive!(|page| api::fetch_videos(page)));

    let result = match &*videos.read() {
        None => return rsx! { p { class: "notice", "불러오는 중…" } },
        Some(Err(error)) => return rsx! { p { class: "notice error", "{error}" } },
        Some(Ok(result)) => result.clone(),
    };
    if result.total_items == 0 {
        return rsx! {
            div { class: "notice",
                p { "아직 공개된 동영상이 없습니다." }
                Link { to: Route::Upload {}, class: "button primary", "첫 동영상 업로드" }
            }
        };
    }

    rsx! {
        h1 { class: "page-title", "동영상" }
        ul { class: "video-grid",
            for video in result.items {
                li { key: "{video.id}",
                    VideoCard { video }
                }
            }
        }
        if result.total_pages > 1 {
            nav { class: "pagination",
                button {
                    class: "button",
                    disabled: page == 0,
                    onclick: move |_| {
                        navigator.push(Route::VideoList { page: page - 1 });
                    },
                    "이전"
                }
                span { "{page + 1} / {result.total_pages}" }
                button {
                    class: "button",
                    disabled: page + 1 >= result.total_pages,
                    onclick: move |_| {
                        navigator.push(Route::VideoList { page: page + 1 });
                    },
                    "다음"
                }
            }
        }
    }
}

#[component]
fn VideoCard(video: Video) -> Element {
    let thumbnail = video.thumbnail_url.as_deref().map(config::resolve);
    let duration = video.duration_seconds.map(format_duration);
    let date = format_date(&video.created_at);

    rsx! {
        Link { to: Route::VideoDetail { id: video.id.clone() }, class: "video-card",
            div { class: "thumbnail",
                if let Some(src) = thumbnail {
                    img { src, alt: "", loading: "lazy" }
                }
                if let Some(duration) = duration {
                    span { class: "duration", "{duration}" }
                }
            }
            h2 { class: "video-card-title", "{video.title}" }
            p { class: "meta", "{video.owner.nickname}" }
            p { class: "meta", "{date}" }
        }
    }
}

/// web/src/pages/VideoDetailPage.tsx와 같은 화면
#[component]
pub fn VideoDetail(id: String) -> Element {
    let auth = use_auth();
    let navigator = use_navigator();
    let mut loaded = use_signal(|| None::<Video>);
    let mut error = use_signal(|| None::<String>);
    let mut editing = use_signal(|| false);
    let mut delete_error = use_signal(|| None::<String>);

    // 처리 중이면 공개 또는 실패로 바뀔 때까지 주기적으로 다시 확인한다.
    // id가 바뀌면 이전 확인은 멈추고 새로 시작한다.
    use_resource(use_reactive!(|id| async move {
        loop {
            match api::fetch_video(&id).await {
                Ok(video) => {
                    let processing = video.status == VideoStatus::Processing;
                    loaded.set(Some(video));
                    error.set(None);
                    if !processing {
                        return;
                    }
                }
                Err(ApiError::Server { status: 404, .. }) => {
                    error.set(Some("동영상을 찾을 수 없습니다.".to_owned()));
                    return;
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            sleep(POLL_INTERVAL).await;
        }
    }));

    // 다른 동영상으로 이동한 직후에는 이전 동영상을 보여주지 않는다
    let Some(video) = loaded().filter(|video| video.id == id) else {
        return match error() {
            Some(message) => rsx! { p { class: "notice error", "{message}" } },
            None => rsx! { p { class: "notice", "불러오는 중…" } },
        };
    };
    let owned = auth.me().is_some_and(|me| me.id == video.owner.id);

    let delete_id = id.clone();
    let delete = move |_| {
        let id = delete_id.clone();
        spawn(async move {
            delete_error.set(None);
            match api::delete_video(&id).await {
                Ok(()) => {
                    navigator.replace(Route::VideoList { page: 0 });
                }
                Err(e) => delete_error.set(Some(e.to_string())),
            }
        });
    };

    let meta_date = format_date(&video.created_at);
    let meta_duration = video.duration_seconds.map(|s| format!(" · {}", format_duration(s))).unwrap_or_default();

    rsx! {
        article { class: "video-detail",
            match (&video.status, &video.playback_url) {
                (VideoStatus::Published, Some(src)) => rsx! {
                    HlsPlayer { key: "{video.id}", src: src.clone(), poster: video.thumbnail_url.clone() }
                },
                (VideoStatus::Processing, _) => rsx! {
                    div { class: "player-placeholder",
                        div { class: "spinner" }
                        p { "동영상을 처리하고 있습니다." }
                        p { class: "meta", "처리가 끝나면 자동으로 재생할 수 있게 됩니다." }
                    }
                },
                _ => rsx! {
                    div { class: "player-placeholder",
                        p { class: "error", "동영상 처리에 실패했습니다." }
                    }
                },
            }

            if editing() {
                EditVideoForm {
                    video: video.clone(),
                    on_saved: move |saved: Video| {
                        loaded.set(Some(saved));
                        editing.set(false);
                    },
                    on_cancel: move |_| editing.set(false),
                }
            } else {
                div { class: "title-row",
                    h1 { class: "video-title", "{video.title}" }
                    if owned {
                        div { class: "owner-actions",
                            button { r#type: "button", class: "button small", onclick: move |_| editing.set(true), "수정" }
                            ConfirmButton {
                                class: "button small danger",
                                message: "동영상을 삭제할까요? 댓글도 함께 삭제되며 되돌릴 수 없습니다.",
                                confirm_label: "삭제",
                                on_confirm: delete,
                                "삭제"
                            }
                        }
                    }
                }
                if let Some(message) = delete_error() {
                    p { class: "hint error", "{message}" }
                }
                p { class: "meta",
                    span { class: "owner-name", "{video.owner.nickname}" }
                    " · {meta_date}{meta_duration}"
                }
                div { class: "report-row",
                    ReportButton {
                        target_type: ReportTargetType::Video,
                        target_id: video.id.clone(),
                        owner_id: video.owner.id.clone(),
                    }
                }
                if let Some(description) = video.description.as_ref().filter(|d| !d.is_empty()) {
                    p { class: "description", "{description}" }
                }
            }

            VideoComments { key: "{video.id}", video_id: video.id.clone() }
        }
    }
}

/// Owner만 제목과 설명을 바꿀 수 있다. 영상 파일은 바꿀 수 없다.
#[component]
fn EditVideoForm(video: Video, on_saved: EventHandler<Video>, on_cancel: EventHandler<()>) -> Element {
    let mut title = use_signal(|| video.title.clone());
    let mut description = use_signal(|| video.description.clone().unwrap_or_default());
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    let submit = move |event: FormEvent| {
        let id = video.id.clone();
        async move {
            event.prevent_default();
            busy.set(true);
            error.set(None);
            let input = VideoEdit { title: title().trim().to_owned(), description: description() };
            match api::edit_video(&id, &input).await {
                Ok(saved) => on_saved.call(saved),
                Err(e) => {
                    error.set(Some(e.to_string()));
                    busy.set(false);
                }
            }
        }
    };

    rsx! {
        form { class: "edit-form", onsubmit: submit,
            label { class: "field",
                span { class: "label", "제목" }
                input {
                    r#type: "text",
                    class: "text-input",
                    maxlength: TITLE_MAX_LENGTH as i64,
                    value: "{title}",
                    oninput: move |e| title.set(e.value()),
                }
            }
            label { class: "field",
                span { class: "label", "설명" }
                textarea {
                    class: "text-input",
                    rows: 5,
                    maxlength: DESCRIPTION_MAX_LENGTH as i64,
                    value: "{description}",
                    oninput: move |e| description.set(e.value()),
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            div { class: "owner-actions",
                button { r#type: "button", class: "button", onclick: move |_| on_cancel.call(()), "취소" }
                button {
                    r#type: "submit",
                    class: "button primary",
                    disabled: busy() || title().trim().is_empty(),
                    "저장"
                }
            }
        }
    }
}

/// web/src/pages/UploadPage.tsx와 같은 화면
#[component]
pub fn Upload() -> Element {
    rsx! {
        RequireLogin { UploadForm {} }
    }
}

/// 고른 파일. iOS·안드로이드는 Dioxus의 파일 입력이 동작하지 않아 웹뷰에서 고른다 (components/webview_file.rs)
#[derive(Clone)]
enum Picked {
    Native(FileData),
    #[cfg(any(target_os = "ios", target_os = "android"))]
    WebView(crate::components::PickedFile),
}

impl Picked {
    fn name(&self) -> String {
        match self {
            Picked::Native(file) => file.name(),
            #[cfg(any(target_os = "ios", target_os = "android"))]
            Picked::WebView(file) => file.name.clone(),
        }
    }

    fn size(&self) -> u64 {
        match self {
            Picked::Native(file) => file.size(),
            #[cfg(any(target_os = "ios", target_os = "android"))]
            Picked::WebView(file) => file.size,
        }
    }
}

/// 고른 파일을 보낸다. 웹뷰에서 고른 파일은 먼저 임시 파일로 옮기고(`copied`) 보낸다(`sent`)
async fn upload(picked: &Picked, input: api::UploadInput, copied: Arc<AtomicU64>, sent: Arc<AtomicU64>) -> api::ApiResult<api::Video> {
    let _ = &copied;
    match picked {
        Picked::Native(file) => api::upload_video(file, input, sent).await,
        #[cfg(any(target_os = "ios", target_os = "android"))]
        Picked::WebView(file) => {
            let path = crate::components::copy_to_temp(&file.name, copied).await?;
            let result = api::upload_path(&path, file.name.clone(), file.size, input, sent).await;
            let _ = tokio::fs::remove_file(&path).await;
            result
        }
    }
}

#[component]
fn UploadForm() -> Element {
    let navigator = use_navigator();
    let mut file = use_signal(|| None::<Picked>);
    let mut title = use_signal(String::new);
    let mut description = use_signal(String::new);
    // 업로드 중일 때만 Some. 0.0~1.0
    let mut progress = use_signal(|| None::<f64>);
    let mut error = use_signal(|| None::<String>);

    let uploading = progress().is_some();
    let problem = file.read().as_ref().and_then(|f| api::file_problem(&f.name(), f.size()));
    let can_submit = file.read().is_some() && problem.is_none() && !title().trim().is_empty() && !uploading;
    let file_hint = match (&problem, file.read().as_ref()) {
        (Some(problem), _) => problem.clone(),
        (None, Some(f)) => format_bytes(f.size()),
        (None, None) => format!("{} · 최대 500MB", api::ALLOWED_EXTENSIONS.join(", ")),
    };
    let accept = api::ALLOWED_EXTENSIONS.iter().map(|ext| format!(".{ext}")).collect::<Vec<_>>().join(",");

    let submit = move |event: FormEvent| async move {
        event.prevent_default();
        let Some(selected) = file() else { return };
        if !can_submit {
            return;
        }
        error.set(None);
        progress.set(Some(0.0));
        // 웹뷰에서 고른 파일은 옮기기와 보내기를 반씩 센다
        let steps = if matches!(selected, Picked::Native(_)) { 1 } else { 2 };
        let total = selected.size().max(1) * steps;
        let copied = Arc::new(AtomicU64::new(0));
        let sent = Arc::new(AtomicU64::new(0));
        let input = api::UploadInput { title: title().trim().to_owned(), description: description() };

        // 보낸 양은 업로드하는 쪽이 계속 더하고, 화면은 0.2초마다 읽어 그린다
        let (watched_copy, watched_send) = (copied.clone(), sent.clone());
        let watcher = spawn(async move {
            loop {
                let done = watched_copy.load(Ordering::Relaxed) + watched_send.load(Ordering::Relaxed);
                progress.set(Some(done as f64 / total as f64));
                sleep(Duration::from_millis(200)).await;
            }
        });
        let result = upload(&selected, input, copied, sent).await;
        watcher.cancel();
        match result {
            Ok(video) => {
                navigator.push(Route::VideoDetail { id: video.id });
            }
            Err(e) => {
                error.set(Some(e.to_string()));
                progress.set(None);
            }
        }
    };

    let title_length = title.read().chars().count();
    let description_length = description.read().chars().count();
    rsx! {
        form { class: "upload-form", onsubmit: submit,
            h1 { class: "page-title", "동영상 업로드" }

            // 안의 파일 선택 버튼이 label이라 label 안에 label이 되지 않게 div로 둔다
            div { class: "field",
                span { class: "label", "영상 파일" }
                if cfg!(any(target_os = "ios", target_os = "android")) {
                    {webview_picker(accept.clone(), uploading, file)}
                } else {
                    input {
                        r#type: "file",
                        accept,
                        disabled: uploading,
                        onchange: move |e| file.set(e.files().into_iter().next().map(Picked::Native)),
                    }
                }
                span { class: if problem.is_some() { "hint error" } else { "hint" }, "{file_hint}" }
            }

            label { class: "field",
                span { class: "label", "제목" }
                input {
                    r#type: "text",
                    value: "{title}",
                    maxlength: TITLE_MAX_LENGTH as i64,
                    disabled: uploading,
                    placeholder: "동영상 제목",
                    oninput: move |e| title.set(e.value()),
                }
                span { class: "hint counter", "{title_length} / {TITLE_MAX_LENGTH}" }
            }

            label { class: "field",
                span { class: "label", "설명 (선택)" }
                textarea {
                    rows: 6,
                    value: "{description}",
                    maxlength: DESCRIPTION_MAX_LENGTH as i64,
                    disabled: uploading,
                    placeholder: "동영상에 대한 설명",
                    oninput: move |e| description.set(e.value()),
                }
                span { class: "hint counter", "{description_length} / {DESCRIPTION_MAX_LENGTH}" }
            }

            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }

            if let Some(fraction) = progress() {
                div { class: "progress", role: "progressbar", aria_valuenow: (fraction * 100.0).round() as i64,
                    div { class: "progress-bar", style: "width: {fraction * 100.0}%" }
                    span {
                        if fraction < 1.0 {
                            "업로드 중 {(fraction * 100.0).floor()}%"
                        } else {
                            "파일 확인 중…"
                        }
                    }
                }
            } else {
                button { r#type: "submit", class: "button primary", disabled: !can_submit, "업로드" }
            }
        }
    }
}

#[cfg(any(target_os = "ios", target_os = "android"))]
fn webview_picker(accept: String, disabled: bool, mut file: Signal<Option<Picked>>) -> Element {
    use crate::components::WebViewFilePicker;
    rsx! {
        div { class: "file-row",
            WebViewFilePicker { accept, disabled, on_pick: move |picked| file.set(Some(Picked::WebView(picked))) }
            if let Some(picked) = file() {
                span { class: "file-name", "{picked.name()}" }
            }
        }
    }
}

#[cfg(not(any(target_os = "ios", target_os = "android")))]
fn webview_picker(_accept: String, _disabled: bool, _file: Signal<Option<Picked>>) -> Element {
    rsx! {}
}
