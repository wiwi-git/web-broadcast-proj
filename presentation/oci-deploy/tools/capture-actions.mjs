// GitHub Actions 데스크톱 빌드 실행 화면을 찍는다(로그인 없이 공개 페이지).
// 사용자 이름이 보이는 머리말은 build_composition에서 잘라 쓰도록, 원본과 함께 요소 위치를 출력한다.
//
//   PUPPETEER_CORE=<puppeteer-core 경로> node presentation/oci-deploy/tools/capture-actions.mjs <run id>
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const puppeteer = require(process.env.PUPPETEER_CORE)
const SCREENS = join(dirname(fileURLToPath(import.meta.url)), '../video/assets/screens')
const run = process.argv[2]

const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  args: ['--lang=ko-KR'],
})
const page = await browser.newPage()
await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: 'dark' }])
await page.setViewport({ width: 1440, height: 1100, deviceScaleFactor: 2 })
await page.goto(`https://github.com/wiwi-git/web-broadcast-proj/actions/runs/${run}`, { waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 2500))
await page.screenshot({ path: join(SCREENS, 'actions-run-raw.png') })
// 사용자 이름이 들어 있는 요소들의 위치 (가림 확인용)
const hits = await page.evaluate(() => {
  const out = []
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)
  while (walker.nextNode()) {
    const n = walker.currentNode
    if (/wiwi/i.test(n.textContent)) {
      const r = n.parentElement.getBoundingClientRect()
      if (r.width && r.bottom > 0 && r.top < innerHeight) out.push([Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height), n.textContent.trim().slice(0, 40)])
    }
  }
  return out
})
console.log(JSON.stringify(hits))
await browser.close()
