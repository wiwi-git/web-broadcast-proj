// 동영상 장면: 목록, 상세(재생·화질), 댓글(작성자만 수정·삭제)
import { createRequire } from 'node:module'
import { join } from 'node:path'
import { SCREENS, BASE, ACCOUNTS, state, save, sleep } from './lib.mjs'

const require = createRequire(import.meta.url)
const puppeteer = require(process.env.PUPPETEER_CORE)
const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  protocolTimeout: 60000,
  args: ['--autoplay-policy=no-user-gesture-required', '--lang=ko-KR', '--disable-renderer-backgrounding'],
})

async function open(who) {
  const context = await browser.createBrowserContext()
  const page = await context.newPage()
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })
  await page.goto(BASE, { waitUntil: 'networkidle2' })
  const acc = ACCOUNTS[who]
  await page.evaluate(async (acc) => {
    await fetch('/api/auth/me')
    const token = decodeURIComponent(document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN=')).slice(11))
    await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
      body: JSON.stringify({ username: acc.username, password: acc.password }),
    })
  }, acc)
  return page
}

async function shot(page, name, wait = 800, clip) {
  await sleep(wait)
  await page.bringToFront()
  await page.screenshot({ path: join(SCREENS, `${name}.png`), ...(clip ? { clip } : {}) })
  console.log('saved', name)
}

async function comment(page, text) {
  const box = await page.$('.comments form textarea, form textarea')
  await box.type(text)
  await box.evaluate((el) => el.closest('form').requestSubmit())
  await sleep(1200)
}

const B = await open('broadcaster')
const V = await open('viewer')

// 이전 캡처에서 남긴 댓글은 각자 지운다
for (const page of [B, V]) {
  const removed = await page.evaluate(async (videoId) => {
    const me = (await (await fetch('/api/auth/me')).json()).user
    const token = decodeURIComponent(document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN=')).slice(11))
    const page = await (await fetch(`/api/videos/${videoId}/comments`)).json()
    let n = 0
    for (const c of page.items) {
      if (c.author.id !== me.id) continue
      await fetch(`/api/comments/${c.id}`, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': token } })
      n++
    }
    return n
  }, state.videoId)
  console.log('removed comments', removed)
}

await V.goto(BASE + '/videos', { waitUntil: 'networkidle2' })
await shot(V, 'video-list', 1500)

await V.goto(`${BASE}/videos/${state.videoId}`, { waitUntil: 'networkidle2' })
await V.waitForFunction(() => document.querySelector('.player video')?.readyState >= 3, { timeout: 40000 })
await V.evaluate(() => {
  const v = document.querySelector('.player video')
  v.muted = true
  v.currentTime = 20
  return v.play()
})
await shot(V, 'video-detail-auto', 2500)
// 화질을 720p로 바꾼 상태
await V.evaluate(() => {
  const s = document.querySelector('.quality select')
  const o = [...s.options].find((x) => x.textContent === '720p')
  if (o) {
    s.value = o.value
    s.dispatchEvent(new Event('change', { bubbles: true }))
  }
})
await shot(V, 'video-detail', 2500)
state.qualityOptions = await V.$$eval('.quality select option', (os) => os.map((o) => o.textContent))
state.rects = state.rects ?? {}
state.rects.videoPlayer = await V.$eval('.player video', (el) => {
  const r = el.getBoundingClientRect()
  return { x: r.x, y: r.y, width: r.width, height: r.height, vw: innerWidth, vh: innerHeight }
})
state.rects.quality = await V.$eval('.quality', (el) => {
  const r = el.getBoundingClientRect()
  return { x: r.x, y: r.y, width: r.width, height: r.height }
})

// 댓글: 방송자(소유자)가 먼저, 시청자가 다음
await B.goto(`${BASE}/videos/${state.videoId}`, { waitUntil: 'networkidle2' })
await comment(B, '영상 올렸습니다. 다음 편은 서브쿼리예요.')
await V.reload({ waitUntil: 'networkidle2' })
const box = await V.$('.comments form textarea, form textarea')
await box.type('GROUP BY 예제가 특히 좋네요. 감사합니다!')
await V.evaluate(() => document.querySelector('.comments-title')?.scrollIntoView({ block: 'start' }))
await shot(V, 'video-comment-typing', 800)
await box.evaluate((el) => el.closest('form').requestSubmit())
await sleep(1200)
await V.evaluate(() => document.querySelector('.comments-title')?.scrollIntoView({ block: 'start' }))
await shot(V, 'video-comments', 1200)

save()
await browser.close()
console.log('done', state.qualityOptions)
