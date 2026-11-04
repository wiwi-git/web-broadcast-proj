-- 회원 도입 전의 Content는 모두 테스트용이고 소유자가 없으므로 지운다
delete from video;
delete from broadcast;

create table app_user (
    id            uuid primary key,
    username      varchar(20)              not null unique,
    password_hash varchar(100)             not null,
    nickname      varchar(20)              not null unique,
    created_at    timestamp with time zone not null
);

alter table video add column owner_id uuid not null references app_user (id);
alter table broadcast add column owner_id uuid not null references app_user (id);
-- 동기화 작업과 Owner의 종료·삭제가 겹쳐도 서로 덮어쓰지 않도록 낙관적 잠금을 쓴다
alter table broadcast add column version bigint not null default 0;

-- 한 회원이 동시에 진행할 수 있는 Broadcast는 하나
create unique index broadcast_one_active_per_owner on broadcast (owner_id) where status in ('PENDING', 'LIVE');

-- Comment는 Video나 Broadcast 중 정확히 하나에 달린다. Content가 지워지면 함께 지워진다
create table comment (
    id           uuid primary key,
    video_id     uuid references video (id) on delete cascade,
    broadcast_id uuid references broadcast (id) on delete cascade,
    author_id    uuid                     not null references app_user (id),
    body         varchar(500)             not null,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone,
    constraint comment_one_target check ((video_id is null) <> (broadcast_id is null))
);

create index comment_video_idx on comment (video_id, created_at desc, id desc);
create index comment_broadcast_idx on comment (broadcast_id, created_at desc, id desc);

-- Spring Session JDBC (spring-session-jdbc의 schema-postgresql.sql)
create table spring_session (
    primary_id            char(36)     not null,
    session_id            char(36)     not null,
    creation_time         bigint       not null,
    last_access_time      bigint       not null,
    max_inactive_interval int          not null,
    expiry_time           bigint       not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session (primary_id) on delete cascade
);
