//! 동영상 업로드. web/src/api.ts의 uploadVideo와 같다.
//!
//! 진행률을 화면에 보여 주기 위해 보낸 바이트 수를 `sent`에 계속 더한다. 화면은 이 값을 주기적으로 읽는다.
//! - 데스크톱·모바일: 로그인 쿠키가 앱의 쿠키 저장소에 있으므로 reqwest로 보낸다. 파일을 조금씩 읽어 보내며 센다.
//! - 웹: reqwest(fetch)는 업로드 진행률을 알려주지 않으므로 웹 원본처럼 XMLHttpRequest를 쓴다.

use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};

use dioxus::html::FileData;

use super::{ApiError, ApiResult, Video};

// 서버(application.properties)와 같은 제약
pub const MAX_FILE_BYTES: u64 = 500 * 1024 * 1024;
pub const ALLOWED_EXTENSIONS: [&str; 5] = ["mp4", "mov", "mkv", "webm", "avi"];

/// 서버로 보내기 전에 막을 수 있는 문제를 먼저 걸러낸다
pub fn file_problem(name: &str, size: u64) -> Option<String> {
    let extension = name.rsplit_once('.').map(|(_, ext)| ext.to_lowercase()).unwrap_or_default();
    if !ALLOWED_EXTENSIONS.contains(&extension.as_str()) {
        return Some(format!("지원하지 않는 형식입니다. ({})", ALLOWED_EXTENSIONS.join(", ")));
    }
    if size > MAX_FILE_BYTES {
        return Some("파일은 500MB 이하여야 합니다.".to_owned());
    }
    None
}

pub struct UploadInput {
    pub title: String,
    pub description: String,
}

#[cfg(not(target_arch = "wasm32"))]
pub async fn upload_video(file: &FileData, input: UploadInput, sent: Arc<AtomicU64>) -> ApiResult<Video> {
    upload_path(&file.path(), file.name(), file.size(), input, sent).await
}

/// 데스크톱·모바일의 파일 선택은 파일 경로를 준다
#[cfg(not(target_arch = "wasm32"))]
pub async fn upload_path(
    path: &std::path::Path,
    name: String,
    size: u64,
    input: UploadInput,
    sent: Arc<AtomicU64>,
) -> ApiResult<Video> {
    use futures_util::TryStreamExt;
    use reqwest::multipart::{Form, Part};
    use reqwest::{Body, Method};

    let opened = tokio::fs::File::open(path)
        .await
        .map_err(|e| ApiError::Network(format!("파일을 열지 못했습니다: {e}")))?;
    let stream = tokio_util::io::ReaderStream::new(opened).inspect_ok(move |chunk| {
        sent.fetch_add(chunk.len() as u64, Ordering::Relaxed);
    });
    let part = Part::stream_with_length(Body::wrap_stream(stream), size).file_name(name);
    let form = Form::new().part("file", part).text("title", input.title).text("description", input.description);

    let response = super::client::check(super::client::request(Method::POST, "/api/videos").multipart(form).send().await?).await?;
    Ok(response.json().await?)
}

#[cfg(target_arch = "wasm32")]
pub async fn upload_video(file: &FileData, input: UploadInput, sent: Arc<AtomicU64>) -> ApiResult<Video> {
    use std::cell::RefCell;
    use std::rc::Rc;
    use std::time::Duration;

    use wasm_bindgen::JsCast;
    use wasm_bindgen::closure::Closure;
    use web_sys::{FormData, ProgressEvent, XmlHttpRequest};

    let network = |_: wasm_bindgen::JsValue| ApiError::Network("네트워크 오류로 업로드에 실패했습니다.".to_owned());
    let Some(web_file) = file.inner().downcast_ref::<web_sys::File>() else {
        return Err(ApiError::Network("파일을 읽지 못했습니다.".to_owned()));
    };

    let form = FormData::new().map_err(network)?;
    form.append_with_blob("file", web_file).map_err(network)?;
    form.append_with_str("title", &input.title).map_err(network)?;
    form.append_with_str("description", &input.description).map_err(network)?;

    let xhr = XmlHttpRequest::new().map_err(network)?;
    xhr.open("POST", "/api/videos").map_err(network)?;
    if let Some(token) = super::client::xsrf_token() {
        xhr.set_request_header("X-XSRF-TOKEN", &token).map_err(network)?;
    }

    let on_progress = Closure::<dyn FnMut(ProgressEvent)>::new(move |event: ProgressEvent| {
        if event.length_computable() {
            sent.store(event.loaded() as u64, Ordering::Relaxed);
        }
    });
    xhr.upload().map_err(network)?.set_onprogress(Some(on_progress.as_ref().unchecked_ref()));

    // 끝나면(성공·실패 모두) done이 채워진다. 화면 갱신과 같은 방식으로 짧게 기다리며 확인한다
    let done = Rc::new(RefCell::new(None::<bool>));
    let on_load = {
        let done = done.clone();
        Closure::<dyn FnMut()>::new(move || *done.borrow_mut() = Some(true))
    };
    let on_error = {
        let done = done.clone();
        Closure::<dyn FnMut()>::new(move || *done.borrow_mut() = Some(false))
    };
    xhr.set_onload(Some(on_load.as_ref().unchecked_ref()));
    xhr.set_onerror(Some(on_error.as_ref().unchecked_ref()));
    xhr.send_with_opt_form_data(Some(&form)).map_err(network)?;

    let loaded = loop {
        if let Some(loaded) = *done.borrow() {
            break loaded;
        }
        crate::time::sleep(Duration::from_millis(200)).await;
    };
    if !loaded {
        return Err(network(wasm_bindgen::JsValue::NULL));
    }
    let status = xhr.status().unwrap_or(0);
    let body = xhr.response_text().ok().flatten().unwrap_or_default();
    if !(200..300).contains(&status) {
        return Err(super::client::server_error(status, &body));
    }
    serde_json::from_str(&body).map_err(|e| ApiError::Decode(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_unknown_extension_and_large_file() {
        assert_eq!(file_problem("clip.MP4", 10), None);
        assert!(file_problem("notes.txt", 10).is_some());
        assert!(file_problem("noextension", 10).is_some());
        assert_eq!(file_problem("big.mov", MAX_FILE_BYTES + 1).as_deref(), Some("파일은 500MB 이하여야 합니다."));
    }
}
