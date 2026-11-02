import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 중에는 API와 미디어 요청을 Spring 서버(8080)로 넘긴다
const server = 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': server,
      '/media': server,
    },
  },
})
