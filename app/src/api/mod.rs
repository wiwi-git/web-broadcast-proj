//! 서버 API. 타입과 제약은 web/src/api.ts, 서버 응답 레코드와 맞춘다.

// 화면이 아직 쓰지 않는 함수가 있다. 화면을 다 옮기면 지운다.
#![allow(dead_code)]

mod client;
mod upload;

pub use client::{ApiError, ApiResult};
pub use upload::{ALLOWED_EXTENSIONS, UploadInput, file_problem, upload_video};

use client::{get_json, send_empty, send_json};
use reqwest::Method;
use serde::{Deserialize, Serialize};

/// URL 경로·쿼리에 넣을 값을 percent-encoding한다
pub fn encode(segment: &str) -> String {
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

pub const PASSWORD_MIN_LENGTH: usize = 8;
pub const PASSWORD_MAX_LENGTH: usize = 64;
pub const NICKNAME_MIN_LENGTH: usize = 2;
pub const NICKNAME_MAX_LENGTH: usize = 20;

/// 아이디는 영문 소문자와 숫자로 4~20자 (web의 USERNAME_PATTERN)
pub fn is_valid_username(username: &str) -> bool {
    (4..=20).contains(&username.len()) && username.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit())
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

/// 댓글을 다는 곳
#[derive(Debug, Clone, PartialEq)]
pub enum CommentTarget {
    Video(String),
    Broadcast(String),
}

impl CommentTarget {
    fn url(&self) -> String {
        match self {
            CommentTarget::Video(id) => format!("/api/videos/{}/comments", encode(id)),
            CommentTarget::Broadcast(id) => format!("/api/broadcasts/{}/comments", encode(id)),
        }
    }
}

/// 최근 100개, 오래된 순
pub async fn fetch_broadcast_comments(broadcast_id: &str) -> ApiResult<Vec<Comment>> {
    get_json(&CommentTarget::Broadcast(broadcast_id.to_owned()).url()).await
}

pub async fn write_comment(target: &CommentTarget, body: &str) -> ApiResult<Comment> {
    send_json(Method::POST, &target.url(), &CommentBody { body }).await
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
    /// 방송 중일 때만 존재. WebRTC(WHEP) 시청 주소 (ADR-0006)
    #[serde(default)]
    pub webrtc_url: Option<String>,
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

pub const BROADCAST_TITLE_MAX_LENGTH: usize = 100;

/// 시작 직후 Owner에게만 오는 응답. streamKey는 서버에 남지 않으므로 다시 필요하면 새로 발급받는다.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StartedBroadcast {
    pub broadcast: Broadcast,
    pub stream_key: String,
    pub whip_url: String,
}

#[derive(Serialize)]
struct BroadcastTitle<'a> {
    title: &'a str,
}

pub async fn start_broadcast(title: &str) -> ApiResult<StartedBroadcast> {
    send_json(Method::POST, "/api/broadcasts", &BroadcastTitle { title }).await
}

pub async fn rename_broadcast(id: &str, title: &str) -> ApiResult<Broadcast> {
    send_json(Method::PATCH, &format!("/api/broadcasts/{}", encode(id)), &BroadcastTitle { title }).await
}

pub async fn end_broadcast(id: &str) -> ApiResult<()> {
    send_empty(Method::POST, &format!("/api/broadcasts/{}/end", encode(id))).await
}

pub async fn delete_broadcast(id: &str) -> ApiResult<()> {
    send_empty(Method::DELETE, &format!("/api/broadcasts/{}", encode(id))).await
}

// 후원

#[derive(Debug, Clone, Copy, PartialEq, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DonationStatus {
    Pending,
    Done,
    Failed,
    Canceled,
    Expired,
}

/// 방송 화면에 공개되는 완료된 후원
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PublicDonation {
    pub id: String,
    pub donor: UserSummary,
    pub amount: u64,
    pub message: Option<String>,
    pub completed_at: String,
}

/// 후원한 방송이 삭제되었으면 id가 None이고 제목만 남는다
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct BroadcastRef {
    pub id: Option<String>,
    pub title: String,
}

/// 후원자 본인에게 보이는 후원
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SentDonation {
    pub id: String,
    pub recipient: UserSummary,
    pub broadcast: BroadcastRef,
    pub amount: u64,
    pub message: Option<String>,
    pub status: DonationStatus,
    /// 실패했을 때만 존재
    pub failure_message: Option<String>,
    pub created_at: String,
    pub completed_at: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceivedDonation {
    pub id: String,
    pub donor: UserSummary,
    pub broadcast: BroadcastRef,
    pub amount: u64,
    pub message: Option<String>,
    pub completed_at: String,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceivedDonations {
    pub items: Vec<ReceivedDonation>,
    pub total_count: u64,
    pub total_amount: u64,
}

/// 결제위젯으로 결제를 요청할 때 쓰는 값. orderId는 후원의 id다
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DonationCheckout {
    pub donation: SentDonation,
    pub order_id: String,
    pub order_name: String,
    pub customer_key: String,
}

// 서버(Donation)와 같은 제약
pub const DONATION_MIN_AMOUNT: u64 = 1_000;
pub const DONATION_MAX_AMOUNT: u64 = 500_000;
pub const DONATION_AMOUNT_UNIT: u64 = 1_000;
pub const DONATION_PRESETS: [u64; 4] = [1_000, 5_000, 10_000, 50_000];
pub const DONATION_MESSAGE_MAX_LENGTH: usize = 100;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PaymentConfig {
    client_key: Option<String>,
}

/// 서버에 토스페이먼츠 키가 설정되지 않았으면 None
pub async fn fetch_payment_client_key() -> ApiResult<Option<String>> {
    Ok(get_json::<PaymentConfig>("/api/payments/config").await?.client_key)
}

/// 최근 완료된 후원 100개, 오래된 순
pub async fn fetch_broadcast_donations(broadcast_id: &str) -> ApiResult<Vec<PublicDonation>> {
    get_json(&format!("/api/broadcasts/{}/donations", encode(broadcast_id))).await
}

#[derive(Serialize)]
struct DonationInput<'a> {
    amount: u64,
    message: &'a str,
}

pub async fn start_donation(broadcast_id: &str, amount: u64, message: &str) -> ApiResult<DonationCheckout> {
    send_json(Method::POST, &format!("/api/broadcasts/{}/donations", encode(broadcast_id)), &DonationInput { amount, message }).await
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ApproveInput<'a> {
    payment_key: &'a str,
    amount: u64,
}

/// 결과를 아직 모르면 503. 같은 요청을 다시 보내면 된다
pub async fn approve_donation(id: &str, payment_key: &str, amount: u64) -> ApiResult<SentDonation> {
    send_json(Method::POST, &format!("/api/donations/{}/approve", encode(id)), &ApproveInput { payment_key, amount }).await
}

#[derive(Serialize)]
struct AbandonInput<'a> {
    code: Option<&'a str>,
    message: Option<&'a str>,
}

/// 결제창을 닫았거나 결제 인증에 실패했을 때 결제 서비스가 준 code, message를 그대로 넘긴다
pub async fn abandon_donation(id: &str, code: Option<&str>, message: Option<&str>) -> ApiResult<SentDonation> {
    send_json(Method::POST, &format!("/api/donations/{}/abandon", encode(id)), &AbandonInput { code, message }).await
}

/// 앱이 브라우저에서 결제하도록 받는 결제 토큰 (ADR-0005). 토큰은 다시 받을 수 없으므로 바로 쓴다
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IssuedCheckoutToken {
    pub token: String,
    pub expires_at: String,
}

/// 후원자 본인이, 결제 대기일 때만 받는다. 다시 받으면 이전 토큰은 쓸 수 없다
pub async fn issue_checkout_token(donation_id: &str) -> ApiResult<IssuedCheckoutToken> {
    send_json(Method::POST, &format!("/api/donations/{}/checkout-token", encode(donation_id)), &serde_json::json!({})).await
}

pub async fn fetch_sent_donations() -> ApiResult<Vec<SentDonation>> {
    get_json("/api/donations/sent").await
}

pub async fn fetch_received_donations() -> ApiResult<ReceivedDonations> {
    get_json("/api/donations/received").await
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

    /// 테스트용 영상은 ffmpeg로 만든다. 파일 경로를 UPLOAD_TEST_FILE로 넘긴다.
    #[tokio::test]
    #[ignore = "localhost:8180에 서버가 떠 있어야 하고 UPLOAD_TEST_FILE이 필요하다"]
    async fn uploads_video_with_progress() {
        use std::sync::Arc;
        use std::sync::atomic::{AtomicU64, Ordering};

        let Ok(path) = std::env::var("UPLOAD_TEST_FILE") else { return };
        let path = std::path::PathBuf::from(path);
        let size = std::fs::metadata(&path).unwrap().len();
        let suffix = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() % 1_000_000_000;
        fetch_me().await.unwrap();
        sign_up(&SignUpInput { username: format!("up{suffix}"), password: format!("pw-{suffix}-check"), nickname: format!("업{}", suffix % 10_000) })
            .await
            .unwrap();

        let sent = Arc::new(AtomicU64::new(0));
        let input = UploadInput { title: "앱 업로드 연동 테스트".to_owned(), description: String::new() };
        let video = upload::upload_path(&path, "test.mp4".to_owned(), size, input, sent.clone()).await.unwrap();
        assert_eq!(video.title, "앱 업로드 연동 테스트");
        assert_eq!(sent.load(Ordering::Relaxed), size, "보낸 바이트를 모두 셌다");
    }
}
