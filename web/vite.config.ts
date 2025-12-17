import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // Security: Keep host: false (default) to avoid exposing dev server to network
    // Only set host: true if you need to test from other devices on your network
    host: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
})

