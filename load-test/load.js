// 서버에 부하를 걸어 HPA가 Pod를 늘리게 하고, 그동안 상태가 유지되는지 확인한다.
// 실행: load-test/run.sh  (k6를 Docker로 돌린다. BASE_URL로 대상 주소를 바꿀 수 있다)
//
// - 가상 사용자마다 로그인(bcrypt라 CPU를 많이 쓴다)과 목록 조회를 반복한다.
// - setup에서 만든 회원의 세션 쿠키 하나로 매번 /api/auth/me, 방송, 댓글을 조회한다.
//   요청이 어느 Pod로 가든, Pod가 늘거나 줄어도 같은 결과가 나와야 한다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = 'password1';

export const options = {
  stages: [
    { duration: '30s', target: 30 }, // 부하 올리기
    { duration: '3m', target: 30 },  // 유지: 서버 Pod가 늘어난다
    { duration: '20s', target: 0 },  // 멈추기: 1분쯤 뒤부터 Pod가 줄어든다
  ],
  thresholds: {
    // 스케일이 바뀌는 동안에도 상태 확인은 모두 통과해야 한다
    'checks{kind:state}': ['rate>0.99'],
    http_req_failed: ['rate<0.02'],
  },
};

function xsrf(jar) {
  const cookies = jar.cookiesForURL(BASE);
  return cookies['XSRF-TOKEN'] ? cookies['XSRF-TOKEN'][0] : '';
}

function json(body, token, extra = {}) {
  return {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    ...extra,
  };
}

/** 회원 하나를 만들고 방송과 댓글을 남긴다. 이 세션으로 상태가 유지되는지 확인한다. */
export function setup() {
  const jar = http.cookieJar();
  const suffix = Date.now().toString(36).slice(-6);
  const username = `load${suffix}`;
  const nickname = `부하${suffix}`;

  http.get(`${BASE}/api/auth/me`);
  const signup = http.post(`${BASE}/api/auth/signup`,
    JSON.stringify({ username, password: PASSWORD, nickname }), json(null, xsrf(jar)));
  check(signup, { 'signup 201': (r) => r.status === 201 });

  const token = xsrf(jar);
  const broadcast = http.post(`${BASE}/api/broadcasts`, JSON.stringify({ title: `부하 테스트 방송 ${suffix}` }),
    json(null, token));
  check(broadcast, { 'broadcast 201': (r) => r.status === 201 });
  // 응답은 {broadcast, streamKey, whipUrl}
  const broadcastId = broadcast.json('broadcast.id');

  const marker = `스케일 확인용 댓글 ${suffix}`;
  const comment = http.post(`${BASE}/api/broadcasts/${broadcastId}/comments`, JSON.stringify({ body: marker }),
    json(null, token));
  check(comment, { 'comment 201': (r) => r.status === 201 });

  return { username, nickname, broadcastId, marker, session: jar.cookiesForURL(BASE).SESSION[0] };
}

export default function (data) {
  // ① CPU 부하: 로그인 (가상 사용자마다 자기 쿠키 저장소를 쓴다)
  const jar = http.cookieJar();
  http.get(`${BASE}/api/auth/me`);
  http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: data.username, password: PASSWORD }),
    json(null, xsrf(jar), { tags: { name: 'login' } }));
  http.get(`${BASE}/api/broadcasts`, { tags: { name: 'broadcasts' } });
  http.get(`${BASE}/api/videos`, { tags: { name: 'videos' } });

  // ② 상태 확인: setup의 세션 쿠키로 조회 (쿠키 저장소 대신 직접 넣는다)
  const as = { cookies: { SESSION: { value: data.session, replace: true } }, tags: { kind: 'state' } };
  const me = http.get(`${BASE}/api/auth/me`, as);
  check(me, { '로그인 유지': (r) => r.json('user.nickname') === data.nickname }, { kind: 'state' });

  const broadcast = http.get(`${BASE}/api/broadcasts/${data.broadcastId}`, as);
  check(broadcast, { '방송 유지': (r) => r.status === 200 }, { kind: 'state' });

  const comments = http.get(`${BASE}/api/broadcasts/${data.broadcastId}/comments`, as);
  check(comments, { '댓글 유지': (r) => r.status === 200 && r.body.includes(data.marker) }, { kind: 'state' });

  sleep(0.5);
}
