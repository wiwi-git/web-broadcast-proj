-- 변환 워커 여러 대가 같은 Video를 동시에 변환하지 않도록, 처리 중인 Video를 한 워커가 점유한다 (ADR-0004).
-- 점유는 claim_expires_at까지만 유효하다. 워커가 죽어 연장하지 못하면 다른 워커가 이어받는다.
alter table video add column claim_token uuid;
alter table video add column claim_expires_at timestamp with time zone;

create index video_processing_queue_idx on video (created_at) where status = 'PROCESSING';
