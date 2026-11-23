//! 딥링크로 앱이 열렸을 때 그 주소의 화면으로 보낸다 (데스크톱·모바일).
//!
//! 후원 결제는 기본 브라우저의 웹 화면에서 하고, 결제가 끝나면 웹이 `webbroadcast://live/{방송 id}`로 후원한 방송 화면을 다시 연다.
//! Dioxus 앱은 http(s) 이동을 모두 기본 브라우저로 넘기기 때문에 결제창(iframe)을 앱 안에 띄울 수 없어서다.
//!
//! macOS·iOS는 열린 주소를 창 이벤트(tao의 `Event::Opened`)로 알려 준다. 이벤트 처리기는 화면 밖에서 돌기 때문에
//! 받은 주소를 여기 쌓아 두고, 화면 쪽(`use_app_links`)이 짧은 주기로 꺼내 이동한다.

use std::sync::Mutex;
use std::time::Duration;

use dioxus::prelude::*;

use crate::route::app_link_route;
use crate::time::sleep;

#[cfg(feature = "desktop")]
use dioxus::desktop as platform;
#[cfg(all(feature = "mobile", not(feature = "desktop")))]
use dioxus::mobile as platform;

static OPENED: Mutex<Vec<String>> = Mutex::new(Vec::new());

/// 데스크톱·모바일 창 설정. 창 제목과 딥링크 수신을 붙인다.
pub fn config() -> platform::Config {
    use platform::tao::event::Event;
    use platform::{Config, WindowBuilder};

    let config = Config::new().with_window(WindowBuilder::new().with_title("Web Broadcast")).with_custom_event_handler(
        |event, _| {
            if let Event::Opened { urls } = event {
                if let Ok(mut opened) = OPENED.lock() {
                    opened.extend(urls.iter().map(|url| url.to_string()));
                }
            }
        },
    );
    windows(config)
}

/// Windows(WebView2) 전용 설정.
///
/// - Dioxus 기본 메뉴(Window·Edit)는 Windows에서 창 안에 메뉴 막대로 보여 웹 화면과 어울리지 않아 뺀다.
///   macOS는 이 메뉴가 Cmd+C·V 같은 편집 단축키를 맡으므로 그대로 둔다.
/// - Dioxus 0.7은 Windows에서 화면을 `http://dioxus.index.html`에서 띄운다. http 주소는 보안 컨텍스트가 아니라서
///   WebView2가 `navigator.mediaDevices`를 숨기고, 방송 화면이 카메라·화면 공유를 쓸 수 없다.
///   이 주소 하나만 보안 출처로 다루게 한다. 브라우저 인자를 넘기면 wry 기본 인자가 빠지므로 함께 넣는다.
#[cfg(all(feature = "desktop", target_os = "windows"))]
fn windows(config: platform::Config) -> platform::Config {
    const WRY_DEFAULT_ARGS: &str = "--disable-features=msWebOOUI,msPdfOOUI,msSmartScreenProtection \
         --autoplay-policy=no-user-gesture-required";
    const SECURE_APP_ORIGIN: &str = "--unsafely-treat-insecure-origin-as-secure=http://dioxus.index.html";
    config.with_menu(None).with_windows_browser_args(format!("{WRY_DEFAULT_ARGS} {SECURE_APP_ORIGIN}"))
}

#[cfg(not(all(feature = "desktop", target_os = "windows")))]
fn windows(config: platform::Config) -> platform::Config {
    config
}

/// 쌓인 딥링크가 있으면 그 화면으로 이동한다. 라우터 안(Shell)에서 부른다.
pub fn use_app_links() {
    let navigator = use_navigator();
    use_future(move || async move {
        loop {
            let links: Vec<String> = OPENED.lock().map(|mut opened| opened.drain(..).collect()).unwrap_or_default();
            if let Some(link) = links.last() {
                navigator.push(app_link_route(link));
            }
            sleep(Duration::from_millis(300)).await;
        }
    });
}

