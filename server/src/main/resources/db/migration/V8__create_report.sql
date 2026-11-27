-- User가 남의 Broadcast·Video·Comment를 신고한 기록.
-- 대상이 지워져도 신고는 운영자가 볼 수 있게 남기므로 대상에 외래 키를 걸지 않는다
create table report (
    id          uuid primary key,
    reporter_id uuid                     not null references app_user (id),
    target_type varchar(20)              not null,
    target_id   uuid                     not null,
    reason      varchar(20)              not null,
    detail      varchar(200),
    created_at  timestamp with time zone not null,
    -- 한 User는 같은 대상을 한 번만 신고한다
    constraint report_once_per_reporter unique (reporter_id, target_type, target_id)
);

create index report_created_idx on report (created_at desc);
