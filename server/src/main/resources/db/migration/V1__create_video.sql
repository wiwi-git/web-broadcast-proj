create table video (
    id                 uuid primary key,
    title              varchar(100)             not null,
    description        varchar(5000),
    status             varchar(20)              not null,
    original_extension varchar(10)              not null,
    size_bytes         bigint                   not null,
    duration_seconds   double precision,
    created_at         timestamp with time zone not null
);

create index video_status_created_at_idx on video (status, created_at desc);
