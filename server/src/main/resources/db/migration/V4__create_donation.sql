-- Donation의 id를 결제 서비스의 orderId로 그대로 쓴다
create table donation (
    id              uuid primary key,
    donor_id        uuid                     not null references app_user (id),
    recipient_id    uuid                     not null references app_user (id),
    -- Broadcast가 지워져도 돈이 오간 기록은 남긴다. 제목은 그때를 위해 따로 저장한다
    broadcast_id    uuid                     references broadcast (id) on delete set null,
    broadcast_title varchar(100)             not null,
    amount          integer                  not null,
    message         varchar(100),
    status          varchar(20)              not null,
    -- 승인을 요청한 적이 있으면 채워진다. 결과를 모를 때 이 값으로 다시 확인한다
    payment_key     varchar(200) unique,
    failure_code    varchar(100),
    failure_message varchar(500),
    created_at      timestamp with time zone not null,
    completed_at    timestamp with time zone,
    -- 승인 요청과 만료 작업이 겹쳐도 서로 덮어쓰지 않게 한다
    version         bigint                   not null default 0,
    constraint donation_amount_range check (amount between 1000 and 500000 and amount % 1000 = 0),
    constraint donation_not_to_self check (donor_id <> recipient_id)
);

create index donation_broadcast_done_idx on donation (broadcast_id, completed_at desc) where status = 'DONE';
create index donation_recipient_done_idx on donation (recipient_id, completed_at desc) where status = 'DONE';
create index donation_donor_idx on donation (donor_id, created_at desc);
create index donation_pending_idx on donation (created_at) where status = 'PENDING';
