<template>
  <div class="workspace">
    <!-- 左侧对话区 -->
    <div class="chat-panel">
      <div class="chat-header">
        <h2 class="project-title">{{ conversation?.projectName || 'AI 开发者工作区' }}</h2>
      </div>

      <!-- 历史对话列表抽屉 -->
      <el-drawer v-model="showConversationList" title="历史对话" size="300px">
        <div class="conversation-list">
          <div v-if="conversationList.length === 0" class="empty-list">
            <el-icon size="48"><ChatDotRound /></el-icon>
            <p>暂无历史对话</p>
          </div>
          <div v-else>
            <div
              v-for="conv in conversationList"
              :key="conv.id"
              class="conversation-item"
              :class="{ active: conversation?.id === conv.id }"
              @click="selectConversation(conv)"
            >
              <div class="conv-title">{{ conv.projectName }}</div>
              <div class="conv-meta">
                <span class="conv-stage">{{ getStageLabel(conv.stage) }}</span>
                <span class="conv-time">{{ formatTime(conv.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-drawer>

      <!-- 对话阶段指示器 -->
      <div class="stage-indicator" v-if="conversation?.stage">
        <div class="stage-progress">
          <div
            v-for="stage in displayStages"
            :key="stage.key"
            class="stage-item"
            :class="{
              active: isStageActive(stage.key),
              completed: isStageCompleted(stage.key),
              failed: isStageFailed(stage.key)
            }"
          >
            <el-icon>
              <CircleCheck v-if="isStageCompleted(stage.key)" />
              <Timer v-else-if="isStageActive(stage.key)" />
              <CircleClose v-else />
            </el-icon>
            <span class="stage-label">{{ stage.label }}</span>
          </div>
        </div>
      </div>

      <!-- 对话历史 -->
      <div class="chat-history" ref="chatHistoryRef">
        <div v-if="!conversation || conversation.messages.length === 0" class="empty-state">
          <el-icon size="48"><ChatDotRound /></el-icon>
          <p>点击"新建对话"开始创建一个新的应用项目</p>
        </div>

        <div v-else class="messages">
          <div
            v-for="message in conversation.messages"
            :key="message.id"
            class="message"
            :class="message.role"
          >
            <div class="message-header">
              <span class="sender">{{ message.senderName }}</span>
              <span class="timestamp">{{ formatTime(message.timestamp) }}</span>
            </div>
            <div class="message-content">
              <div class="content-text" v-html="renderMarkdown(message.content)"></div>
              
              <!-- 工具调用展示 -->
              <div v-if="message.toolCalls && message.toolCalls.length > 0" class="tool-calls">
                <div
                  v-for="toolCall in message.toolCalls"
                  :key="toolCall.id"
                  class="tool-call"
                >
                  <div class="tool-call-header">
                    <el-icon><Tools /></el-icon>
                    <span class="tool-name">{{ toolCall.toolName }}</span>
                    <span class="tool-status" :class="toolCall.status">{{ toolCall.status }}</span>
                  </div>
                  <div class="tool-arguments" v-if="toolCall.arguments">
                    <pre>{{ toolCall.arguments }}</pre>
                  </div>
                  <div class="tool-result" v-if="toolCall.result">
                    <div class="result-header">结果：</div>
                    <pre>{{ toolCall.result }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 理解确认界面 -->
      <div class="understanding-confirm-panel" v-if="conversation?.stage === 'UNDERSTANDING_CONFIRMED' && conversation.aiUnderstanding">
        <div class="confirm-header">
          <el-icon><Document /></el-icon>
          <span>需求理解确认</span>
        </div>
        <div class="confirm-content">
          <h4>我对您需求的理解：</h4>
          <div class="understanding-text" v-html="renderMarkdown(conversation.aiUnderstanding)"></div>
        </div>
        <div class="confirm-actions">
          <el-button type="success" @click="confirmUnderstanding(true)" :loading="isConfirming">
            <el-icon><CircleCheck /></el-icon>
            确认，开始生成代码
          </el-button>
          <el-button type="danger" @click="confirmUnderstanding(false)" :loading="isConfirming">
            <el-icon><CircleClose /></el-icon>
            不正确，需要修改
          </el-button>
        </div>
      </div>

      <!-- 代码生成完成确认启动 -->
      <div class="start-confirm-panel" v-if="conversation?.stage === 'READY_TO_START'">
        <div class="confirm-header">
          <el-icon><Monitor /></el-icon>
          <span>代码生成完成</span>
        </div>
        <div class="confirm-content">
          <p>代码已生成，点击下方按钮启动预览服务。</p>
        </div>
        <div class="confirm-actions">
          <el-button type="primary" @click="startPreviewFromConfirm" :loading="isStartingPreview">
            <el-icon><VideoPlay /></el-icon>
            确认启动
          </el-button>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="输入你的需求，让 AI 帮助你开发应用..."
          @keydown.enter.prevent="conversationId && sendMessage()"
        />
        <el-button type="primary" @click="sendMessage" :loading="isSending" :disabled="!conversationId">
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>

    <!-- 右侧工作区 -->
    <div class="developer-panel">
      <div class="panel-header">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="预览" name="preview">
            <div class="tab-content preview-content">
              <PreviewPanel :conversation-id="conversationId || undefined" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="文件" name="files">
            <div class="tab-content files-content">
              <div v-if="canAccessFiles" class="files-split">
                <div class="files-tree">
                  <FileBrowser
                    :conversation-id="canAccessConversationFiles ? conversationId || undefined : undefined"
                    @file-selected="handleFileSelected"
                  />
                </div>
                <div class="files-editor">
                  <CodeEditor
                    :conversation-id="canAccessConversationFiles ? conversationId || undefined : undefined"
                    :selected-file="selectedFile"
                  />
                </div>
              </div>
              <div v-else class="placeholder">
                <el-icon size="64"><FolderOpened /></el-icon>
                <p v-if="conversationId">代码生成后可查看文件</p>
                <p v-else>请先创建对话</p>
                <p v-if="conversationId && conversation?.stage" class="hint">
                  当前阶段：{{ getStageLabel(conversation.stage) }}
                </p>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="发布" name="publish">
            <div class="tab-content publish-content">
              <div class="placeholder">
                <el-icon size="64"><Upload /></el-icon>
                <p>发布功能暂未实现</p>
                <p class="hint">代码将发布到 GitLab</p>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="脚本" name="scripts">
            <div class="tab-content scripts-content">
              <div class="placeholder">
                <el-icon size="64"><VideoPlay /></el-icon>
                <p>脚本执行暂未实现</p>
                <p class="hint">构建和运行脚本将在此处执行</p>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="教程" name="tutorial">
            <div class="tab-content tutorial-content">
              <div class="tutorial-view">
                <div class="tutorial-sidebar">
                  <div v-if="tutorialHeadings.length === 0" class="no-headings">
                    <span>暂无标题</span>
                  </div>
                  <el-tree
                    v-else
                    :data="tutorialHeadings"
                    :props="headingTreeProps"
                    node-key="anchor"
                    :expand-on-click-node="false"
                    @node-click="handleHeadingClick"
                    :highlight-current="true"
                    :default-expand-all="true"
                  >
                    <template #default="{ node, data }">
                      <span class="heading-node" :class="'heading-level-' + data.level">
                        <span>{{ node.label }}</span>
                      </span>
                    </template>
                  </el-tree>
                </div>
                <div class="tutorial-preview" ref="tutorialPreviewRef">
                  <div v-if="tutorialLoading" class="loading-container">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>加载中...</span>
                  </div>
                  <div v-else-if="currentTutorialContent" class="markdown-content" v-html="renderedTutorialContent"></div>
                  <div v-else class="empty-container">
                    <el-icon><Reading /></el-icon>
                    <span>加载中...</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Tools,
  CircleCheck,
  CircleClose,
  Timer,
  Promotion,
  Monitor,
  FolderOpened,
  Upload,
  VideoPlay,
  Reading,
  Document
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import { conversationApi, previewApi, type FileNode } from '../api/job'
import { getTutorialTree, getTutorialContent, type TutorialNode } from '../api/tutorial'
import FileBrowser from './FileBrowser.vue'
import CodeEditor from './CodeEditor.vue'
import PreviewPanel from './PreviewPanel.vue'

interface Message {
  id: number
  role: string
  content: string
  timestamp: string
  senderName: string
  toolCalls?: ToolCall[]
}

interface ToolCall {
  id: number
  toolName: string
  arguments: string
  result?: string
  status: string
}

interface Conversation {
  id: number
  projectName: string
  stage?: string
  generatedCodePath?: string
  messages: Message[]
}

const route = useRoute()
const router = useRouter()
const conversation = ref<Conversation | null>(null)
const inputMessage = ref('')
const isSending = ref(false)
const isConfirming = ref(false)
const isStartingPreview = ref(false)
const activeTab = ref('preview')
const chatHistoryRef = ref<HTMLElement | null>(null)
const selectedFile = ref<FileNode | null>(null)
const conversationId = ref<number | null>(null)
const showConversationList = ref(false)
const conversationList = ref<Conversation[]>([])
const tutorialTree = ref<TutorialNode[]>([])
const tutorialHeadings = ref<any[]>([])
const currentTutorialContent = ref('')
const tutorialLoading = ref(false)
const tutorialPreviewRef = ref<HTMLElement | null>(null)
const tutorialTreeProps = {
  children: 'children',
  label: 'name'
}
const headingTreeProps = {
  children: 'children',
  label: 'text'
}
const autoRefreshTimer = ref<number | null>(null)
const isLoadingConversation = ref(false)
const AUTO_REFRESH_INTERVAL = 3000
const conversationFileStages = new Set([
  'CODE_GENERATING',
  'READY_TO_START',
  'SERVICE_STARTING',
  'PREVIEWING',
  'COMPLETED',
  'FAILED'
])
const canAccessConversationFiles = computed(() => {
  if (!conversationId.value || !conversation.value) return false
  if (!conversation.value.generatedCodePath) return false
  const stage = conversation.value.stage
  return !!stage && conversationFileStages.has(stage)
})
const canAccessFiles = computed(() => canAccessConversationFiles.value)

// 对话阶段配置（生成流程）
const generationStages = [
  { key: 'NEED_INPUT', label: '需求输入' },
  { key: 'UNDERSTANDING', label: '理解需求' },
  { key: 'UNDERSTANDING_CONFIRMED', label: '确认理解' },
  { key: 'CODE_GENERATING', label: '生成代码' },
  { key: 'READY_TO_START', label: '确认启动' }
]

const generationStageKeys = generationStages.map(stage => stage.key)
const stageLabelMap: Record<string, string> = {
  NEED_INPUT: '需求输入',
  UNDERSTANDING: '理解需求',
  UNDERSTANDING_CONFIRMED: '确认理解',
  CODE_GENERATING: '生成代码',
  READY_TO_START: '确认启动',
  SERVICE_STARTING: '启动服务',
  PREVIEWING: '预览',
  COMPLETED: '生成完成',
  FAILED: '生成失败'
}

const displayStages = computed(() => {
  const stage = conversation.value?.stage
  if (stage === 'COMPLETED' || stage === 'FAILED') {
    const terminalLabel = stage === 'COMPLETED' ? '生成完成' : '生成失败'
    return generationStages.map((item, index) =>
      index === generationStages.length - 1
        ? { ...item, label: terminalLabel }
        : item
    )
  }
  return generationStages
})

const normalizeStageForProgress = (stage?: string | null) => {
  if (!stage) return null
  if (stage === 'SERVICE_STARTING' || stage === 'PREVIEWING' || stage === 'COMPLETED' || stage === 'FAILED') {
    return 'READY_TO_START'
  }
  return stage
}

const isStageCompleted = (stageKey: string) => {
  if (!conversation.value?.stage) return false
  const normalizedStage = normalizeStageForProgress(conversation.value.stage)
  if (!normalizedStage) return false

  const currentIndex = generationStageKeys.indexOf(normalizedStage)
  const targetIndex = generationStageKeys.indexOf(stageKey)
  if (currentIndex === -1 || targetIndex === -1) return false

  const stage = conversation.value.stage
  if (stage === 'SERVICE_STARTING' || stage === 'PREVIEWING' || stage === 'COMPLETED') {
    return targetIndex <= currentIndex
  }
  return targetIndex < currentIndex
}

const isStageActive = (stageKey: string) => {
  if (!conversation.value?.stage) return false
  const stage = conversation.value.stage
  if (stage === 'SERVICE_STARTING' || stage === 'PREVIEWING' || stage === 'COMPLETED' || stage === 'FAILED') {
    return false
  }
  const normalizedStage = normalizeStageForProgress(stage)
  return normalizedStage === stageKey
}

const isStageFailed = (stageKey: string) => {
  return conversation.value?.stage === 'FAILED' && stageKey === 'READY_TO_START'
}


onMounted(async () => {
  const conversationIdParam = route.query.conversationId

  if (conversationIdParam) {
    // 独立对话模式
    conversationId.value = parseInt(conversationIdParam as string)
    await loadNewConversation(conversationId.value)
  }
  // 否则进入空状态，等待用户创建新对话

  // 加载历史对话列表
  await loadConversationList()

  // 加载教程目录树
  await loadTutorialTree()
})

const clearActionQuery = () => {
  if (!('action' in route.query)) {
    return
  }
  const nextQuery = { ...route.query } as Record<string, string | string[]>
  delete nextQuery.action
  router.replace({
    path: route.path,
    query: nextQuery
  })
}

watch(
  () => route.query.action,
  async (action) => {
    const actionValue = Array.isArray(action) ? action[0] : action
    if (!actionValue) return

    if (actionValue === 'history') {
      showConversationList.value = true
      clearActionQuery()
      return
    }

    if (actionValue === 'new') {
      await createNewConversation()
      clearActionQuery()
    }
  },
  { immediate: true }
)


const isChatNearBottom = () => {
  if (!chatHistoryRef.value) return true
  const { scrollTop, scrollHeight, clientHeight } = chatHistoryRef.value
  return scrollHeight - scrollTop - clientHeight < 80
}

const loadNewConversation = async (id: number, autoScroll: boolean = true) => {
  if (isLoadingConversation.value) return
  isLoadingConversation.value = true
  try {
    const nearBottom = autoScroll && isChatNearBottom()
    const previousCount = conversation.value?.messages.length ?? 0
    const response = await conversationApi.getNewConversation(id)
    conversation.value = response.data
    await nextTick()
    if (autoScroll) {
      const newCount = conversation.value?.messages.length ?? 0
      if (nearBottom || (previousCount === 0 && newCount > 0)) {
        scrollToBottom()
      }
    }
  } catch (error) {
    console.error('Failed to load new conversation:', error)
  } finally {
    isLoadingConversation.value = false
  }
}

const createNewConversation = async () => {
  try {
    // 直接创建新对话，不需要弹出对话框
    const response = await conversationApi.createNewConversation()
    const newConversation = response.data

    // 更新路由，跳转到新对话
    router.push({
      path: '/workspace',
      query: { conversationId: newConversation.id }
    })

    conversationId.value = newConversation.id
    await loadNewConversation(newConversation.id)
    await loadConversationList()

    ElMessage.success('已创建新对话')
  } catch (error) {
    console.error('Failed to create new conversation:', error)
    ElMessage.error('创建对话失败，请稍后重试')
  }
}

const sendMessageToNewConversation = async (content: string) => {
  if (!conversationId.value) return

  isSending.value = true
  try {
    const userMessage: Message = {
      id: Date.now(),
      role: 'user',
      content,
      timestamp: new Date().toISOString(),
      senderName: '用户'
    }

    // 先显示用户消息
    conversation.value?.messages.push(userMessage)
    await nextTick()
    scrollToBottom()

    const response = await conversationApi.sendMessageToNewConversation(conversationId.value, userMessage)
    const aiMessage = response.data

    // 显示 AI 回复
    conversation.value?.messages.push(aiMessage)
    await nextTick()
    scrollToBottom()

    // 如果阶段改变，重新加载对话以获取最新状态
    await loadNewConversation(conversationId.value)
    
  } catch (error) {
    console.error('Failed to send message:', error)
    ElMessage.error('发送消息失败，请稍后重试')
  } finally {
    isSending.value = false
  }
}

const confirmUnderstanding = async (confirmed: boolean) => {
  if (!conversationId.value) return

  isConfirming.value = true

  try {
    await conversationApi.confirmUnderstanding(conversationId.value, confirmed)

    // 重新加载对话
    await loadNewConversation(conversationId.value)

    if (confirmed) {
      ElMessage.success('已确认，开始生成代码...')
    } else {
      ElMessage.info('请告诉我需要修改的地方')
    }

    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to confirm understanding:', error)
    ElMessage.error('确认失败，请稍后重试')
  } finally {
    isConfirming.value = false
  }
}

const startPreviewFromConfirm = async () => {
  if (!conversationId.value) return
  isStartingPreview.value = true
  try {
    await previewApi.startPreview(conversationId.value)
    await loadNewConversation(conversationId.value)
    activeTab.value = 'preview'
    ElMessage.success('预览已启动')
  } catch (error) {
    console.error('Failed to start preview:', error)
    ElMessage.error('启动预览失败')
  } finally {
    isStartingPreview.value = false
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim()) {
    return
  }

  if (isSending.value) {
    return
  }

  if (!conversationId.value) {
    ElMessage.warning('请先创建对话')
    return
  }

  isSending.value = true
  try {
    await sendMessageToNewConversation(inputMessage.value)
  } finally {
    isSending.value = false
    inputMessage.value = ''
  }
}

const handleFileSelected = (node: FileNode) => {
  selectedFile.value = node
  if (!node.directory) {
    activeTab.value = 'files'
  }
}

const renderMarkdown = (content: string) => {
  try {
    return marked(content)
  } catch (error) {
    return content
  }
}

const formatTime = (timestamp: string) => {
  try {
    const date = new Date(timestamp)
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch (error) {
    return ''
  }
}

const scrollToBottom = () => {
  if (chatHistoryRef.value) {
    chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
  }
}

const loadConversationList = async () => {
  try {
    const response = await conversationApi.getActiveConversations('user')
    conversationList.value = response.data
  } catch (error) {
    console.error('Failed to load conversation list:', error)
  }
}

const selectConversation = async (conv: Conversation) => {
  showConversationList.value = false

  if (conv.id === conversationId.value) {
    return // 已经是当前对话
  }

  // 更新路由
  router.push({
    path: '/workspace',
    query: { conversationId: conv.id }
  })

  // 加载对话
  conversationId.value = conv.id
  await loadNewConversation(conv.id)
}

const getStageLabel = (stage: string) => {
  return stageLabelMap[stage] || stage
}

const loadTutorialTree = async () => {
  try {
    const { data } = await getTutorialTree()
    tutorialTree.value = data

    // 自动加载第一个教程文件
    const firstFile = findFirstTutorialFile(data)
    if (firstFile) {
      await loadTutorialContent(firstFile.path)
    }
  } catch (error) {
    ElMessage.error('加载教程目录失败')
  }
}

const findFirstTutorialFile = (nodes: any[]): any | null => {
  for (const node of nodes) {
    if (node.type === 'file') {
      return node
    }
    if (node.children && Array.isArray(node.children) && node.children.length > 0) {
      const found = findFirstTutorialFile(node.children)
      if (found) {
        return found
      }
    }
  }
  return null
}

const loadTutorialContent = async (path: string) => {
  tutorialLoading.value = true
  try {
    const { data: contentData } = await getTutorialContent(path)
    currentTutorialContent.value = contentData.content
    tutorialHeadings.value = contentData.headings || []
  } catch (error) {
    ElMessage.error('加载教程内容失败')
    currentTutorialContent.value = ''
    tutorialHeadings.value = []
  } finally {
    tutorialLoading.value = false
  }
}

const handleHeadingClick = (data: any) => {
  const anchor = data.anchor
  const element = document.getElementById(anchor)
  if (element && tutorialPreviewRef.value) {
    const previewRect = tutorialPreviewRef.value.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const scrollTop = elementRect.top - previewRect.top - 20 // 20px padding
    tutorialPreviewRef.value.scrollTop += scrollTop
  }
}

const renderedTutorialContent = computed(() => {
  if (!currentTutorialContent.value) return ''
  const html = marked(currentTutorialContent.value)
  // 为标题添加锚点 ID
  return html.replace(/<h([1-6])>(.*?)<\/h\1>/g, (match, level, text) => {
    const anchor = generateAnchorFromText(text)
    return `<h${level} id="${anchor}">${text}</h${level}>`
  })
})

const generateAnchorFromText = (text: string) => {
  // 移除 HTML 标签
  const plainText = text.replace(/<[^>]*>/g, '')
  // 转小写，移除特殊字符，空格替换为连字符
  return plainText.toLowerCase()
          .replace(/[^\w\s\u4e00-\u9fa5]/g, '')
          .replace(/\s+/g, '-')
}

// 监听教程目录树变化，自动加载第一个文件
watch(tutorialTree, (newTree) => {
  if (newTree && newTree.length > 0 && !currentTutorialContent.value) {
    const firstFile = findFirstTutorialFile(newTree)
    if (firstFile) {
      loadTutorialContent(firstFile.path)
    }
  }
})

const stopAutoRefresh = () => {
  if (autoRefreshTimer.value !== null) {
    window.clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (!conversationId.value) return
  autoRefreshTimer.value = window.setInterval(() => {
    if (conversationId.value) {
      loadNewConversation(conversationId.value, true)
    }
  }, AUTO_REFRESH_INTERVAL)
}

watch(conversationId, () => {
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  background-color: #1a1a1a;
  color: #e0e0e0;
}

/* 左侧对话区 */
.chat-panel {
  flex: 0 0 40%;
  min-width: 420px;
  max-width: 40%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #3a3a3a;
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid #3a3a3a;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.empty-state .el-icon {
  margin-bottom: 16px;
  color: #444;
}

.empty-state p {
  margin: 8px 0;
  font-size: 14px;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  max-width: 85%;
}

.message.user {
  align-self: flex-end;
}

.message.assistant {
  align-self: flex-start;
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: #888;
}

.sender {
  font-weight: 500;
}

.timestamp {
  opacity: 0.7;
}

.message-content {
  background-color: #2a2a2a;
  border-radius: 12px;
  padding: 12px 16px;
  border: 1px solid #3a3a3a;
}

.message.user .message-content {
  background-color: #667eea;
  border-color: #667eea;
}

.content-text {
  line-height: 1.6;
  max-width: 100%;
  overflow-wrap: break-word;
  word-wrap: break-word;
}

.content-text :deep(p) {
  margin: 8px 0;
  max-width: 100%;
  overflow-wrap: break-word;
}

.content-text :deep(code) {
  background-color: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  max-width: 100%;
  word-break: break-all;
  overflow-wrap: break-word;
}

.content-text :deep(pre) {
  background-color: rgba(0, 0, 0, 0.3);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  margin: 12px 0;
  max-width: 100%;
  word-wrap: break-word;
}

.content-text :deep(pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 工具调用 */
.tool-calls {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-call {
  background-color: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  padding: 12px;
  border-left: 3px solid #667eea;
}

.tool-call-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.tool-name {
  font-weight: 600;
  color: #667eea;
}

.tool-status {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.tool-status.success {
  background-color: rgba(103, 194, 58, 0.2);
  color: #67c23a;
}

.tool-status.failed {
  background-color: rgba(245, 108, 108, 0.2);
  color: #f56c6c;
}

.tool-status.running {
  background-color: rgba(230, 162, 60, 0.2);
  color: #e6a23c;
}

.tool-arguments,
.tool-result {
  margin-top: 8px;
}

.result-header {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
}

.tool-arguments pre,
.tool-result pre {
  background-color: rgba(0, 0, 0, 0.5);
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  margin: 0;
}

/* 待办事项 */
.todo-panel {
  padding: 16px 20px;
  border-top: 1px solid #3a3a3a;
  background-color: #1f1f1f;
}

.todo-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
  color: #888;
}

.todo-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 150px;
  overflow-y: auto;
}

.todo-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.todo-item:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.todo-item.completed .todo-title {
  text-decoration: line-through;
  color: #666;
}

.todo-item.in_progress {
  border-left: 2px solid #e6a23c;
}

.todo-content {
  flex: 1;
}

.todo-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 4px;
}

.todo-desc {
  font-size: 11px;
  color: #666;
  line-height: 1.4;
}

/* 对话阶段指示器 */
.stage-indicator {
  padding: 12px 20px;
  border-bottom: 1px solid #3a3a3a;
  background-color: #1f1f1f;
}

.stage-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
}

.stage-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  color: #666;
  background-color: #2a2a2a;
  white-space: nowrap;
  transition: all 0.3s;
}

.stage-item.active {
  background-color: rgba(102, 126, 234, 0.2);
  color: #667eea;
  border: 1px solid #667eea;
}

.stage-item.completed {
  background-color: rgba(103, 194, 58, 0.2);
  color: #67c23a;
}

.stage-item.failed {
  background-color: rgba(245, 108, 108, 0.2);
  color: #f56c6c;
  border: 1px solid #f56c6c;
}

.stage-label {
  font-weight: 500;
}

/* 理解确认界面 */
.understanding-confirm-panel,
.start-confirm-panel {
  padding: 16px 20px;
  border-top: 1px solid #3a3a3a;
  background-color: #1f1f1f;
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

.confirm-content {
  margin-bottom: 16px;
}

.confirm-content h4 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #888;
}

.understanding-text {
  background-color: #2a2a2a;
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #e0e0e0;
  max-height: 200px;
  overflow-y: auto;
}

.confirm-actions {
  display: flex;
  gap: 12px;
}

/* 输入框 */
.chat-input {
  padding: 16px 20px;
  border-top: 1px solid #3a3a3a;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input :deep(.el-textarea__inner) {
  background-color: #2a2a2a;
  border-color: #3a3a3a;
  color: #e0e0e0;
  font-family: inherit;
}

.chat-input :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
}

/* 右侧工作区 */
.developer-panel {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  height: 100%;
}

.panel-header :deep(.el-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.panel-header :deep(.el-tab-pane) {
  height: 100%;
}

.tab-content {
  height: 100%;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1f1f1f;
  overflow: hidden;
}

/* 特定标签页样式 */
.files-content,
.code-content,
.preview-content,
.tutorial-content {
  width: 100%;
  flex: 1;
  align-items: stretch;
  justify-content: flex-start;
}

.files-split {
  display: flex;
  gap: 12px;
  height: 100%;
  width: 100%;
  padding: 12px;
  box-sizing: border-box;
}

.files-tree {
  flex: 0 0 32%;
  min-width: 260px;
  border-right: 1px solid #2f2f2f;
  padding-right: 12px;
  box-sizing: border-box;
  overflow: hidden;
}

.files-editor {
  flex: 1 1 auto;
  min-width: 0;
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

.placeholder .hint {
  font-size: 12px;
  opacity: 0.7;
}

.tutorial-view {
  display: flex;
  width: 100%;
  height: 100%;
}

.tutorial-sidebar {
  width: 280px;
  background-color: #2a2a2a;
  border-right: 1px solid #3a3a3a;
  overflow-y: auto;
  padding: 16px;
}

.tutorial-sidebar :deep(.el-tree) {
  background: transparent;
  color: #e0e0e0;
}

.tutorial-sidebar :deep(.el-tree-node__content) {
  background: transparent;
  color: #e0e0e0;
}

.tutorial-sidebar :deep(.el-tree-node__content:hover) {
  background-color: #3a3a3a;
}

.tutorial-sidebar :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: #4a4a4a;
  color: #fff;
}

.tutorial-sidebar :deep(.el-tree-node__expand-icon) {
  color: #999;
}

.tutorial-sidebar :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}

.tutorial-preview {
  flex: 1;
  background-color: #1f1f1f;
  overflow-y: auto;
  padding: 32px;
}

.tutorial-sidebar .no-headings {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  font-size: 14px;
}

.tutorial-sidebar .heading-node {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #e0e0e0;
  cursor: pointer;
  transition: color 0.2s;
  padding: 4px 8px;
  border-radius: 4px;
}

.tutorial-sidebar .heading-node:hover {
  background-color: #3a3a3a;
  color: #fff;
}

.tutorial-sidebar .heading-level-1 {
  font-size: 15px;
  font-weight: 600;
}

.tutorial-sidebar .heading-level-2 {
  padding-left: 20px;
  font-size: 14px;
}

.tutorial-sidebar .heading-level-3 {
  padding-left: 36px;
  font-size: 13px;
  color: #ccc;
}

.tutorial-sidebar .heading-level-4 {
  padding-left: 52px;
  font-size: 13px;
  color: #bbb;
}

.tutorial-sidebar .heading-level-5 {
  padding-left: 68px;
  font-size: 12px;
  color: #aaa;
}

.tutorial-sidebar .heading-level-6 {
  padding-left: 84px;
  font-size: 12px;
  color: #aaa;
}

.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  gap: 12px;
}

.loading-container .el-icon,
.empty-container .el-icon {
  font-size: 48px;
}

.markdown-content {
  max-width: 900px;
  margin: 0 auto;
  line-height: 1.8;
  color: #e0e0e0;
}

.markdown-content :deep(h1) {
  font-size: 28px;
  font-weight: 700;
  margin-top: 0;
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid #3a3a3a;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  margin-top: 32px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #3a3a3a;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(h3) {
  font-size: 20px;
  font-weight: 600;
  margin-top: 24px;
  margin-bottom: 12px;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(h4) {
  font-size: 18px;
  font-weight: 600;
  margin-top: 20px;
  margin-bottom: 10px;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(h5) {
  font-size: 16px;
  font-weight: 600;
  margin-top: 18px;
  margin-bottom: 8px;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(h6) {
  font-size: 14px;
  font-weight: 600;
  margin-top: 16px;
  margin-bottom: 8px;
  color: #fff;
  scroll-margin-top: 20px;
}

.markdown-content :deep(p) {
  margin-bottom: 16px;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-bottom: 16px;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin-bottom: 8px;
}

.markdown-content :deep(code) {
  background: #2a2a2a;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  color: #d63384;
}

.markdown-content :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin-bottom: 16px;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #667eea;
  padding-left: 16px;
  margin: 16px 0;
  color: #999;
  background: #2a2a2a;
  padding: 12px 16px;
  border-radius: 4px;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #3a3a3a;
  padding: 12px;
  text-align: left;
}

.markdown-content :deep(th) {
  background: #2a2a2a;
  font-weight: 600;
}

.markdown-content :deep(a) {
  color: #667eea;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 16px 0;
}

/* 历史对话列表 */
.conversation-list {
  padding: 12px;
}

.empty-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #999;
}

.empty-list .el-icon {
  margin-bottom: 12px;
}

.conversation-item {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: #2a2a2a;
  border: 1px solid #3a3a3a;
}

.conversation-item:hover {
  background-color: #3a3a3a;
  border-color: #4a4a4a;
}

.conversation-item.active {
  background-color: rgba(102, 126, 234, 0.2);
  border-color: #667eea;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  color: #e0e0e0;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #888;
}

.conv-stage {
  padding: 2px 6px;
  border-radius: 4px;
  background-color: rgba(102, 126, 234, 0.2);
  color: #667eea;
}

.conv-time {
  opacity: 0.7;
}
</style>
