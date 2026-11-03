create table broadcast (
    id              uuid primary key,
    title           varchar(100)             not null,
    status          varchar(20)              not null,
    stream_key_hash varchar(64)              not null,
    created_at      timestamp with time zone not null,
    started_at      timestamp with time zone,
    ended_at        timestamp with time zone
);

create index broadcast_status_idx on broadcast (status);
