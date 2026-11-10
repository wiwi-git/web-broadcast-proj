// 캡처용 헤드리스 Chrome(포트 9333)에 붙어 단계별로 화면을 찍는 도우미.
// 브라우저 컨텍스트는 사용자별(broadcaster, viewer)로 하나씩 두고, 다음 실행에서도 이어서 쓴다.
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const puppeteer = require(process.env.PUPPETEER_CORE)

const HERE = dirname(fileURLToPath(import.meta.url))
export const SCREENS = join(HERE, '../../video/assets/screens')
const STATE = join(HERE, 'state.local.json')
export const BASE = 'http://localhost:5173'
export const ACCOUNTS = JSON.parse(readFileSync(join(HERE, '../demo-accounts.local.json'), 'utf8'))

mkdirSync(SCREENS, { recursive: true })

export const state = existsSync(STATE) ? JSON.parse(readFileSync(STATE, 'utf8')) : {}
export const save = () => writeFileSync(STATE, JSON.stringify(state, null, 1))

const VIEWPORTS = {
  broadcaster: { width: 390, height: 844, deviceScaleFactor: 3, isMobile: true, hasTouch: true },
  viewer: { width: 1440, height: 900, deviceScaleFactor: 2 },
}

export async function connect() {
  return puppeteer.connect({ browserURL: 'http://127.0.0.1:9333', defaultViewport: null })
}

/** who의 컨텍스트와 그 안의 첫 페이지 */
export async function pageOf(browser, who, { viewport } = {}) {
  const contexts = browser.browserContexts()
  const saved = state[`ctx_${who}`]
  let context = saved ? contexts.find((c) => c.id === saved) : undefined
  if (!context) {
    context = await browser.createBrowserContext()
    state[`ctx_${who}`] = context.id
    save()
  }
  await context.overridePermissions(BASE, ['camera', 'microphone'])
  const pages = await context.pages()
  const page = pages[0] ?? (await context.newPage())
  // 모바일 에뮬레이션을 다시 걸면 페이지가 새로고침되는데, 라이브 페이지는 load가 늦을 수 있다
  await page.setViewport(viewport ?? VIEWPORTS[who]).catch((e) => console.log('viewport:', e.message))
  await page.bringToFront()
  page.setDefaultTimeout(30000)
  return page
}

export async function shot(page, name, options = {}) {
  await new Promise((r) => setTimeout(r, options.wait ?? 600))
  const path = join(SCREENS, `${name}.png`)
  await page.screenshot({ path, ...options.screenshot })
  console.log('saved', name)
  return path
}

/** 페이지 안에서 앱과 같은 방식(CSRF 헤더 포함)으로 API를 부른다 */
export async function api(page, method, url, body) {
  return page.evaluate(
    async ({ method, url, body }) => {
      if (!document.cookie.includes('XSRF-TOKEN=')) await fetch('/api/auth/me')
      const token = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.slice(11)
      const headers = { 'Content-Type': 'application/json' }
      if (method !== 'GET' && token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
      const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
      const text = await res.text()
      let json = null
      try {
        json = JSON.parse(text)
      } catch {}
      return { status: res.status, json, text: json ? undefined : text.slice(0, 300) }
    },
    { method, url, body },
  )
}

export async function ensureLoggedIn(page, who) {
  if (!page.url().startsWith(BASE)) await page.goto(BASE, { waitUntil: 'networkidle2' })
  const me = await api(page, 'GET', '/api/auth/me')
  if (me.json?.user) return me.json.user
  const acc = ACCOUNTS[who]
  let res = await api(page, 'POST', '/api/auth/login', { username: acc.username, password: acc.password })
  if (res.status !== 200) res = await api(page, 'POST', '/api/auth/signup', acc)
  if (res.status >= 300) throw new Error(`login ${who}: ${res.status} ${JSON.stringify(res.json ?? res.text)}`)
  return res.json.user
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
