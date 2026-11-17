//! 화면. 컴포넌트 이름은 [`crate::route::Route`]의 항목 이름과 같아야 한다.

mod account;
mod donations;
mod live;
mod not_found;
mod videos;

pub use account::{Login, SignUp};
pub use donations::{Donate, DonationFail, DonationSuccess, Donations};
pub use live::{Broadcast, LiveList, LiveWatch};
pub use not_found::NotFound;
pub use videos::{Upload, VideoDetail, VideoList};
