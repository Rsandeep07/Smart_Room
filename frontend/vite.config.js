import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Dev server for the dashboard.
 *
 * `/api` is proxied to the backend so the browser sees one origin during development.
 * The backend also configures CORS for http://localhost:5173 (build plan Step 6.6), so
 * this proxy is not strictly required - but going through it means the dev setup
 * matches production, where the built bundle is served from the same host as the API
 * and no CORS is involved at all.
 *
 * Override the target with VITE_API_TARGET when the backend runs elsewhere.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
