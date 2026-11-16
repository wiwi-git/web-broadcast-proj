use dioxus::prelude::*;

use super::Todo;
use crate::auth::RequireLogin;

#[component]
pub fn Donate(id: String) -> Element {
    rsx! {
        RequireLogin {
            Todo {
                title: "후원하기",
                note: "토스페이먼츠 결제창 자리. 앱에서는 결제 후 돌아오는 주소(success/fail)를 따로 정해야 한다. 방송 id: {id}",
            }
        }
    }
}

#[component]
pub fn Donations() -> Element {
    rsx! {
        RequireLogin {
            Todo { title: "후원 내역", note: "보낸 후원과 받은 후원 목록 자리." }
        }
    }
}
