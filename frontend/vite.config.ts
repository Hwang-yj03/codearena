import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 개발 중에는 /api 요청을 Spring 서버로 넘긴다. 같은 출처로 보이므로 CORS 설정이 필요 없다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // Monaco 에디터가 커서 기본 경고 기준(500KB)을 넘는다. 에디터는 문제 화면에서만 지연 로딩한다.
    chunkSizeWarningLimit: 4000,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: false,
  },
})
