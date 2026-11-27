//! 여러 화면이 함께 쓰는 컴포넌트

mod comments;
mod confirm_button;
mod hls_player;
mod icons;
mod live;
mod report;
mod shell;
mod simulcast;
#[cfg(any(target_os = "ios", target_os = "android"))]
mod webview_file;

pub use comments::VideoComments;
pub use confirm_button::ConfirmButton;
pub use hls_player::HlsPlayer;
pub use report::ReportButton;
pub use live::{BroadcastDonations, DonationAlert, LiveComments, use_broadcast_donations};
pub use shell::Shell;
pub use simulcast::{SimulcastPicker, SimulcastStatusLine};
#[cfg(any(target_os = "ios", target_os = "android"))]
pub use webview_file::{PickedFile, WebViewFilePicker, copy_to_temp};
