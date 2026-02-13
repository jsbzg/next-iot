import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api/device': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/model': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/parse': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/alarm-rule': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/offline': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/alarm': {
        target: 'http://localhost:8086',
        changeOrigin: true
      },
      '/api/metric-data': {
        target: 'http://localhost:8086',
        changeOrigin: true
      }
    }
  }
})