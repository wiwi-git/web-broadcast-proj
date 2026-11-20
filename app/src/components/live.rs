//! 라이브 화면의 댓글·후원 표시. web/src/components/LiveComments.tsx, DonationAlert.tsx와 같다.

use std::collections::{HashSet, VecDeque};
use std::time::Duration;

use dioxus::prelude::*;

use super::comments::{CommentForm, CommentItem};
use crate::api::{self, Comment, CommentTarget, PublicDonation};
use crate::time::{format_relative, format_won, sleep};

const POLL_INTERVAL: Duration = Duration::from_secs(3);
const ALERT_DURATION: Duration = Duration::from_secs(5);
const LIST_ID: &str = "live-comment-list";

/// Broadcast의 완료된 후원을 댓글과 같은 주기로 다시 불러온다.
/// 화면을 연 뒤에 새로 들어온 후원만 알림 대기열(arrived)에 넣는다. 그 전의 후원은 목록에만 있다.
pub struct BroadcastDonations {
    pub donations: Signal<Vec<PublicDonation>>,
    pub arrived: Signal<VecDeque<PublicDonation>>,
}

/// broadcast_id가 None이면(아직 방송 전) 아무것도 불러오지 않는다
pub fn use_broadcast_donations(broadcast_id: Option<String>, active: bool) -> BroadcastDonations {
    let mut donations = use_signal(Vec::<PublicDonation>::new);
    let mut arrived = use_signal(VecDeque::<PublicDonation>::new);
    // 처음 불러오기 전에는 None. 처음 받은 후원은 알림 없이 본 것으로 둔다
    let mut seen = use_signal(|| None::<HashSet<String>>);

    use_resource(use_reactive!(|(broadcast_id, active)| async move {
        let Some(broadcast_id) = broadcast_id else { return };
        loop {
            // 후원 목록은 댓글보다 덜 중요하므로 실패해도 따로 알리지 않고 다음 주기에 다시 시도한다
            if let Ok(list) = api::fetch_broadcast_donations(&broadcast_id).await {
                let mut known = seen().unwrap_or_default();
                if seen.read().is_some() {
                    let fresh: Vec<_> = list.iter().filter(|d| !known.contains(&d.id)).cloned().collect();
                    arrived.write().extend(fresh);
                }
                known.extend(list.iter().map(|d| d.id.clone()));
                seen.set(Some(known));
                donations.set(list);
            }
            if !active {
                return;
            }
            sleep(POLL_INTERVAL).await;
        }
    }));

    BroadcastDonations { donations, arrived }
}

enum Entry {
    Comment(Comment),
    Donation(PublicDonation),
}

impl Entry {
    fn at(&self) -> &str {
        match self {
            Entry::Comment(comment) => &comment.created_at,
            Entry::Donation(donation) => &donation.completed_at,
        }
    }
}

/// 댓글과 후원을 시간순으로 섞는다. 둘 다 이미 오래된 순이다
fn merge(comments: Vec<Comment>, donations: Vec<PublicDonation>) -> Vec<Entry> {
    let mut entries: Vec<Entry> =
        comments.into_iter().map(Entry::Comment).chain(donations.into_iter().map(Entry::Donation)).collect();
    // ISO-8601 UTC 문자열은 길이가 달라도(소수점 자릿수) 시각으로 비교해야 하므로 파싱해서 정렬한다
    entries.sort_by_key(|entry| chrono::DateTime::parse_from_rfc3339(entry.at()).ok());
    entries
}

/// 채팅처럼 최근 100개를 오래된 순으로 보여 주고 새 댓글을 아래에 쌓는다.
/// 실시간 채팅이 아니라 3초마다 다시 불러오는 Comment다. 다른 방송으로 바뀌면 부모가 key로 새로 만든다.
#[component]
pub fn LiveComments(broadcast_id: String, active: bool, donations: Vec<PublicDonation>) -> Element {
    let mut items = use_signal(|| None::<Vec<Comment>>);
    let mut error = use_signal(|| None::<String>);
    // 사용자가 위로 올려 읽는 중이면 아래로 끌어내리지 않는다
    let mut stick_to_bottom = use_signal(|| true);
    let target = CommentTarget::Broadcast(broadcast_id.clone());

    use_resource(use_reactive!(|(broadcast_id, active)| async move {
        loop {
            match api::fetch_broadcast_comments(&broadcast_id).await {
                Ok(list) => {
                    items.set(Some(list));
                    error.set(None);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            if !active {
                return;
            }
            sleep(POLL_INTERVAL).await;
        }
    }));

    // 그린 뒤에 목록 맨 아래로 내린다
    let donation_count = donations.len();
    use_effect(use_reactive!(|donation_count| {
        let _ = (items.read().as_ref().map(Vec::len), donation_count);
        if *stick_to_bottom.peek() {
            document::eval(&format!(
                "const list = document.getElementById('{LIST_ID}'); if (list) list.scrollTop = list.scrollHeight;"
            ));
        }
    }));

    let entries = items().map(|list| merge(list, donations));

    rsx! {
        aside { class: "live-comments",
            h2 { class: "comments-title", "댓글" }
            ul {
                id: LIST_ID,
                class: "comment-list live",
                onscroll: move |e| {
                    let gap = e.scroll_height() as f64 - e.scroll_top() - e.client_height() as f64;
                    stick_to_bottom.set(gap < 40.0);
                },
                if entries.as_ref().is_some_and(Vec::is_empty) {
                    li { class: "meta comments-empty", "아직 댓글이 없습니다." }
                }
                for entry in entries.into_iter().flatten() {
                    match entry {
                        Entry::Donation(donation) => rsx! {
                            DonationItem { key: "{donation.id}", donation }
                        },
                        Entry::Comment(comment) => rsx! {
                            CommentItem {
                                key: "{comment.id}",
                                comment,
                                on_edited: move |edited: Comment| {
                                    if let Some(list) = items.write().as_mut() {
                                        if let Some(slot) = list.iter_mut().find(|c| c.id == edited.id) {
                                            *slot = edited;
                                        }
                                    }
                                },
                                on_deleted: move |id: String| {
                                    if let Some(list) = items.write().as_mut() {
                                        list.retain(|c| c.id != id);
                                    }
                                },
                            }
                        },
                    }
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            CommentForm {
                compact: true,
                target,
                on_written: move |comment: Comment| {
                    stick_to_bottom.set(true);
                    items.write().get_or_insert_with(Vec::new).push(comment);
                },
            }
        }
    }
}

#[component]
fn DonationItem(donation: PublicDonation) -> Element {
    let amount = format_won(donation.amount);
    let when = format_relative(&donation.completed_at);
    rsx! {
        li { class: "comment donation-item",
            div { class: "comment-head",
                span { class: "comment-author", "{donation.donor.nickname}" }
                span { class: "donation-amount", "{amount} 후원" }
                span { class: "meta", "{when}" }
            }
            if let Some(message) = donation.message.as_ref().filter(|m| !m.is_empty()) {
                p { class: "comment-body", "{message}" }
            }
        }
    }
}

/// 플레이어나 카메라 미리보기 위에 5초 동안 뜨는 후원 알림. 부모가 key로 후원마다 새로 그린다
#[component]
pub fn DonationAlert(donation: PublicDonation, on_done: EventHandler<()>) -> Element {
    use_future(move || async move {
        sleep(ALERT_DURATION).await;
        on_done.call(());
    });

    let amount = format_won(donation.amount);
    rsx! {
        div { class: "donation-alert", role: "status",
            p { class: "donation-alert-title",
                strong { "{donation.donor.nickname}" }
                "님이 "
                strong { class: "donation-amount", "{amount}" }
                " 후원했습니다"
            }
            if let Some(message) = donation.message.as_ref().filter(|m| !m.is_empty()) {
                p { class: "donation-alert-message", "{message}" }
            }
        }
    }
}
