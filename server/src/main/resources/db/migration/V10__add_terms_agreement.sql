-- 가입할 때 이용약관·개인정보처리방침에 동의한 시각과 그 문서의 버전(시행일).
-- 이 기능 전에 가입한 User는 동의 기록이 없다(null)
alter table app_user add column terms_agreed_at timestamp with time zone;
alter table app_user add column terms_version varchar(20);
