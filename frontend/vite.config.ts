import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const previewProxyTarget = env.VITE_PREVIEW_PROXY_TARGET || 'http://localhost:3002'
  const previewApiProxyTarget = env.VITE_PREVIEW_API_PROXY_TARGET || 'http://localhost:8081'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        'vue': 'vue/dist/vue.esm-bundler.js'
      }
    },
    build: {
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return
            }
            if (id.includes('monaco-editor')) {
              return 'vendor-monaco'
            }
            if (id.includes('@element-plus/icons-vue')) {
              return 'vendor-ep-icons'
            }
            if (id.includes('element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('vue') || id.includes('vue-router') || id.includes('pinia')) {
              return 'vendor-vue'
            }
            if (id.includes('marked') || id.includes('dompurify')) {
              return 'vendor-content'
            }
            return 'vendor-misc'
          }
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 3000,
      proxy: {
        '/api/v1': {
          target: 'http://localhost:8081',
          changeOrigin: true
        },
        '/subapi': {
          target: previewApiProxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/subapi/, '/api')
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
