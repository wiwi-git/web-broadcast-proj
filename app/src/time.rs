//! 시간 표시와 기다리기. 표시 형식은 web/src/format.ts와 같다.

use std::time::Duration;

use chrono::{DateTime, Local, Timelike, Utc};

/// 125 → "2:05", 3725 → "1:02:05"
pub fn format_duration(seconds: f64) -> String {
    let total = seconds.max(0.0) as u64;
    let (h, m, s) = (total / 3600, total % 3600 / 60, total % 60);
    if h > 0 { format!("{h}:{m:02}:{s:02}") } else { format!("{m}:{s:02}") }
}

/// "2026. 11. 10. 오후 6:05" (ko-KR medium/short와 같은 모양)
pub fn format_date(iso: &str) -> String {
    let Ok(time) = DateTime::parse_from_rfc3339(iso) else { return iso.to_owned() };
    let local = time.with_timezone(&Local);
    let (pm, hour) = local.hour12();
    let noon = if pm { "오후" } else { "오전" };
    format!("{} {noon} {hour}:{:02}", local.format("%Y. %-m. %-d."), local.minute())
}

/// "방금 전", "5분 전", "3시간 전", 하루가 넘으면 날짜
pub fn format_relative(iso: &str) -> String {
    let Ok(time) = DateTime::parse_from_rfc3339(iso) else { return iso.to_owned() };
    let minutes = (Utc::now() - time.with_timezone(&Utc)).num_minutes();
    match minutes {
        ..1 => "방금 전".to_owned(),
        1..60 => format!("{minutes}분 전"),
        60..1440 => format!("{}시간 전", minutes / 60),
        _ => format_date(iso),
    }
}

/// since부터 now까지의 경과 시간("12:34")
pub fn format_elapsed(since: &str, now: DateTime<Utc>) -> String {
    let Ok(start) = DateTime::parse_from_rfc3339(since) else { return String::new() };
    let seconds = (now - start.with_timezone(&Utc)).num_milliseconds() as f64 / 1000.0;
    format_duration(seconds)
}

/// 파일 크기. "1.25 GB", "12.3 MB", "40 KB" (web의 formatBytes와 같다)
pub fn format_bytes(bytes: u64) -> String {
    const MB: f64 = 1024.0 * 1024.0;
    const GB: f64 = MB * 1024.0;
    let value = bytes as f64;
    if value >= GB {
        format!("{:.2} GB", value / GB)
    } else if value >= MB {
        format!("{:.1} MB", value / MB)
    } else {
        format!("{} KB", (value / 1024.0).ceil())
    }
}

/// 12000 → "12,000"
pub fn format_number(value: u64) -> String {
    let digits = value.to_string();
    let mut out = String::new();
    for (i, c) in digits.chars().enumerate() {
        if i > 0 && (digits.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    out
}

/// 12000 → "12,000원"
pub fn format_won(amount: u64) -> String {
    format!("{}원", format_number(amount))
}

/// 웹(wasm)과 앱(tokio)에서 같은 방식으로 기다린다
pub async fn sleep(duration: Duration) {
    #[cfg(target_arch = "wasm32")]
    gloo_timers::future::sleep(duration).await;
    #[cfg(not(target_arch = "wasm32"))]
    tokio::time::sleep(duration).await;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_duration() {
        assert_eq!(format_duration(125.0), "2:05");
        assert_eq!(format_duration(3725.4), "1:02:05");
        assert_eq!(format_duration(156.27), "2:36");
    }

    #[test]
    fn formats_won() {
        assert_eq!(format_won(500), "500원");
        assert_eq!(format_won(12_000), "12,000원");
        assert_eq!(format_won(1_234_567), "1,234,567원");
    }

    #[test]
    fn formats_date_with_korean_noon_marker() {
        let text = format_date("2026-11-10T09:05:36.032915Z");
        assert!(text.starts_with("2026. 11. 10.") || text.starts_with("2026. 11. 9."), "{text}");
        assert!(text.contains("오전") || text.contains("오후"), "{text}");
    }
}
