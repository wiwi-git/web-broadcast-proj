//! 댓글 입력과 댓글 한 개, 동영상 댓글 목록. web/src/components/VideoComments.tsx, CommentItem.tsx, CommentForm.tsx와 같다.

use std::collections::HashSet;
use std::time::Duration;

use dioxus::prelude::*;

use super::{ConfirmButton, ReportButton};
use crate::api::{self, COMMENT_MAX_LENGTH, Comment, CommentTarget, ReportTargetType};
use crate::auth::use_auth;
use crate::route::Route;
use crate::time::{format_relative, sleep};

/// 운영자가 내린 댓글을 이 간격으로 확인해 화면에서 뺀다. 라이브 댓글 갱신과 같다
const HIDDEN_POLL_INTERVAL: Duration = Duration::from_secs(3);

/// 최신순으로 20개씩 "더 보기". 다른 동영상으로 바뀌면 부모가 key로 새로 만든다.
/// 댓글은 처음에 한 번만 불러오므로, 운영자가 내린 댓글은 주기적으로 ID만 받아 뺀다.
#[component]
pub fn VideoComments(video_id: String) -> Element {
    let mut items = use_signal(|| None::<Vec<Comment>>);
    let mut next_cursor = use_signal(|| None::<String>);
    let mut total = use_signal(|| 0u64);
    let mut loading_more = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    let first_id = video_id.clone();
    use_future(move || {
        let video_id = first_id.clone();
        async move {
            match api::fetch_video_comments(&video_id, None).await {
                Ok(page) => {
                    items.set(Some(page.items));
                    next_cursor.set(page.next_cursor);
                    total.set(page.total_count);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
        }
    });

    let poll_id = video_id.clone();
    use_future(move || {
        let video_id = poll_id.clone();
        async move {
            loop {
                sleep(HIDDEN_POLL_INTERVAL).await;
                // 실패하면 다음 주기에 다시 확인한다
                let Ok(hidden) = api::fetch_hidden_video_comments(&video_id).await else { continue };
                let hidden: HashSet<String> = hidden.into_iter().collect();
                let Some(current) = items() else { continue };
                let before = current.len();
                let kept: Vec<Comment> = current.into_iter().filter(|c| !hidden.contains(&c.id)).collect();
                let removed = before - kept.len();
                if removed > 0 {
                    items.set(Some(kept));
                    total.set(total().saturating_sub(removed as u64));
                }
            }
        }
    });

    let more_id = video_id.clone();
    let load_more = move |_| {
        let video_id = more_id.clone();
        async move {
            let Some(cursor) = next_cursor() else { return };
            loading_more.set(true);
            match api::fetch_video_comments(&video_id, Some(&cursor)).await {
                Ok(page) => {
                    // 그 사이 새로 쓴 댓글이 있어도 겹치지 않게 ID로 거른다
                    let mut current = items().unwrap_or_default();
                    let seen: HashSet<String> = current.iter().map(|c| c.id.clone()).collect();
                    current.extend(page.items.into_iter().filter(|c| !seen.contains(&c.id)));
                    items.set(Some(current));
                    next_cursor.set(page.next_cursor);
                    total.set(page.total_count);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            loading_more.set(false);
        }
    };

    let total_label = if items.read().is_some() { total().to_string() } else { String::new() };

    rsx! {
        section { class: "comments",
            h2 { class: "comments-title", "댓글 {total_label}" }
            CommentForm {
                target: CommentTarget::Video(video_id.clone()),
                on_written: move |comment: Comment| {
                    let mut current = items().unwrap_or_default();
                    current.insert(0, comment);
                    items.set(Some(current));
                    total += 1;
                },
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
            match items() {
                Some(list) if list.is_empty() => rsx! { p { class: "meta comments-empty", "첫 댓글을 남겨 보세요." } },
                Some(list) => rsx! {
                    ul { class: "comment-list",
                        for comment in list {
                            CommentItem {
                                key: "{comment.id}",
                                comment: comment.clone(),
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
                                    total -= 1;
                                },
                            }
                        }
                    }
                },
                None => rsx! {},
            }
            if next_cursor.read().is_some() {
                button {
                    r#type: "button",
                    class: "button more-button",
                    disabled: loading_more(),
                    onclick: load_more,
                    if loading_more() { "불러오는 중…" } else { "댓글 더 보기" }
                }
            }
        }
    }
}

/// 로그인한 User만 쓸 수 있다. 로그인하지 않았으면 로그인 안내를 보여 준다.
/// compact면 라이브 화면처럼 한 줄 입력칸으로 보여 준다.
#[component]
pub fn CommentForm(target: CommentTarget, on_written: EventHandler<Comment>, #[props(default)] compact: bool) -> Element {
    let auth = use_auth();
    let route = use_route::<Route>();
    let mut body = use_signal(String::new);
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);

    if auth.me().is_none() {
        return rsx! {
            p { class: "comment-login",
                Link { to: route.login_then(), "로그인" }
                "하고 댓글을 남겨 보세요."
            }
        };
    }

    let submit = move |event: FormEvent| {
        let target = target.clone();
        async move {
            event.prevent_default();
            let text = body().trim().to_owned();
            if text.is_empty() {
                return;
            }
            busy.set(true);
            error.set(None);
            match api::write_comment(&target, &text).await {
                Ok(comment) => {
                    on_written.call(comment);
                    body.set(String::new());
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            busy.set(false);
        }
    };

    let length = body.read().chars().count();
    rsx! {
        form { class: if compact { "comment-form compact" } else { "comment-form" }, onsubmit: submit,
            if compact {
                input {
                    r#type: "text",
                    class: "text-input",
                    placeholder: "댓글 입력",
                    maxlength: COMMENT_MAX_LENGTH as i64,
                    value: "{body}",
                    oninput: move |e| body.set(e.value()),
                }
            } else {
                textarea {
                    class: "text-input",
                    rows: 3,
                    placeholder: "댓글을 남겨 보세요",
                    maxlength: COMMENT_MAX_LENGTH as i64,
                    value: "{body}",
                    oninput: move |e| body.set(e.value()),
                }
            }
            div { class: "comment-form-actions",
                if !compact {
                    span { class: "hint", "{length} / {COMMENT_MAX_LENGTH}" }
                }
                button {
                    r#type: "submit",
                    class: "button primary small",
                    disabled: busy() || body().trim().is_empty(),
                    "등록"
                }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
        }
    }
}

/// Author에게만 수정·삭제 버튼이 보인다. 서버도 Author인지 다시 확인한다.
#[component]
pub fn CommentItem(comment: Comment, on_edited: EventHandler<Comment>, on_deleted: EventHandler<String>) -> Element {
    let auth = use_auth();
    let mut editing = use_signal(|| false);
    let mut draft = use_signal(String::new);
    let mut busy = use_signal(|| false);
    let mut error = use_signal(|| None::<String>);
    let mine = auth.me().is_some_and(|me| me.id == comment.author.id);

    let save_id = comment.id.clone();
    let save = move |event: FormEvent| {
        let id = save_id.clone();
        async move {
            event.prevent_default();
            busy.set(true);
            error.set(None);
            match api::edit_comment(&id, draft().trim()).await {
                Ok(edited) => {
                    on_edited.call(edited);
                    editing.set(false);
                }
                Err(e) => error.set(Some(e.to_string())),
            }
            busy.set(false);
        }
    };

    let delete_id = comment.id.clone();
    let delete = move |_| {
        let id = delete_id.clone();
        spawn(async move {
            busy.set(true);
            error.set(None);
            match api::delete_comment(&id).await {
                Ok(()) => on_deleted.call(id),
                Err(e) => {
                    error.set(Some(e.to_string()));
                    busy.set(false);
                }
            }
        });
    };

    let original = comment.body.clone();
    let when = format_relative(&comment.created_at);
    let edited_mark = if comment.updated_at.is_some() { " (수정됨)" } else { "" };

    rsx! {
        li { class: "comment",
            div { class: "comment-head",
                span { class: "comment-author", "{comment.author.nickname}" }
                span { class: "meta", "{when}{edited_mark}" }
                if mine && !editing() {
                    span { class: "comment-actions",
                        button {
                            r#type: "button",
                            class: "link-button",
                            disabled: busy(),
                            onclick: move |_| {
                                draft.set(original.clone());
                                editing.set(true);
                            },
                            "수정"
                        }
                        ConfirmButton {
                            class: "link-button",
                            disabled: busy(),
                            message: "댓글을 삭제할까요?",
                            confirm_label: "삭제",
                            on_confirm: delete,
                            "삭제"
                        }
                    }
                }
                if !mine {
                    span { class: "comment-actions",
                        ReportButton {
                            target_type: ReportTargetType::Comment,
                            target_id: comment.id.clone(),
                            owner_id: comment.author.id.clone(),
                        }
                    }
                }
            }
            if editing() {
                form { class: "comment-edit", onsubmit: save,
                    textarea {
                        class: "text-input",
                        rows: 2,
                        maxlength: COMMENT_MAX_LENGTH as i64,
                        value: "{draft}",
                        // autofocus 속성은 나중에 그려진 요소에는 적용되지 않으므로 붙는 순간 직접 맞춘다
                        onmounted: move |e| async move {
                            let _ = e.set_focus(true).await;
                        },
                        oninput: move |e| draft.set(e.value()),
                    }
                    div { class: "comment-edit-actions",
                        button { r#type: "button", class: "button small", onclick: move |_| editing.set(false), "취소" }
                        button {
                            r#type: "submit",
                            class: "button primary small",
                            disabled: busy() || draft().trim().is_empty(),
                            "저장"
                        }
                    }
                }
            } else {
                p { class: "comment-body", "{comment.body}" }
            }
            if let Some(message) = error() {
                p { class: "hint error", "{message}" }
            }
        }
    }
}
