-- 앱(데스크톱·모바일)이 브라우저에서 결제할 때 쓰는 한 번용 결제 토큰 (ADR-0005).
-- 원본은 저장하지 않고 해시만 둔다. 다시 발급하면 덮어써서 이전 토큰은 쓸 수 없게 된다
alter table donation add column checkout_token_hash varchar(64) unique;
alter table donation add column checkout_token_expires_at timestamp with time zone;
