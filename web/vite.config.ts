import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { defineConfig } from 'vite'

// 개발 중에는 API와 미디어 요청을 각 서버로 넘긴다
const springServer = 'http://localhost:8180'
const mediaMtxHls = 'http://localhost:8888'
const mediaMtxWhip = 'http://localhost:8889'

// 휴대폰 브라우저는 HTTPS에서만 카메라를 열어 준다.
// `npm run dev:https`는 자체 서명 인증서로 같은 Wi-Fi의 휴대폰에서도 접속할 수 있게 한다.
const https = process.env.HTTPS === '1'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), ...(https ? [basicSsl()] : [])],
  server: {
    // 기본값(localhost)이면 Node가 IPv6(::1)에만 열어서, localhost를 127.0.0.1로 찾는 Firefox는 연결하지 못한다
    host: https ? true : '127.0.0.1',
    proxy: {
      // MediaMTX만 부르는 서버 내부 API(유튜브 동시 송출 중계, ADR-0007)는 웹으로 넘기지 않는다
      '^/api/internal/': { target: springServer, bypass: () => false },
      '/api': springServer,
      '/media': springServer,
      '/hls': {
        target: mediaMtxHls,
        rewrite: (path) => path.replace(/^\/hls/, ''),
        // MediaMTX는 첫 요청을 쿠키 확인용 절대 경로(/live/...)로 리다이렉트하므로 /hls 접두사를 되붙인다
        configure: (proxy) =>
          proxy.on('proxyRes', (response) => {
            const location = response.headers.location
            if (location?.startsWith('/')) response.headers.location = `/hls${location}`
          }),
      },
      '/whip': { target: mediaMtxWhip, rewrite: (path) => path.replace(/^\/whip/, '') },
    },
  },
})
