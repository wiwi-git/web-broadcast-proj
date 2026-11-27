-- 운영자 조치: 역할, 계정 정지, 콘텐츠 숨김, 신고 처리 상태

-- 운영자는 SQL로 지정한다: update app_user set role = 'ADMIN' where username = '...';
alter table app_user add column role varchar(20) not null default 'USER';
-- 정지된 User는 로그인할 수 없다. null이면 정지되지 않은 것
alter table app_user add column suspended_at timestamp with time zone;

-- 운영자가 내린 Content·Comment는 지우지 않고 숨긴다. 누구에게도 보이지 않지만 기록은 남는다
alter table broadcast add column hidden_at timestamp with time zone;
alter table video add column hidden_at timestamp with time zone;
alter table comment add column hidden_at timestamp with time zone;

-- OPEN(처리 전) / ACTIONED(대상을 내림) / DISMISSED(문제없음)
alter table report add column status varchar(20) not null default 'OPEN';
alter table report add column resolved_at timestamp with time zone;
alter table report add column resolved_by uuid references app_user (id);

create index report_open_idx on report (status, created_at);
