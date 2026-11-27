-- 로그인 아이디를 이메일로 바꾼다.
-- 지금까지의 회원은 이메일이 없고 데모·테스트 계정뿐이라 모두 지운다 (사용자 결정, 2026-11-27).
-- DB 기록만 지우므로, 배포할 때 미디어 저장소(MEDIA_STORAGE_ROOT의 originals, public, work)도 비운다.
delete from spring_session;
delete from comment;
delete from report;
delete from donation;
delete from broadcast;
delete from video;
delete from simulcast_setting;
delete from app_user;

-- 이메일은 소문자로 저장한다. 최대 길이는 RFC 5321의 254자
alter table app_user rename column username to email;
alter table app_user alter column email type varchar(254);
alter table app_user rename constraint app_user_username_key to app_user_email_key;
