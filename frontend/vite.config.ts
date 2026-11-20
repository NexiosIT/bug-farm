/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { msw } from 'msw/vite'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  // The msw plugin serves the mock worker script for `VITE_MOCK=1 npm run dev`.
  // Only in dev, so production builds never ship the mock worker.
  plugins: [react(), command === 'serve' ? msw({ mode: 'worker-only' }) : null],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    restoreMocks: true,
  },
}))
