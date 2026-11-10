// 라이브 장면을 한 번에 찍는다. Chrome을 직접 띄우고(가짜 카메라 = 쇼츠 영상), 방송자·시청자 두 컨텍스트로 진행한다.
// 결제창 이후 승인은 실제 카드 인증을 할 수 없으므로, 결제 대기 후원을 로컬 DB에서 완료로 바꿔 알림을 띄운다(데모 데이터).
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import { join } from 'node:path'
import { SCREENS, BASE, ACCOUNTS, state, save, sleep } from './lib.mjs'

const require = createRequire(import.meta.url)
const puppeteer = require(process.env.PUPPETEER_CORE)
const FAKE = process.env.FAKE_MEDIA

const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  protocolTimeout: 60000,
  args: [
    '--use-fake-ui-for-media-stream',
    '--use-fake-device-for-media-stream',
    `--use-file-for-fake-video-capture=${FAKE}/cam.mjpeg`,
    `--use-file-for-fake-audio-capture=${FAKE}/mic.wav`,
    '--autoplay-policy=no-user-gesture-required',
    '--disable-renderer-backgrounding',
    '--disable-background-timer-throttling',
    '--disable-backgrounding-occluded-windows',
    '--lang=ko-KR',
  ],
})

async function open(viewport) {
  const context = await browser.createBrowserContext()
  await context.overridePermissions(BASE, ['camera', 'microphone'])
  const page = await context.newPage()
  await page.setViewport(viewport)
  page.setDefaultTimeout(30000)
  page.on('pageerror', (e) => console.log('pageerror', e.message))
  await page.goto(BASE, { waitUntil: 'networkidle2' })
  return page
}

async function api(page, method, url, body) {
  return page.evaluate(
    async ({ method, url, body }) => {
      if (!document.cookie.includes('XSRF-TOKEN=')) await fetch('/api/auth/me')
      const token = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.slice(11)
      const headers = { 'Content-Type': 'application/json' }
      if (method !== 'GET' && token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
      const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
      const text = await res.text()
      try {
        return { status: res.status, json: JSON.parse(text) }
      } catch {
        return { status: res.status, text }
      }
    },
    { method, url, body },
  )
}

async function login(page, who) {
  const acc = ACCOUNTS[who]
  const res = await api(page, 'POST', '/api/auth/login', { username: acc.username, password: acc.password })
  if (res.status !== 200) throw new Error(`login ${who} ${res.status}`)
  return res.json.user
}

async function shot(page, name, wait = 600) {
  await sleep(wait)
  await page.bringToFront()
  await page.screenshot({ path: join(SCREENS, `${name}.png`) })
  console.log('saved', name)
}

async function rect(page, selector) {
  return page.$eval(selector, (el) => {
    const r = el.getBoundingClientRect()
    return { x: r.x, y: r.y, width: r.width, height: r.height, vw: innerWidth, vh: innerHeight }
  })
}

const B = await open({ width: 390, height: 844, deviceScaleFactor: 3, isMobile: true, hasTouch: true })
const V = await open({ width: 1440, height: 900, deviceScaleFactor: 2 })
const me = await login(B, 'broadcaster')
await login(V, 'viewer')

// 이전 데모 방송 정리
const list = await api(B, 'GET', '/api/broadcasts')
for (const x of [...list.json.live, ...list.json.recentlyEnded]) {
  if (x.owner.id === me.id) console.log('delete old broadcast', (await api(B, 'DELETE', `/api/broadcasts/${x.id}`)).status)
}

// 방송자: 준비 → 시작 → 방송 중
await B.goto(BASE + '/broadcast', { waitUntil: 'networkidle2' })
await B.waitForFunction(() => document.querySelector('.camera video')?.readyState >= 2)
await B.$eval('button.camera-switch', (el) => el.click())
await B.waitForFunction(() => !document.querySelector('.camera video.mirrored') && document.querySelector('.camera video')?.readyState >= 2)
await B.type('form.broadcaster-controls input.text-input', '자바 클래스 라이브 강의')
await shot(B, 'broadcast-preview', 1500)
await B.$eval('form.broadcaster-controls', (f) => f.requestSubmit())
await sleep(250)
await shot(B, 'broadcast-starting', 0)
await B.waitForFunction(() => document.querySelector('.camera-badge.live'), { timeout: 40000 })
await shot(B, 'broadcast-live', 3000)
state.rects = { broadcasterCamera: await rect(B, '.camera') }
const live = await api(B, 'GET', '/api/broadcasts')
state.broadcastId = live.json.live.find((x) => x.owner.id === me.id).id
save()
console.log('broadcast', state.broadcastId)

// 시청자(로그인하지 않은 브라우저): 라이브 목록 → 시청
const A = await open({ width: 1440, height: 900, deviceScaleFactor: 2 })
await A.goto(BASE + '/', { waitUntil: 'networkidle2' })
await shot(A, 'live-list', 1500)
async function playLive(P = V) {
  for (let i = 0; i < 40; i++) {
    const s = await P.evaluate(() => {
      const v = document.querySelector('.stage video')
      if (!v) return { none: true, text: document.querySelector('.stage')?.innerText }
      if (v.paused) {
        v.muted = true
        v.play().catch(() => {})
      }
      return { rs: v.readyState, paused: v.paused, t: v.currentTime, err: v.error?.message, src: v.currentSrc?.slice(0, 60) }
    })
    if (i % 5 === 0) console.log('player', JSON.stringify(s))
    if (s.rs >= 3 && !s.paused && s.t > 0.5) return true
    await sleep(1000)
  }
  return false
}
await A.goto(`${BASE}/live/${state.broadcastId}`, { waitUntil: 'networkidle2' })
console.log('playing (anonymous)', await playLive(A))
await shot(A, 'live-watch', 3000)
state.rects.viewerPlayer = await rect(A, '.stage video')

// 로그인한 시청자는 댓글·후원에 쓴다
await V.goto(`${BASE}/live/${state.broadcastId}`, { waitUntil: 'networkidle2' })
await playLive(V)

// 댓글 (시청자, 방송자 휴대폰에서도 하나)
async function comment(page, text) {
  const box = (await page.$('.live-layout form textarea, .live-layout form input.text-input')) ?? (await page.$('form textarea'))
  await box.type(text)
  await box.evaluate((el) => el.closest('form').requestSubmit())
  await sleep(1200)
}
await comment(V, '클래스와 객체 차이가 한 번에 이해되네요!')
await shot(V, 'live-watch-comment', 1500)
save()

// 후원: 금액 선택 → 결제창
await V.goto(`${BASE}/live/${state.broadcastId}/donate`, { waitUntil: 'networkidle2' })
await V.waitForSelector('.amount-presets button')
await V.evaluate(() => {
  const b = [...document.querySelectorAll('.amount-presets button')].find((x) => x.textContent.includes('5,000'))
  b?.click()
})
await V.type('.donate-form textarea', '강의 잘 보고 있어요. 응원합니다!')
await shot(V, 'donate-form', 800)
await V.$eval('.donate-form', (f) => f.requestSubmit())
await sleep(7000)
await shot(V, 'toss-window', 1000)

// 결제 대기 후원을 완료로 (데모 데이터)
const sent = await api(V, 'GET', '/api/donations/sent')
const pending = sent.json.find((d) => d.status === 'PENDING')
console.log('pending donation', pending?.id)
if (pending) {
  execFileSync('docker', ['exec', 'web-broadcast-proj-postgres-1', 'psql', '-U', 'webbroadcast', '-d', 'webbroadcast', '-c',
    `update donation set status='DONE', completed_at=now(), payment_key='demo-${pending.id}' where id='${pending.id}'`])
}
await V.goto(`${BASE}/live/${state.broadcastId}`, { waitUntil: 'networkidle2' })
await playLive()
await B.bringToFront()
// 새로 완료된 후원이 3초 주기 조회로 들어오면 알림이 뜬다
await B.waitForSelector('.donation-alert', { timeout: 20000 }).catch(() => console.log('no alert on broadcaster'))
await shot(B, 'broadcast-alert', 300)
await shot(V, 'live-watch-donation', 1500)

// 후원 내역
await V.goto(BASE + '/donations', { waitUntil: 'networkidle2' })
await shot(V, 'donations-sent', 1200)
const D = await open({ width: 1440, height: 900, deviceScaleFactor: 2 })
await login(D, 'broadcaster')
await D.goto(BASE + '/donations', { waitUntil: 'networkidle2' })
await shot(D, 'donations-received-tab', 1200)
await D.evaluate(() => [...document.querySelectorAll('button, a')].find((x) => x.textContent.trim().startsWith('받은'))?.click())
await shot(D, 'donations-received', 1200)

// 방송자: 방송 종료
await B.bringToFront()
await B.evaluate(() => [...document.querySelectorAll('.broadcaster-controls button')].find((x) => x.textContent.includes('방송 종료'))?.click())
await sleep(500)
await shot(B, 'broadcast-confirm-end', 0)
await B.evaluate(() => [...document.querySelectorAll('.confirm-inline button')].find((x) => x.textContent.trim() === '종료')?.click())
await sleep(3000)
await shot(B, 'broadcast-ended', 0)

save()
await browser.close()
console.log('done')
