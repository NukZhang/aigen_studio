<template>
  <div class="workspace">
    <!-- 左侧对话区 -->
    <div class="chat-panel">
      <div class="chat-header">
        <h2 class="project-title">{{ conversation?.projectName || 'AI 开发者工作区' }}</h2>
        <div class="header-actions">
          <el-button size="small" @click="showConversationList = true">
            <el-icon><List /></el-icon>
            历史对话
          </el-button>
          <el-button size="small" @click="createNewConversation" type="primary">
            <el-icon><Plus /></el-icon>
            新建对话
          </el-button>
        </div>
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
            v-for="stage in stages"
            :key="stage.key"
            class="stage-item"
            :class="{ active: conversation.stage === stage.key, completed: isStageCompleted(stage.key) }"
          >
            <el-icon>
              <CircleCheck v-if="isStageCompleted(stage.key)" />
              <Timer v-else-if="conversation.stage === stage.key" />
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
          <p v-if="jobId">开始新的对话，让 AI 帮助你开发应用</p>
          <p v-else-if="!isNewConversationMode">请先从作业列表中选择一个作业，或创建新的作业</p>
          <p v-else>点击"新建对话"开始创建一个新的应用项目</p>
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

      <!-- 待办事项 -->
      <div class="todo-panel" v-if="conversation && conversation.todos && conversation.todos.length > 0">
        <div class="todo-header">
          <el-icon><List /></el-icon>
          <span>待办事项 ({{ conversation.todos.filter(t => t.status !== 'completed').length }}/{{ conversation.todos.length }})</span>
        </div>
        <div class="todo-items">
          <div
            v-for="todo in conversation.todos"
            :key="todo.id"
            class="todo-item"
            :class="todo.status"
            @click="toggleTodo(todo)"
          >
            <el-icon>
              <CircleCheck v-if="todo.status === 'completed'" />
              <Timer v-else-if="todo.status === 'in_progress'" />
              <Warning v-else />
            </el-icon>
            <div class="todo-content">
              <div class="todo-title">{{ todo.title }}</div>
              <div class="todo-desc">{{ todo.description }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          :placeholder="jobId ? '输入你的需求，让 AI 帮助你开发应用...' : '请先从作业列表中选择一个作业'"
          @keydown.enter.prevent="jobId && sendMessage()"
        />
        <el-button type="primary" @click="sendMessage" :loading="isSending" :disabled="!conversation && !jobId">
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
              <PreviewPanel v-if="conversationId" :conversation-id="conversationId" />
              <div v-else class="placeholder">
                <el-icon size="64"><Monitor /></el-icon>
                <p>预览仅支持对话模式</p>
                <p class="hint">请先创建对话并完成代码生成</p>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="文件" name="files">
            <div class="tab-content files-content">
              <div class="files-split">
                <div class="files-tree">
                  <FileBrowser
                    v-if="jobId || conversationId"
                    :job-id="jobId || undefined"
                    :conversation-id="conversationId || undefined"
                    @file-selected="handleFileSelected"
                  />
                </div>
                <div class="files-editor">
                  <CodeEditor
                    v-if="jobId || conversationId"
                    :job-id="jobId || undefined"
                    :conversation-id="conversationId || undefined"
                    :selected-file="selectedFile"
                  />
                </div>
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
              <div class="placeholder">
                <el-icon size="64"><Reading /></el-icon>
                <p>教程文档暂未实现</p>
                <p class="hint">项目开发指南将在此处展示</p>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus,
  ChatDotRound,
  Tools,
  List,
  CircleCheck,
  CircleClose,
  Timer,
  Warning,
  Promotion,
  Monitor,
  FolderOpened,
  Edit,
  Upload,
  VideoPlay,
  Reading,
  Document
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getConversation, conversationApi, previewApi, type FileNode } from '../api/job'
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

interface Todo {
  id: number
  title: string
  description: string
  status: string
}

interface Conversation {
  id: number
  projectName: string
  messages: Message[]
  todos: Todo[]
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
const jobId = ref<number | null>(null)
const isNewConversationMode = ref(false)
const conversationId = ref<number | null>(null)
const showConversationList = ref(false)
const conversationList = ref<Conversation[]>([])

// 对话阶段配置
const stages = [
  { key: 'NEED_INPUT', label: '需求输入' },
  { key: 'UNDERSTANDING', label: '理解需求' },
  { key: 'UNDERSTANDING_CONFIRMED', label: '确认理解' },
  { key: 'CODE_GENERATING', label: '生成代码' },
  { key: 'READY_TO_START', label: '确认启动' },
  { key: 'SERVICE_STARTING', label: '启动服务' },
  { key: 'PREVIEWING', label: '预览' }
]

// WebSocket 相关状态
const websocket = ref<WebSocket | null>(null)
const useWebSocket = ref(true) // 是否使用 WebSocket 流式响应

onMounted(async () => {
  const jobIdParam = route.query.jobId
  const conversationIdParam = route.query.conversationId

  if (conversationIdParam) {
    // 独立对话模式
    isNewConversationMode.value = true
    conversationId.value = parseInt(conversationIdParam as string)
    await loadNewConversation(conversationId.value)
  } else if (jobIdParam) {
    // 基于 Job 的对话模式
    jobId.value = parseInt(jobIdParam as string)
    await loadConversation(jobId.value)
  }
  // 否则进入空状态，等待用户创建新对话

  // 加载历史对话列表
  await loadConversationList()
})

const startPolling = () => {
  // 清除之前的定时器
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
  }
  
  // 每 3 秒刷新一次对话
  pollingTimer.value = window.setInterval(async () => {
    if (conversationId.value) {
      try {
        const data = await getConversation(conversationId.value)
        // 只在消息数量变化时更新
        if (data.messages.length !== conversation.value?.messages.length) {
          conversation.value = data
          await nextTick()
          scrollToBottom()
        }
      } catch (error) {
        console.error('Failed to poll conversation:', error)
      }
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

onUnmounted(() => {
  // 关闭 WebSocket 连接
  if (websocket.value) {
    websocket.value.close()
    websocket.value = null
  }
  
  // 停止定时刷新
  stopPolling()
})

const loadConversation = async (jobId: number) => {
  try {
    const data = await getConversation(jobId)
    conversation.value = data
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to load conversation:', error)
  }
}

const loadNewConversation = async (id: number) => {
  try {
    const response = await conversationApi.getNewConversation(id)
    conversation.value = response.data
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to load new conversation:', error)
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

    // 加载新对话
    isNewConversationMode.value = true
    conversationId.value = newConversation.id
    await loadNewConversation(newConversation.id)

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

const isStageCompleted = (stageKey: string) => {
  if (!conversation.value) return false

  const currentStageIndex = stages.findIndex(s => s.key === conversation.value?.stage)
  const targetStageIndex = stages.findIndex(s => s.key === stageKey)

  return targetStageIndex < currentStageIndex
}

const sendMessage = async () => {
  console.log('sendMessage called')
  console.log('inputMessage.value:', inputMessage.value)
  console.log('conversation.value:', conversation.value)
  console.log('isSending.value:', isSending.value)

  if (!inputMessage.value.trim()) {
    console.log('Message is empty, not sending')
    return
  }

  if (isSending.value) {
    console.log('Already sending, not sending')
    return
  }

  // 如果是独立对话模式
  if (isNewConversationMode.value && conversationId.value) {
    isSending.value = true
    try {
      await sendMessageToNewConversation(inputMessage.value)
    } finally {
      isSending.value = false
      inputMessage.value = ''
    }
    return
  }

  // 如果 conversation 不存在，尝试加载（基于 Job 的模式）
  if (!conversation.value) {
    console.log('Conversation is null, attempting to load...')
    if (jobId.value) {
      try {
        await loadConversation(jobId.value)
        if (!conversation.value) {
          console.error('Failed to load conversation after retry')
          ElMessage.error('加载对话失败，请稍后重试')
          return
        }
      } catch (error) {
        console.error('Failed to load conversation:', error)
        ElMessage.error('加载对话失败，请稍后重试')
        return
      }
    } else {
      console.error('No jobId available, cannot send message')
      ElMessage.warning('请先创建或选择一个作业')
      return
    }
  }

  isSending.value = true

  // 添加用户消息到对话列表
  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content: inputMessage.value,
    timestamp: new Date().toISOString(),
    senderName: '用户'
  }

  conversation.value.messages.push(userMessage)
  scrollToBottom()

  try {
    if (useWebSocket.value) {
      // 使用 WebSocket 流式响应
      await sendMessageWithWebSocket(inputMessage.value)
    } else {
      // 使用普通的 REST API
      await sendMessageWithRest(userMessage)
    }
  } catch (error) {
    console.error('Failed to send message:', error)
    // 添加错误消息
    const errorMessage: Message = {
      id: Date.now() + 1,
      role: 'assistant',
      content: '发送消息失败，请稍后重试。',
      timestamp: new Date().toISOString(),
      senderName: 'AI 开发者'
    }
    conversation.value.messages.push(errorMessage)
    scrollToBottom()
  } finally {
    isSending.value = false
    inputMessage.value = ''
  }
}

// 使用 WebSocket 发送消息（流式响应）
const sendMessageWithWebSocket = async (messageContent: string) => {
  const aiMessage: Message = {
    id: Date.now() + 1,
    role: 'assistant',
    content: '',
    timestamp: new Date().toISOString(),
    senderName: 'AI 开发者'
  }

  // 添加空的 AI 消息（用于流式显示）
  conversation.value.messages.push(aiMessage)
  scrollToBottom()

  // 创建 WebSocket 连接
  const ws = new WebSocket(`ws://localhost:8080/api/ws/conversation`)
  websocket.value = ws

  ws.onopen = () => {
    console.log('WebSocket connected')
    // 发送消息
    ws.send(messageContent)
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      
      if (data.type === 'text') {
        // 流式文本消息
        aiMessage.content += data.content
        scrollToBottom()
      } else if (data.type === 'finish') {
        // 完成消息
        console.log('Stream finished')
        ws.close()
      } else if (data.type === 'error') {
        // 错误消息
        aiMessage.content += `\n[错误: ${data.error}]`
        scrollToBottom()
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket error:', error)
    aiMessage.content = '连接错误，请稍后重试。'
    scrollToBottom()
  }

  ws.onclose = () => {
    console.log('WebSocket closed')
    isSending.value = false
  }

  // 等待消息发送完成（最多 30 秒）
  await new Promise(resolve => {
    const timeout = setTimeout(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.close()
      }
      resolve(null)
    }, 30000)
    
    ws.addEventListener('close', () => {
      clearTimeout(timeout)
      resolve(null)
    })
  })
}

// 使用 REST API 发送消息（非流式）
const sendMessageWithRest = async (userMessage: Message) => {
  const response = await fetch(`/api/conversations/${conversation.value.id}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(userMessage)
  })

  if (!response.ok) {
    throw new Error('Failed to send message')
  }

  const aiMessage = await response.json()

  // 添加 AI 回复到对话列表
  conversation.value.messages.push(aiMessage)
  scrollToBottom()
}

const toggleTodo = async (todo: Todo) => {
  if (!conversation.value) return

  // 循环切换状态：pending -> in_progress -> completed -> pending
  const statusMap: Record<string, string> = {
    'pending': 'in_progress',
    'in_progress': 'completed',
    'completed': 'pending'
  }

  const newStatus = statusMap[todo.status] || 'pending'

  try {
    // 调用后端 API 更新待办事项状态
    const response = await conversationApi.updateTodoStatus(
      conversation.value.id,
      todo.id,
      newStatus
    )

    // 更新本地待办事项状态
    if (response.data) {
      const updatedTodo = conversation.value.todos.find(t => t.id === todo.id)
      if (updatedTodo) {
        updatedTodo.status = newStatus
      }
    }
  } catch (error) {
    console.error('Failed to update todo status:', error)
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
  isNewConversationMode.value = true
  conversationId.value = conv.id
  await loadNewConversation(conv.id)
}

const getStageLabel = (stage: string) => {
  const stageItem = stages.find(s => s.key === stage)
  return stageItem ? stageItem.label : stage
}
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
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1f1f1f;
  overflow: hidden;
}

/* 特定标签页样式 */
.files-content,
.code-content,
.preview-content {
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
