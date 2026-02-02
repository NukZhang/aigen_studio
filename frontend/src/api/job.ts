import api from './index'

export interface GenerationJob {
  id: number
  requirementId: number
  irDocumentId: number
  jobCode: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
  logOutput: string | null
  gitlabBranch: string | null
  gitlabCommitId: string | null
  gitlabPipelineId: string | null
  errorMessage: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

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

export interface TodoItem {
  id: number
  title: string
  description: string
  status: string
  order: number
  moduleName: string
  featureName: string
}

export interface Conversation {
  id: number
  projectId?: string
  projectName: string
  status: string
  stage?: string
  jobId?: number
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
  todos: TodoItem[]
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

export const jobApi = {
  getAll: (status?: string) => {
    const params = status ? { status } : {}
    return api.get<GenerationJob[]>('/generation-jobs', { params })
  },

  getById: (id: number) => {
    return api.get<GenerationJob>(`/generation-jobs/${id}`)
  },

  getByRequirementId: (requirementId: number) => {
    return api.get<GenerationJob[]>(`/generation-jobs/requirement/${requirementId}`)
  },

  create: (requirementId: number, irDocumentId: number) => {
    return api.post<GenerationJob>('/generation-jobs', null, {
      params: { requirementId, irDocumentId }
    })
  },

  execute: (id: number) => {
    return api.post(`/generation-jobs/${id}/execute`)
  },

  getConversation: (id: number) => {
    return api.get<Conversation>(`/generation-jobs/${id}/conversation`)
  }
}

export const conversationApi = {
  createConversation: (jobId: number) => {
    return api.post<Conversation>('/conversations', null, {
      params: { jobId }
    })
  },

  getConversation: (id: number) => {
    return api.get<Conversation>(`/conversations/${id}`)
  },

  getConversationByJobId: (jobId: number) => {
    return api.get<Conversation>(`/conversations/job/${jobId}`)
  },

  sendMessage: (conversationId: number, message: Message) => {
    return api.post<Message>(`/conversations/${conversationId}/messages`, message)
  },

  getMessages: (conversationId: number) => {
    return api.get<Message[]>(`/conversations/${conversationId}/messages`)
  },

  getTodosByJobId: (jobId: number) => {
    return api.get<TodoItem[]>(`/conversations/job/${jobId}/todos`)
  },

  updateTodoStatus: (jobId: number, todoId: number, status: string) => {
    return api.put<TodoItem>(`/conversations/job/${jobId}/todos/${todoId}`, null, {
      params: { status }
    })
  },

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

export const getConversation = (id: number) => {
  return jobApi.getConversation(id).then(res => res.data)
}

export const fileApi = {
  getFileTree: (jobId: number) => {
    return api.get<FileNode[]>(`/files/job/${jobId}/tree`)
  },

  getFileContent: (jobId: number, filePath: string) => {
    return api.get<FileContent>(`/files/job/${jobId}/content`, {
      params: { filePath }
    })
  },

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
  }
}
