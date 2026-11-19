-- 유튜브 동시 송출 (ADR-0007).
-- YouTube Key는 암호화해 두고, 화면에 보여 줄 끝 네 자리만 따로 둔다. on_by_default는 지난번 방송에서 고른 값이다
create table simulcast_setting (
    user_id               uuid primary key references app_user (id) on delete cascade,
    youtube_key_encrypted varchar(512) not null,
    youtube_key_last4     varchar(4)   not null,
    on_by_default         boolean      not null
);

-- Broadcast마다 동시 송출을 켰는지와 중계가 마지막으로 알린 상태
alter table broadcast add column simulcast boolean not null default false;
alter table broadcast add column simulcast_status varchar(20);
