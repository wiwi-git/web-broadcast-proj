//! Web Broadcast의 웹·데스크톱(Windows, macOS)·모바일(Android, iOS) 클라이언트.
//! 같은 코드를 `dx serve --platform <web|desktop|android|ios>`로 빌드한다.

mod api;
mod auth;
mod components;
mod config;
mod pages;
mod route;
mod time;

use dioxus::prelude::*;

use route::Route;

const MAIN_CSS: Asset = asset!("/assets/main.css");

fn main() {
    dioxus::launch(App);
}

#[component]
fn App() -> Element {
    auth::use_auth_provider();
    rsx! {
        document::Meta { name: "viewport", content: "width=device-width, initial-scale=1, viewport-fit=cover" }
        document::Stylesheet { href: MAIN_CSS }
        Router::<Route> {}
    }
}
