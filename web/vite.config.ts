import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { defineConfig } from 'vite'

// 개발 중에는 API와 미디어 요청을 각 서버로 넘긴다
const springServer = 'http://localhost:8080'
const mediaMtxHls = 'http://localhost:8888'
const mediaMtxWhip = 'http://localhost:8889'

// 휴대폰 브라우저는 HTTPS에서만 카메라를 열어 준다.
// `npm run dev:https`는 자체 서명 인증서로 같은 Wi-Fi의 휴대폰에서도 접속할 수 있게 한다.
const https = process.env.HTTPS === '1'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), ...(https ? [basicSsl()] : [])],
  server: {
    host: https ? true : undefined,
    proxy: {
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
