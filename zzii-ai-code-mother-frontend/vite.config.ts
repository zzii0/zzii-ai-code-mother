import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('ant-design-vue') || id.includes('@ant-design/icons-vue')) {
            return 'vendor-antd'
          }
          if (
            id.includes('/node_modules/vue/') ||
            id.includes('/node_modules/vue-router/') ||
            id.includes('/node_modules/pinia/')
          ) {
            return 'vendor-vue'
          }
          if (id.includes('markdown-it') || id.includes('highlight.js')) {
            return 'vendor-markdown'
          }
          if (id.includes('axios') || id.includes('dayjs')) {
            return 'vendor-utils'
          }
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
