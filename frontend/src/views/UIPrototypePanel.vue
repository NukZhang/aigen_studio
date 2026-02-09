<template>
  <div class="ui-prototype-panel">
    <div class="preview-header">
      <div class="header-left">
        <h3 class="title">UI 原型</h3>
        <span v-if="conversation?.stage === 'UI_GENERATING'" class="status-generating">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在生成中...
        </span>
        <span v-else-if="conversation?.uiConfirmed" class="status-confirmed">
          <el-icon><CircleCheck /></el-icon>
          已确认
        </span>
      </div>
      <div class="header-right">
        <el-button
          v-if="conversation?.uiConfirmed"
          type="primary"
          size="small"
          disabled
        >
          <el-icon><CircleCheck /></el-icon>
          已确认 UI 设计
        </el-button>
        <template v-else>
          <el-button
            v-if="conversation?.uiPrototypeContent && !conversation?.uiConfirmed"
            type="primary"
            size="small"
            @click="confirmUIPrototype"
            :loading="isConfirming"
          >
            <el-icon><CircleCheck /></el-icon>
            确认 UI 设计
          </el-button>
          <el-button
            v-if="conversation?.uiPrototypeContent"
            size="small"
            @click="regenerateUIPrototype"
            :loading="isRegenerating"
          >
            <el-icon><Refresh /></el-icon>
            重新生成
          </el-button>
        </template>
      </div>
    </div>

    <div class="preview-content">
      <div v-if="!conversationId" class="placeholder">
        <el-icon size="64"><Monitor /></el-icon>
        <p>UI 原型预览仅支持对话模式</p>
        <p class="hint">请先创建对话并完成需求理解</p>
      </div>

      <div v-else-if="conversation?.stage === 'UNDERSTANDING'" class="placeholder">
        <el-icon size="64"><Loading /></el-icon>
        <p>等待需求理解完成</p>
        <p class="hint">AI 正在理解您的需求...</p>
      </div>

      <div v-else-if="conversation?.stage === 'UI_GENERATING' && !uiHtml" class="placeholder">
        <el-icon size="64" class="is-loading"><Loading /></el-icon>
        <p>正在生成 UI 原型...</p>
        <p class="hint">AI 正在根据需求设计 UI 界面</p>
      </div>

      <div v-else-if="!uiHtml" class="placeholder">
        <el-icon size="64"><Document /></el-icon>
        <p>UI 原型尚未生成</p>
        <p class="hint">请先确认需求理解，系统将自动生成 UI 原型</p>
      </div>

      <div v-else class="iphone-frame">
        <div class="iphone-border">
          <div class="dynamic-island"></div>
          <iframe
            :srcdoc="uiHtml"
            class="preview-frame"
            frameborder="0"
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-presentation"
          ></iframe>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Document, CircleCheck, Refresh, Loading } from '@element-plus/icons-vue'
import { uiPrototypeApi } from '../api/ui-prototype'
import type { ConversationDTO } from '../api/job'

interface Props {
  conversationId?: number
}

const props = defineProps<Props>()

const conversation = ref<ConversationDTO | null>(null)
const uiHtml = ref<string | null>(null)
const isConfirming = ref(false)
const isRegenerating = ref(false)

const fetchConversation = async () => {
  if (!props.conversationId) return
  try {
    const { data } = await uiPrototypeApi.getUIPrototype(props.conversationId)
    conversation.value = data as any
    uiHtml.value = (data as any).htmlContent
  } catch (error) {
    console.error('Failed to fetch conversation:', error)
  }
}

const confirmUIPrototype = async () => {
  if (!props.conversationId) return
  isConfirming.value = true
  try {
    await uiPrototypeApi.confirmUIPrototype(props.conversationId)
    await fetchConversation()
    ElMessage.success('UI 设计已确认，开始生成代码...')
  } catch (error) {
    console.error('Failed to confirm UI prototype:', error)
    ElMessage.error('确认失败，请稍后重试')
  } finally {
    isConfirming.value = false
  }
}

const regenerateUIPrototype = async () => {
  if (!props.conversationId) return
  isRegenerating.value = true
  try {
    await uiPrototypeApi.regenerateUIPrototype(props.conversationId)
    ElMessage.success('UI 原型重新生成中...')
    // 轮询获取新的 UI 原型
    setTimeout(() => {
      fetchConversation()
    }, 2000)
  } catch (error) {
    console.error('Failed to regenerate UI prototype:', error)
    ElMessage.error('重新生成失败，请稍后重试')
  } finally {
    isRegenerating.value = false
  }
}

watch(() => props.conversationId, () => {
  fetchConversation()
}, { immediate: true })

onMounted(() => {
  if (props.conversationId) {
    fetchConversation()
  }
})
</script>

<style scoped>
.ui-prototype-panel {
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

.status-generating {
  font-size: 12px;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-confirmed {
  font-size: 12px;
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 4px;
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

.placeholder {
  text-align: center;
  color: #666;
}

.placeholder .el-icon {
  margin-bottom: 16px;
  opacity: 0.5;
}

.placeholder p {
  margin: 8px 0;
  font-size: 14px;
}

.hint {
  font-size: 12px;
  opacity: 0.7;
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
</style>