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
  }
}