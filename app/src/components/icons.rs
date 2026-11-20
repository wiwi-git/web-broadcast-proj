//! 머리글 메뉴용 16px 선 아이콘. 글자색(currentColor)을 따른다. web/src/components/Icons.tsx와 같다.
//! 좁은 화면에서는 CSS가 글자(.nav-label)를 숨기고 아이콘만 남긴다.

use dioxus::prelude::*;

#[component]
fn Icon(children: Element) -> Element {
    rsx! {
        svg {
            class: "icon",
            width: "16",
            height: "16",
            view_box: "0 0 24 24",
            fill: "none",
            stroke: "currentColor",
            stroke_width: "2",
            stroke_linecap: "round",
            stroke_linejoin: "round",
            "aria-hidden": "true",
            {children}
        }
    }
}

/// 라이브: 가운데 점과 양쪽 전파
#[component]
pub fn LiveIcon() -> Element {
    rsx! {
        Icon {
            circle { cx: "12", cy: "12", r: "2" }
            path { d: "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.9 4.9a10 10 0 0 0 0 14.2M19.1 4.9a10 10 0 0 1 0 14.2" }
        }
    }
}

/// 동영상: 화면 안의 재생 삼각형
#[component]
pub fn VideoIcon() -> Element {
    rsx! {
        Icon {
            rect { x: "3", y: "5", width: "18", height: "14", rx: "2" }
            path { d: "M10 9.5v5l4.5-2.5z" }
        }
    }
}

/// 방송하기: 카메라
#[component]
pub fn CameraIcon() -> Element {
    rsx! {
        Icon {
            rect { x: "2", y: "7", width: "14", height: "10", rx: "2" }
            path { d: "M16 11l6-3.5v9L16 13" }
        }
    }
}

/// 업로드: 위쪽 화살표와 받침
#[component]
pub fn UploadIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" }
        }
    }
}

/// 후원 내역: 하트
#[component]
pub fn HeartIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" }
        }
    }
}

/// 로그인: 문으로 들어가는 화살표
#[component]
pub fn LoginIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4M3 12h11M10 8l4 4-4 4" }
        }
    }
}

/// 로그아웃: 문에서 나가는 화살표
#[component]
pub fn LogoutIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4M10 12h11M17 8l4 4-4 4" }
        }
    }
}

/// 앱 다운로드: 아래쪽 화살표와 받침
#[component]
pub fn DownloadIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M12 4v11M7 10l5 5 5-5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" }
        }
    }
}

/// 내 계정: 사람
#[component]
pub fn UserIcon() -> Element {
    rsx! {
        Icon {
            circle { cx: "12", cy: "8", r: "4" }
            path { d: "M4 20a8 8 0 0 1 16 0" }
        }
    }
}

/// 닫기: X
#[component]
pub fn CloseIcon() -> Element {
    rsx! {
        Icon {
            path { d: "M6 6l12 12M18 6L6 18" }
        }
    }
}
