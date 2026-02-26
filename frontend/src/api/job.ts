import api from './index'

export interface Message {
  id: number
  role: string
  content: string
  timestamp: string
  senderName: string
  senderAvatar?: string
  clarificationQuestionId?: string
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
  me2aiContractJson?: string
  me2aiConfirmedAt?: string
  clarificationQuestionsJson?: string
  generatedCodePath?: string
  serviceStatus?: string
  previewUrl?: string
  uiPrototypePath?: string
  uiPrototypeContent?: string
  uiSpecJson?: string
  uiConfirmed?: boolean
  uiConfirmedAt?: string
  implementationPlanJson?: string
  evidenceManifestPath?: string
  gateStatusJson?: string
  errorMessage?: string
  currentQuestionIndex?: number | null
  answeredQuestionIds?: string[]
  createdAt: string
  updatedAt: string
  messages: Message[]
}

export interface UiDesignResponse {
  conversation: Conversation
  uiSpec: Record<string, any>
  prototypePath?: string
  prototypeUrl?: string
}

export interface ImplementationVerifyRequest {
  passed?: boolean
  verifications?: Array<{
    cmd: string
    status: 'PASS' | 'FAIL'
    summary?: string
    logsRef?: string
  }>
  artifacts?: string[]
}

export interface ImplementationVerifyResponse {
  result: 'PASS' | 'FAIL'
  manifestPath: string
  verifications: Array<{
    cmd: string
    status: 'PASS' | 'FAIL'
    summary?: string
    logsRef?: string
  }>
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

  updateConversationTitle: (conversationId: number, projectName: string) => {
    return api.patch<Conversation>(`/conversations/new/${conversationId}/title`, {
      projectName
    })
  },

  confirmUnderstanding: (conversationId: number, confirmed: boolean, feedback?: string) => {
    return api.post<Conversation>(`/conversations/new/${conversationId}/confirm`, {
      confirmed,
      feedback
    })
  },

  parseUnderstanding: (conversationId: number) => {
    return api.post<{ nextAction: string, questions?: any[], contract?: Record<string, any> }>(
      `/conversations/${conversationId}/understanding/parse`
    )
  },

  confirmUnderstandingSdac: (conversationId: number, confirmed: boolean, feedback?: string) => {
    return api.post<Conversation>(`/conversations/${conversationId}/understanding/confirm`, {
      confirmed,
      feedback
    })
  },

  designUi: (conversationId: number) => {
    return api.post<UiDesignResponse>(`/conversations/${conversationId}/ui/design`)
  },

  confirmUiDesign: (conversationId: number) => {
    return api.post<Conversation>(`/conversations/${conversationId}/ui/confirm`)
  },

  createImplementationPlan: (conversationId: number) => {
    return api.post<Record<string, any>>(`/conversations/${conversationId}/implementation/plan`)
  },

  verifyImplementation: (conversationId: number, request: ImplementationVerifyRequest) => {
    return api.post<ImplementationVerifyResponse>(`/conversations/${conversationId}/implementation/verify`, request)
  },

  startPreviewWithEvidence: (conversationId: number, evidenceRef: string) => {
    return api.post<PreviewStatus>(`/conversations/${conversationId}/preview/start`, {
      evidenceRef
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

export interface Model {
  id: string
  name: string
  description: string
  isDefault: boolean
  type: string
}

export const modelApi = {
  getAvailableModels: () => {
    return api.get<Model[]>('/models')
  }
}
