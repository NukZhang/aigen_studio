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
        >
          <el-icon><VideoPause /></el-icon>
          停止预览
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
      <div v-if="!isPreviewRunning && !isStarting" class="placeholder">
        <el-icon size="64"><Monitor /></el-icon>
        <p>预览功能尚未启动</p>
        <p class="hint">点击上方"启动预览"按钮，预览生成的应用</p>
        <p class="note">注意：预览功能需要在本地环境中运行 npm run dev</p>
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
import { ref } from 'vue'
import { VideoPlay, VideoPause, TopRight, Loading, Monitor } from '@element-plus/icons-vue'

interface Props {
  jobId: number
}

const props = defineProps<Props>()

const isPreviewRunning = ref(false)
const isStarting = ref(false)
const previewUrl = ref<string>('')

// 默认预览端口为 3001（主项目在 3000）
const getPreviewPort = () => 3000 + props.jobId

const startPreview = async () => {
  isStarting.value = true

  try {
    // 在 PoC 阶段，我们假设预览服务已经在运行
    // 实际实现需要调用后端 API 启动预览服务
    const port = getPreviewPort()
    previewUrl.value = `http://localhost:${port}`
    
    // 模拟启动延迟
    await new Promise(resolve => setTimeout(resolve, 2000))
    
    isPreviewRunning.value = true
  } catch (error) {
    console.error('Failed to start preview:', error)
  } finally {
    isStarting.value = false
  }
}

const stopPreview = () => {
  isPreviewRunning.value = false
  previewUrl.value = ''
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
