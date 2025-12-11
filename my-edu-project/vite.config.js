import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },

  server: {
    port: 5173,
    open: true,
    proxy: {
      // 代理 API 请求
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 【新增】代理上传文件的请求，解决图片 404/FAILED 问题
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})