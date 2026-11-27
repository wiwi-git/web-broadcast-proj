-- 이메일 인증 시각. 인증한 회원만 동영상을 올리고 댓글을 쓸 수 있다
alter table app_user add column email_verified_at timestamp with time zone;

-- 메일로 보내는 일회용 링크(이메일 인증, 비밀번호 재설정). 링크 속 값은 해시만 저장한다
create table email_token (
    id         uuid primary key,
    user_id    uuid                     not null references app_user (id) on delete cascade,
    purpose    varchar(20)              not null,
    token_hash varchar(64)              not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    -- 썼거나 새 링크를 보내 무효가 되면 채워진다
    used_at    timestamp with time zone
);

create index email_token_user_idx on email_token (user_id, purpose, created_at desc);
