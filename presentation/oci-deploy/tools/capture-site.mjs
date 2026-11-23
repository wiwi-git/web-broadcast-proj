// 실 사이트(duse.dev, live.duse.dev)를 로그인하지 않은 시청자로 찍는다. 라이브 방송이 없어도 되는 화면만.
//
//   PUPPETEER_CORE=<puppeteer-core 경로> node presentation/oci-deploy/tools/capture-site.mjs
//
// 결과: video/assets/screens/*.png (1440×900, 2배 해상도). 화면 글자도 함께 출력해 개인정보가 없는지 확인한다.
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const puppeteer = require(process.env.PUPPETEER_CORE)
const SCREENS = join(dirname(fileURLToPath(import.meta.url)), '../video/assets/screens')
mkdirSync(SCREENS, { recursive: true })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  args: ['--autoplay-policy=no-user-gesture-required', '--lang=ko-KR'],
})
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })

async function shot(name, url, { wait = 1500, before } = {}) {
  await page.goto(url, { waitUntil: 'networkidle2' })
  if (before) await before()
  await sleep(wait)
  await page.screenshot({ path: join(SCREENS, `${name}.png`) })
  const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 700))
  console.log(`\n## ${name} ${url}\n${text}`)
}

const only = process.argv.slice(2)
const want = (name) => only.length === 0 || only.includes(name)

if (want('duse-home')) await shot('duse-home', 'https://duse.dev/')
if (want('live-home')) await shot('live-home', 'https://live.duse.dev/')
if (want('download')) await shot('download', 'https://live.duse.dev/download/')
if (want('vod-list')) await shot('vod-list', 'https://live.duse.dev/videos')
if (want('vod-detail')) {
  const list = await (await fetch('https://live.duse.dev/api/videos?page=0')).json()
  const demo = list.items.find((v) => v.title === '데모 동영상 1')
  await shot('vod-detail', `https://live.duse.dev/videos/${demo.id}`, {
    wait: 2500,
    before: async () => {
      await page.waitForFunction(() => document.querySelector('video')?.readyState >= 3, { timeout: 40000 })
      await page.evaluate(() => {
        const v = document.querySelector('video')
        v.muted = true
        v.currentTime = 8
        return v.play()
      })
    },
  })
}

// 라이브: 데모 방송이 켜져 있어야 한다(가짜 카메라는 켜고·끄고·다시 켠 두 번째 방송). 명시해야만 찍는다
if (only.includes('live')) {
  const { live } = await (await fetch('https://live.duse.dev/api/broadcasts')).json()
  const demo = live.find((b) => b.owner.nickname === '데모방송')
  if (!demo) throw new Error('데모방송 계정의 라이브가 없습니다')
  await shot('live-home-on', 'https://live.duse.dev/', { wait: 2500 })
  await shot('live-watch', `https://live.duse.dev/live/${demo.id}`, {
    wait: 4000,
    before: async () => {
      await page.waitForFunction(() => document.querySelector('video')?.readyState >= 3, { timeout: 40000 })
      await page.evaluate(() => {
        const v = document.querySelector('video')
        v.muted = true
        return v.play()
      })
    },
  })
  // 실제로 영상이 나오는지(소리만 방송이 아닌지) 확인
  console.log('video size', await page.evaluate(() => [document.querySelector('video').videoWidth, document.querySelector('video').videoHeight]))
}

await browser.close()
