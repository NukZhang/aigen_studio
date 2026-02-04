import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const previewProxyTarget = env.VITE_PREVIEW_PROXY_TARGET || 'http://localhost:3002'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        'vue': 'vue/dist/vue.esm-bundler.js'
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api/v1': {
          target: 'http://localhost:8081',
          changeOrigin: true
        },
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        },
        '/__preview__': {
          target: previewProxyTarget,
          changeOrigin: true,
          ws: true
        }
      }
    }
  }
})
