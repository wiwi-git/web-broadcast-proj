//! 서버 API. 타입과 제약은 web/src/api.ts, 서버 응답 레코드와 맞춘다.
//!
//! 지금은 회원·동영상·라이브 조회만 옮겼다. 댓글·후원·업로드는 해당 화면을 만들 때 같은 방식으로 추가한다.

// 화면이 아직 쓰지 않는 함수가 있다. 화면을 다 옮기면 지운다.
#![allow(dead_code)]

mod client;

pub use client::{ApiError, ApiResult};

use client::{get_json, send_empty, send_json};
use reqwest::Method;
use serde::{Deserialize, Serialize};

fn encode(segment: &str) -> String {
    segment
        .bytes()
        .map(|byte| match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (byte as char).to_string(),
            _ => format!("%{byte:02X}"),
        })
        .collect()
}

/// 다른 사람에게 보이는 회원 정보
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct UserSummary {
    pub id: String,
    pub nickname: String,
}

// 회원

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Me {
    pub id: String,
    pub username: String,
    pub nickname: String,
}

#[derive(Deserialize)]
struct MaybeUser {
    user: Option<Me>,
}

#[derive(Deserialize)]
struct User {
    user: Me,
}

#[derive(Serialize)]
pub struct LogInInput {
    pub username: String,
    pub password: String,
}

#[derive(Serialize)]
pub struct SignUpInput {
    pub username: String,
    pub password: String,
    pub nickname: String,
}

/// 로그인하지 않았으면 None. 이 요청으로 CSRF 토큰 쿠키도 받는다.
pub async fn fetch_me() -> ApiResult<Option<Me>> {
    Ok(get_json::<MaybeUser>("/api/auth/me").await?.user)
}

pub async fn log_in(input: &LogInInput) -> ApiResult<Me> {
    Ok(send_json::<User>(Method::POST, "/api/auth/login", input).await?.user)
}

pub async fn sign_up(input: &SignUpInput) -> ApiResult<Me> {
    Ok(send_json::<User>(Method::POST, "/api/auth/signup", input).await?.user)
}

pub async fn log_out() -> ApiResult<()> {
    send_empty(Method::POST, "/api/auth/logout").await
}

// 동영상

#[derive(Debug, Clone, Copy, PartialEq, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum VideoStatus {
    Processing,
    Published,
    Failed,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Video {
    pub id: String,
    pub title: String,
    pub description: Option<String>,
    pub status: VideoStatus,
    pub owner: UserSummary,
    /// 소수로 온다 (예: 156.27)
    pub duration_seconds: Option<f64>,
    pub created_at: String,
    /// 공개 상태일 때만 존재
    pub playback_url: Option<String>,
    pub thumbnail_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VideoPage {
    pub items: Vec<Video>,
    pub page: u32,
    pub total_pages: u32,
    pub total_items: u64,
}

pub async fn fetch_videos(page: u32) -> ApiResult<VideoPage> {
    get_json(&format!("/api/videos?page={page}")).await
}

pub async fn fetch_video(id: &str) -> ApiResult<Video> {
    get_json(&format!("/api/videos/{}", encode(id))).await
}

// 서버(Video, VideoService)와 같은 제약
pub const TITLE_MAX_LENGTH: usize = 100;
pub const DESCRIPTION_MAX_LENGTH: usize = 5000;

#[derive(Serialize)]
pub struct VideoEdit {
    pub title: String,
    pub description: String,
}

pub async fn edit_video(id: &str, input: &VideoEdit) -> ApiResult<Video> {
    send_json(Method::PATCH, &format!("/api/videos/{}", encode(id)), input).await
}

pub async fn delete_video(id: &str) -> ApiResult<()> {
    send_empty(Method::DELETE, &format!("/api/videos/{}", encode(id))).await
}

// 댓글

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Comment {
    pub id: String,
    pub body: String,
    pub author: UserSummary,
    pub created_at: String,
    /// 수정한 적이 있을 때만 존재
    pub updated_at: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VideoComments {
    pub items: Vec<Comment>,
    /// 다음 요청의 before로 넘긴다. None이면 더 없다
    pub next_cursor: Option<String>,
    pub total_count: u64,
}

pub const COMMENT_MAX_LENGTH: usize = 500;

#[derive(Serialize)]
struct CommentBody<'a> {
    body: &'a str,
}

pub async fn fetch_video_comments(video_id: &str, before: Option<&str>) -> ApiResult<VideoComments> {
    let query = before.map(|cursor| format!("?before={}", encode(cursor))).unwrap_or_default();
    get_json(&format!("/api/videos/{}/comments{query}", encode(video_id))).await
}

pub async fn write_video_comment(video_id: &str, body: &str) -> ApiResult<Comment> {
    send_json(Method::POST, &format!("/api/videos/{}/comments", encode(video_id)), &CommentBody { body }).await
}

pub async fn edit_comment(id: &str, body: &str) -> ApiResult<Comment> {
    send_json(Method::PATCH, &format!("/api/comments/{}", encode(id)), &CommentBody { body }).await
}

pub async fn delete_comment(id: &str) -> ApiResult<()> {
    send_empty(Method::DELETE, &format!("/api/comments/{}", encode(id))).await
}

// 라이브

#[derive(Debug, Clone, Copy, PartialEq, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BroadcastStatus {
    Pending,
    Live,
    Ended,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Broadcast {
    pub id: String,
    pub title: String,
    pub status: BroadcastStatus,
    pub owner: UserSummary,
    pub created_at: String,
    pub started_at: Option<String>,
    pub ended_at: Option<String>,
    /// 방송 중일 때만 존재
    pub playback_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BroadcastList {
    pub live: Vec<Broadcast>,
    pub recently_ended: Vec<Broadcast>,
}

pub async fn fetch_broadcasts() -> ApiResult<BroadcastList> {
    get_json("/api/broadcasts").await
}

pub async fn fetch_broadcast(id: &str) -> ApiResult<Broadcast> {
    get_json(&format!("/api/broadcasts/{}", encode(id))).await
}

/// 실제 서버에 붙어 데스크톱·모바일 경로(앱 쿠키 저장소 + CSRF 헤더)를 확인한다.
/// 서버를 띄운 뒤 `cargo test --no-default-features --features desktop -- --ignored`로 실행한다.
#[cfg(all(test, not(target_arch = "wasm32")))]
mod live_server_tests {
    use super::*;

    #[tokio::test]
    #[ignore = "localhost:8180에 서버가 떠 있어야 한다"]
    async fn signs_up_logs_out_and_logs_in_with_cookie_session() {
        let suffix = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() % 1_000_000_000;
        let username = format!("app{suffix}");
        let password = format!("pw-{suffix}-check");

        // 첫 요청으로 XSRF-TOKEN 쿠키를 받는다
        assert_eq!(fetch_me().await.unwrap(), None);

        let me = sign_up(&SignUpInput { username: username.clone(), password: password.clone(), nickname: format!("앱{}", suffix % 10_000) })
            .await
            .unwrap();
        assert_eq!(fetch_me().await.unwrap().as_ref(), Some(&me), "가입 뒤 세션 쿠키로 로그인 상태가 유지된다");

        log_out().await.unwrap();
        assert_eq!(fetch_me().await.unwrap(), None, "로그아웃(CSRF 헤더 필요)이 받아들여진다");

        let again = log_in(&LogInInput { username, password }).await.unwrap();
        assert_eq!(again.id, me.id);
        assert!(fetch_me().await.unwrap().is_some());

        fetch_videos(0).await.unwrap();
        fetch_broadcasts().await.unwrap();
        log_out().await.unwrap();
    }
}
