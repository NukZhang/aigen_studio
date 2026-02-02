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
        <p class="note">注意：预览功能会在后台启动前后端服务</p>
      </div>

      <div v-else-if="isStarting" class="loading">
        <el-icon class="is-loading" size="48"><Loading /></el-icon>
        <p>正在启动预览服务...</p>
        <p class="hint">这可能需要几秒钟时间</p>
      </div>

      <iframe
        v-else-if="previewUrl"
        :src="previewUrl"
        class="preview-frame"
        frameborder="0"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      ></iframe>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause, TopRight, Loading, Monitor, Refresh } from '@element-plus/icons-vue'
import { previewApi, type PreviewStatus } from '../api/job'

interface Props {
  conversationId?: number
}

const props = defineProps<Props>()

const status = ref<PreviewStatus | null>(null)
const isStarting = ref(false)
const isStopping = ref(false)
const isRestarting = ref(false)

const isPreviewRunning = computed(() => status.value?.running ?? false)
const previewUrl = computed(() => status.value?.frontendUrl || status.value?.backendUrl || '')
const conversationId = computed(() => props.conversationId)

const fetchStatus = async () => {
  if (!conversationId.value) {
    status.value = null
    return
  }
  try {
    const response = await previewApi.getStatus(conversationId.value)
    status.value = response.data
  } catch (error) {
    console.error('Failed to fetch preview status:', error)
  }
}

watch(conversationId, () => {
  fetchStatus()
}, { immediate: true })

const startPreview = async () => {
  if (!conversationId.value) return
  isStarting.value = true
  try {
    const response = await previewApi.startPreview(conversationId.value)
    status.value = response.data
    ElMessage.success('预览已启动')
  } catch (error) {
    console.error('Failed to start preview:', error)
    ElMessage.error('启动预览失败')
  } finally {
    isStarting.value = false
  }
}

const stopPreview = async () => {
  if (!conversationId.value) return
  isStopping.value = true
  try {
    const response = await previewApi.stopPreview(conversationId.value)
    status.value = response.data
    ElMessage.success('预览已停止')
  } catch (error) {
    console.error('Failed to stop preview:', error)
    ElMessage.error('停止预览失败')
  } finally {
    isStopping.value = false
  }
}

const restartPreview = async () => {
  if (!conversationId.value) return
  isRestarting.value = true
  try {
    const response = await previewApi.restartPreview(conversationId.value)
    status.value = response.data
    ElMessage.success('预览已重启')
  } catch (error) {
    console.error('Failed to restart preview:', error)
    ElMessage.error('重启预览失败')
  } finally {
    isRestarting.value = false
  }
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}
</script>

<style scoped>
.preview-panel {
  display: flex;
  flex-direction: column;
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

.preview-frame {
  width: 100%;
  height: 100%;
  border: none;
  background-color: #ffffff;
}
</style>
