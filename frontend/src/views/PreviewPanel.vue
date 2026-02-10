<template>
  <div class="preview-panel">
    <div class="preview-header">
      <div class="header-left">
        <h3 class="title">应用预览</h3>
        <span v-if="previewUrl" class="url">{{ previewUrl }}</span>
      </div>
      <div class="header-right">
        <el-button
          v-if="!isPreviewRunning"
          type="primary"
          size="small"
          @click="startPreview"
          :loading="isStarting"
        >
          <el-icon><VideoPlay /></el-icon>
          启动预览
        </el-button>
        <el-button
          v-else
          type="danger"
          size="small"
          @click="stopPreview"
          :loading="isStopping"
        >
          <el-icon><VideoPause /></el-icon>
          停止预览
        </el-button>
        <el-button
          v-if="isPreviewRunning"
          size="small"
          @click="restartPreview"
          :loading="isRestarting"
        >
          <el-icon><Refresh /></el-icon>
          重启
        </el-button>
        <el-button
          v-if="previewUrl"
          size="small"
          @click="openInNewTab"
        >
          <el-icon><TopRight /></el-icon>
          新标签页打开
        </el-button>
        <el-button
          v-if="isPreviewRunning || previewUrl"
          size="small"
          @click="toggleView"
        >
          <el-icon><component :is="currentView === 'logs' ? Monitor : Document" /></el-icon>
          {{ currentView === 'logs' ? '查看预览' : '查看日志' }}
        </el-button>
      </div>
    </div>

    <div class="preview-content">
      <div v-if="!conversationId" class="placeholder">
        <el-icon size="64"><Monitor /></el-icon>
        <p>预览仅支持对话模式</p>
        <p class="hint">请先创建对话并完成代码生成</p>
      </div>

      <div v-else-if="!isPreviewRunning && !isStarting" class="placeholder">
        <el-icon size="64"><Monitor /></el-icon>
        <p>预览功能尚未启动</p>
        <p class="hint">点击上方"启动预览"按钮，预览生成的应用</p>
        <p class="note">注意：预览功能会在后台启动前后端服务，端口以配置文件为准</p>
        <p v-if="status?.message" class="status-message">{{ status.message }}</p>
      </div>

      <div v-else-if="isStarting || currentView === 'logs'" class="startup-logs">
        <div class="logs-header">
          <el-icon v-if="isStarting" class="is-loading"><Loading /></el-icon>
          <span>{{ isStarting ? '正在启动预览服务...' : '服务日志' }}</span>
        </div>
        <div class="logs-container" ref="logsContainer">
          <div v-for="(log, index) in startupLogs" :key="index" class="log-line">
            <span class="log-time">{{ log.time }}</span>
            <span :class="['log-content', log.type]">{{ log.content }}</span>
          </div>
          <div v-if="startupLogs.length === 0" class="log-placeholder">
            {{ isStarting ? '等待日志输出...' : '暂无日志' }}
          </div>
        </div>
      </div>

      <div v-else-if="previewUrl" class="iphone-frame">
        <div class="iphone-border">
          <div class="dynamic-island"></div>
          <iframe
            :src="previewUrl"
            class="preview-frame"
            frameborder="0"
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
          ></iframe>
        </div>
      </div>

      <div v-else class="placeholder">
        <el-icon size="64"><Monitor /></el-icon>
        <p>预览服务尚未就绪</p>
        <p class="hint">点击上方"查看日志"查看启动进度</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause, TopRight, Loading, Monitor, Refresh, Document } from '@element-plus/icons-vue'
import { previewApi, type PreviewStatus } from '../api/job'

interface Props {
  conversationId?: number
}

const props = defineProps<Props>()

const status = ref<PreviewStatus | null>(null)
const isStarting = ref(false)
const isStopping = ref(false)
const isRestarting = ref(false)
const isStartupComplete = ref(false)
const currentView = ref<'logs' | 'preview'>('logs')
const startupLogs = ref<Array<{ time: string, content: string, type: string }>>([])
const logsContainer = ref<HTMLElement | null>(null)
let logCheckInterval: number | null = null
let startupCheckInterval: number | null = null

const isPreviewRunning = computed(() => status.value?.running ?? false)

const toggleView = () => {
  currentView.value = currentView.value === 'logs' ? 'preview' : 'logs'
}

const resolveHost = () => {
  if (typeof window === 'undefined') return 'localhost'
  return window.location.hostname || 'localhost'
}

const resolveProtocol = () => {
  if (typeof window === 'undefined') return 'http:'
  return window.location.protocol || 'http:'
}

const replaceLocalhost = (rawUrl?: string | null) => {
  if (!rawUrl) return ''
  try {
    const url = new URL(rawUrl)
    if (['localhost', '127.0.0.1', '::1'].includes(url.hostname)) {
      url.hostname = resolveHost()
      url.protocol = resolveProtocol()
      return url.toString().replace(/\/$/, '')
    }
    return rawUrl
  } catch (error) {
    return rawUrl
  }
}

const normalizeProxyPath = (rawPath: string) => {
  const normalized = rawPath.startsWith('/') ? rawPath : `/${rawPath}`
  return normalized.endsWith('/') ? normalized : `${normalized}/`
}

const shouldUsePreviewProxy = () => {
  if (typeof window === 'undefined') return false
  const envFlag = import.meta.env.VITE_PREVIEW_PROXY
  if (envFlag === 'true') return true
  if (envFlag === 'false') return false
  return false // 默认直连预览服务，避免生成页面将 /api 误指向主站 3000
}

const previewProxyPath = normalizeProxyPath(
  import.meta.env.VITE_PREVIEW_PROXY_PATH || '/__preview__'
)

const previewProxyBase = computed(() => {
  if (typeof window === 'undefined') return ''
  return `${window.location.origin}${previewProxyPath}`
})

const previewUrl = computed(() => {
  const currentStatus = status.value
  if (!currentStatus) return ''

  if (shouldUsePreviewProxy() && currentStatus.frontendRunning) {
    return previewProxyBase.value
  }

  const host = resolveHost()
  const protocol = resolveProtocol()
  if (currentStatus.frontendRunning && currentStatus.frontendPort) {
    return `${protocol}//${host}:${currentStatus.frontendPort}${previewProxyPath}`
  }
  if (currentStatus.backendRunning && currentStatus.backendPort) {
    return `${protocol}//${host}:${currentStatus.backendPort}`
  }

  return replaceLocalhost(currentStatus.frontendUrl) || replaceLocalhost(currentStatus.backendUrl)
})
const conversationId = computed(() => props.conversationId)

const fetchStatus = async () => {
  console.log('[PreviewPanel] fetchStatus called, conversationId:', conversationId.value)
  if (!conversationId.value) {
    status.value = null
    isStartupComplete.value = false
    console.log('[PreviewPanel] No conversationId, status cleared')
    return
  }
  try {
    const response = await previewApi.getStatus(conversationId.value)
    status.value = response.data
    console.log('[PreviewPanel] Status fetched:', response.data)
    // 如果预览正在运行但未标记为完成，检查是否应该标记为完成
    if (status.value?.running && !isStartupComplete.value && !isStarting.value) {
      isStartupComplete.value = true
      console.log('[PreviewPanel] Auto-marked as startup complete')
    }
  } catch (error) {
    console.error('[PreviewPanel] Failed to fetch preview status:', error)
    // 网络错误时不清空status，保留当前状态
  }
}

const startPreview = async () => {
  if (!conversationId.value) {
    return
  }
  isStarting.value = true
  isStartupComplete.value = false
  currentView.value = 'logs'
  clearLogs()
  addLog('正在启动预览服务...', 'status')

  try {
    startLogCheck()
    startStartupCheck()

    const response = await previewApi.startPreview(conversationId.value)
    status.value = response.data
    addLog('预览服务启动命令已发送', 'status')
  } catch (error) {
    console.error('Failed to start preview:', error)
    ElMessage.error('启动预览失败')
    isStarting.value = false
    stopStartupCheck()
    stopLogCheck()
  }
}

const stopPreview = async () => {
  if (!conversationId.value) return
  isStopping.value = true
  addLog('正在停止预览服务...', 'status')
  try {
    const response = await previewApi.stopPreview(conversationId.value)
    status.value = response.data
    ElMessage.success('预览已停止')
    cleanup()
  } catch (error) {
    console.error('Failed to stop preview:', error)
    ElMessage.error('停止预览失败')
  } finally {
    isStopping.value = false
    isStartupComplete.value = false
  }
}

const restartPreview = async () => {
  if (!conversationId.value) return
  isRestarting.value = true
  addLog('正在重启预览服务...', 'status')
  try {
    cleanup()
    isStartupComplete.value = false
    clearLogs()

    startLogCheck()
    startStartupCheck()

    const response = await previewApi.restartPreview(conversationId.value)
    status.value = response.data
    addLog('预览服务重启命令已发送', 'status')
  } catch (error) {
    console.error('Failed to restart preview:', error)
    ElMessage.error('重启预览失败')
    cleanup()
  } finally {
    isRestarting.value = false
  }
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 日志文件读取和显示
const addLog = (content: string, type: string = 'info') => {
  const now = new Date()
  const time = now.toTimeString().split(' ')[0]
  startupLogs.value.push({ time, content, type })
  // 自动滚动到底部
  nextTick(() => {
    if (logsContainer.value) {
      logsContainer.value.scrollTop = logsContainer.value.scrollHeight
    }
  })
}

const clearLogs = () => {
  startupLogs.value = []
}

const fetchLogs = async () => {
  if (!conversationId.value) return

  try {
    const response = await previewApi.getLogs(conversationId.value, 50)
    const newLogs = response.data.logs

    // 只添加新的日志行（去重）
    const existingContents = new Set(startupLogs.value.map(l => l.content))
    for (const log of newLogs) {
      if (!existingContents.has(log)) {
        // 解析日志类型
        let type = 'info'
        if (log.includes('[Frontend]') || log.includes('[Backend]')) {
          type = 'info'
        } else if (log.includes('Log Started')) {
          type = 'status'
        } else if (log.includes('ERROR') || log.includes('error')) {
          type = 'error'
        } else if (log.includes('WARN') || log.includes('warn')) {
          type = 'warning'
        } else if (log.includes('ready') || log.includes('Started') || log.includes('localhost')) {
          type = 'success'
        }

        addLog(log, type)
        existingContents.add(log)
      }
    }
  } catch (error) {
    console.error('Failed to fetch logs:', error)
  }
}

const startLogCheck = () => {
  stopLogCheck()
  logCheckInterval = window.setInterval(() => {
    fetchLogs()
  }, 3000) // 每3秒读取一次日志
}

const stopLogCheck = () => {
  if (logCheckInterval) {
    clearInterval(logCheckInterval)
    logCheckInterval = null
  }
}

const startStartupCheck = () => {
  isStartupComplete.value = false
  clearLogs()

  // 定期检查服务是否真正启动
  startupCheckInterval = window.setInterval(async () => {
    if (!conversationId.value) return
    try {
      const response = await previewApi.getStatus(conversationId.value)
      status.value = response.data

      // 检查服务是否已启动
      if (status.value?.frontendRunning || status.value?.backendRunning) {
        // 等待一段时间后标记为完成
        setTimeout(() => {
          if (isStarting.value) {
            isStartupComplete.value = true
            isStarting.value = false
            stopStartupCheck()
            addLog('预览服务启动完成', 'success')
          }
        }, 3000)
      }
    } catch (error) {
      console.error('Failed to check startup status:', error)
    }
  }, 2000)
}

const stopStartupCheck = () => {
  if (startupCheckInterval) {
    clearInterval(startupCheckInterval)
    startupCheckInterval = null
  }
}

// 清理函数
const cleanup = () => {
  stopStartupCheck()
  stopLogCheck()
}

watch(conversationId, (newId, oldId) => {
  console.log('[PreviewPanel] conversationId changed:', { oldId, newId })
  if (newId !== oldId) {
    // 只在conversationId真正改变时清理
    cleanup()
    clearLogs()
    isStartupComplete.value = false
    status.value = null
  }
  // 确保在清理之后再fetchStatus
  if (newId) {
    fetchStatus()
  }
}, { immediate: true })

// 组件挂载时初始化
onMounted(() => {
  console.log('[PreviewPanel] Component mounted, conversationId:', conversationId.value)
  if (conversationId.value) {
    fetchStatus()
  }
})

// 组件卸载时清理资源
onUnmounted(() => {
  cleanup()
})
</script>

<style scoped>
.preview-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: #1f1f1f;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #3a3a3a;
  background-color: #2a2a2a;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #e0e0e0;
}

.url {
  font-size: 12px;
  color: #888;
  background-color: #3a3a3a;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
}

.header-right {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.placeholder,
.loading {
  text-align: center;
  color: #666;
}

.placeholder .el-icon,
.loading .el-icon {
  margin-bottom: 16px;
  opacity: 0.5;
}

.placeholder p,
.loading p {
  margin: 8px 0;
  font-size: 14px;
}

.hint {
  font-size: 12px;
  opacity: 0.7;
}

.note {
  font-size: 11px;
  color: #e6a23c;
  margin-top: 16px;
}

.status-message {
  margin-top: 12px;
  font-size: 12px;
  color: #e6a23c;
  word-break: break-word;
}

.preview-frame {
  width: 100%;
  height: 100%;
  border: none;
  background-color: #ffffff;
}

/* iPhone 15 Pro 手机框样式 */
.iphone-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 20px;
  background-color: #1f1f1f;
}

.iphone-border {
  position: relative;
  width: 100%;
  max-width: 390px;
  height: 100%;
  max-height: 844px;
  background: linear-gradient(145deg, #C0C4C8 0%, #D0D4D8 100%);
  border-radius: 32px;
  padding: 12px 10px 10px 10px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(0, 0, 0, 0.05),
    inset 0 0 0 1px rgba(255, 255, 255, 0.3);
  /* 哑光质感 */
  background-image:
    radial-gradient(circle at 20% 30%, rgba(255, 255, 255, 0.05) 0%, transparent 20%),
    radial-gradient(circle at 80% 70%, rgba(0, 0, 0, 0.03) 0%, transparent 20%),
    repeating-linear-gradient(45deg, transparent, transparent 3px, rgba(0, 0, 0, 0.01) 3px, rgba(0, 0, 0, 0.01) 6px);
}

/* Dynamic Island */
.dynamic-island {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  width: 52px;
  height: 24px;
  border-radius: 12px;
  background: #000;
  box-shadow: inset 0 0 2px rgba(0, 0, 0, 0.3);
  z-index: 10;
}

/* 屏幕区域容器 */
.iphone-border .preview-frame {
  width: 100%;
  height: 100%;
  border-radius: 28px;
  overflow: hidden;
  background-color: #000;
}

/* 启动日志样式 */
.startup-logs {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: #1a1a1a;
}

.logs-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #3a3a3a;
  background-color: #252525;
  color: #888;
  font-size: 13px;
}

.logs-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  font-family: 'Courier New', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
  background-color: #0d0d0d;
}

.logs-container::-webkit-scrollbar {
  width: 8px;
}

.logs-container::-webkit-scrollbar-track {
  background: #1a1a1a;
}

.logs-container::-webkit-scrollbar-thumb {
  background: #444;
  border-radius: 4px;
}

.logs-container::-webkit-scrollbar-thumb:hover {
  background: #555;
}

.log-line {
  display: flex;
  gap: 12px;
  padding: 2px 0;
  word-break: break-all;
}

.log-time {
  color: #666;
  min-width: 80px;
  flex-shrink: 0;
}

.log-content {
  flex: 1;
  color: #ccc;
}

.log-content.info {
  color: #ccc;
}

.log-content.status {
  color: #409eff;
}

.log-content.success {
  color: #67c23a;
}

.log-content.error {
  color: #f56c6c;
}

.log-content.warning {
  color: #e6a23c;
}

.log-placeholder {
  color: #666;
  text-align: center;
  padding: 40px 0;
}
</style>
