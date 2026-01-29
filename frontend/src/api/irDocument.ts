import api from './index'

export interface IRDocument {
  id: number
  requirementId: number
  content: string
  status: 'DRAFT' | 'VALID' | 'INVALID' | 'LOCKED'
  validationErrors: string | null
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export const irDocumentApi = {
  getByRequirementId: (requirementId: number) => {
    return api.get<IRDocument>(`/ir-documents/requirement/${requirementId}`)
  },

  create: (requirementId: number, content: string) => {
    return api.post<IRDocument>('/ir-documents', content, {
      params: { requirementId },
      headers: { 'Content-Type': 'application/json' }
    })
  },

  update: (id: number, content: string) => {
    return api.put<IRDocument>(`/ir-documents/${id}`, content, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  validate: (id: number) => {
    return api.post<IRDocument>(`/ir-documents/${id}/validate`)
  },

  delete: (id: number) => {
    return api.delete(`/ir-documents/${id}`)
  }
}