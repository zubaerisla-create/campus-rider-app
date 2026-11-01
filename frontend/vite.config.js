import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The dev server proxies /api and /ws to the Spring Boot backend, so the browser
// talks to a single origin (session cookie + CSRF cookie work without CORS).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8080'
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: backend, changeOrigin: false },
        '/ws': { target: backend.replace(/^http/, 'ws'), ws: true, changeOrigin: false },
      },
    },
    preview: {
      port: 5173,
      proxy: {
        '/api': { target: backend, changeOrigin: false },
        '/ws': { target: backend.replace(/^http/, 'ws'), ws: true, changeOrigin: false },
      },
    },
  }
})
