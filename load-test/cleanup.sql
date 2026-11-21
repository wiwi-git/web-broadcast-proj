-- load.js가 만든 테스트 회원과 그 방송·댓글·세션을 지운다.
-- load.js의 회원은 아이디가 load + 영문·숫자 6자, 닉네임이 부하 + 같은 6자다.
--   OCI: kubectl -n webbroadcast exec -i deploy/postgres -- psql -U webbroadcast -d webbroadcast < load-test/cleanup.sql
begin;

create temp table load_user on commit drop as
select id, username from app_user
where username ~ '^load[0-9a-z]{6}$' and nickname = '부하' || substr(username, 5);

select username from load_user;

delete from comment where author_id in (select id from load_user);
-- 방송을 지우면 그 방송의 댓글도 함께 지워진다 (on delete cascade)
delete from broadcast where owner_id in (select id from load_user);
delete from spring_session where principal_name in (select username from load_user);
delete from app_user where id in (select id from load_user);

commit;
