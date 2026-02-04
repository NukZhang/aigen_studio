import api from './index'

export interface Message {
  id: number
  role: string
  content: string
  timestamp: string
  senderName: string
  senderAvatar?: string
  toolCalls?: ToolCall[]
}

export interface ToolCall {
  id: number
  toolName: string
  arguments: string
  result?: string
  status: string
  icon?: string
}

export interface Conversation {
  id: number
  projectName: string
  status: string
  stage?: string
  userRequirement?: string
  aiUnderstanding?: string
  understandingConfirmed?: boolean
  generatedCodePath?: string
  serviceStatus?: string
  previewUrl?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
  messages: Message[]
}

export interface FileNode {
  name: string
  path: string
  directory: boolean
  type: string
  size: number
  children?: FileNode[]
}

export interface FileContent {
  success: boolean
  content?: string
  filePath?: string
  error?: string
}

export interface FileOperationResult {
  success: boolean
  error?: string
  filePath?: string
  path?: string
  from?: string
  to?: string
}

export interface PreviewStatus {
  conversationId: number
  running: boolean
  frontendRunning: boolean
  backendRunning: boolean
  frontendPort?: number
  backendPort?: number
  frontendUrl?: string
  backendUrl?: string
  message?: string
}

export const conversationApi = {
  // ==================== 独立对话流程 API ====================

  createNewConversation: (createdBy: string = 'user') => {
    return api.post<Conversation>('/conversations/new', null, {
      params: { createdBy }
    })
  },

  getNewConversation: (id: number) => {
    return api.get<Conversation>(`/conversations/new/${id}`)
  },

  getActiveConversations: (createdBy: string = 'user') => {
    return api.get<Conversation[]>('/conversations/active', {
      params: { createdBy }
    })
  },

  sendMessageToNewConversation: (conversationId: number, message: Message) => {
    return api.post<Message>(`/conversations/new/${conversationId}/messages`, message)
  },

  confirmUnderstanding: (conversationId: number, confirmed: boolean, feedback?: string) => {
    return api.post<Conversation>(`/conversations/new/${conversationId}/confirm`, {
      confirmed,
      feedback
    })
  }
}

export const fileApi = {
  getConversationFileTree: (conversationId: number) => {
    return api.get<FileNode[]>(`/files/conversation/${conversationId}/tree`)
  },

  getConversationFileContent: (conversationId: number, filePath: string) => {
    return api.get<FileContent>(`/files/conversation/${conversationId}/content`, {
      params: { filePath }
    })
  },

  saveConversationFileContent: (conversationId: number, filePath: string, content: string) => {
    return api.put<FileContent>(`/files/conversation/${conversationId}/content`, {
      filePath,
      content
    })
  },

  createConversationFile: (conversationId: number, filePath: string) => {
    return api.post<FileOperationResult>(`/files/conversation/${conversationId}/create`, { filePath })
  },

  createConversationDirectory: (conversationId: number, path: string) => {
    return api.post<FileOperationResult>(`/files/conversation/${conversationId}/mkdir`, { path })
  },

  renameConversationPath: (conversationId: number, from: string, to: string) => {
    return api.post<FileOperationResult>(`/files/conversation/${conversationId}/rename`, { from, to })
  },

  deleteConversationPath: (conversationId: number, path: string) => {
    return api.post<FileOperationResult>(`/files/conversation/${conversationId}/delete`, { path })
  },

  uploadConversationFile: (conversationId: number, file: File, targetDir?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    if (targetDir) {
      formData.append('targetDir', targetDir)
    }
    return api.post<FileOperationResult>(`/files/conversation/${conversationId}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export const previewApi = {
  startPreview: (conversationId: number) => {
    return api.post<PreviewStatus>(`/preview/conversation/${conversationId}/start`)
  },

  stopPreview: (conversationId: number) => {
    return api.post<PreviewStatus>(`/preview/conversation/${conversationId}/stop`)
  },

  restartPreview: (conversationId: number) => {
    return api.post<PreviewStatus>(`/preview/conversation/${conversationId}/restart`)
  },

  getStatus: (conversationId: number) => {
    return api.get<PreviewStatus>(`/preview/conversation/${conversationId}/status`)
  },

  getLogs: (conversationId: number, lines: number = 100) => {
    return api.get<{ conversationId: number, logs: string[], count: number }>(
      `/preview/conversation/${conversationId}/logs`,
      { params: { lines } }
    )
  }
}
