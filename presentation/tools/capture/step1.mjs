// 로그인 화면, 데모 계정, 동영상 업로드
import { connect, pageOf, shot, BASE, ACCOUNTS, ensureLoggedIn, state, save, sleep } from './lib.mjs'

const b = await connect()
const v = await pageOf(b, 'viewer')
await v.goto(BASE + '/login', { waitUntil: 'networkidle2' })
if (await v.$('form.auth-form')) {
  await v.type('form.auth-form input.text-input', ACCOUNTS.viewer.username)
  await shot(v, 'login')
}
await ensureLoggedIn(v, 'viewer')

const d = await pageOf(b, 'ownerdesk', { viewport: { width: 1440, height: 900, deviceScaleFactor: 2 } })
await ensureLoggedIn(d, 'broadcaster')
await d.goto(BASE + '/upload', { waitUntil: 'networkidle2' })
const input = await d.$('input[type=file]')
// UPLOAD_VIDEO: 업로드 데모에 쓸 동영상 파일 경로
await input.uploadFile(process.env.UPLOAD_VIDEO)
const [title] = await d.$$('form.upload-form input:not([type=file])')
await title.type('SQL SELECT · JOIN · GROUP BY 정리')
await d.type('form.upload-form textarea', '쇼츠로 정리한 SQL 기본 문법입니다. SELECT, JOIN, GROUP BY를 예제로 봅니다.')
await shot(d, 'upload-form')
await d.$eval('form.upload-form', (f) => f.requestSubmit())
await sleep(800)
await shot(d, 'upload-progress', { wait: 0 })
for (let i = 0; i < 60 && !d.url().includes('/videos/'); i++) await sleep(1000)
state.videoId = d.url().split('/videos/')[1]
save()
await shot(d, 'video-processing', { wait: 1200 })
console.log('video', state.videoId)
b.disconnect()
