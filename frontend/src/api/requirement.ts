import api from './index'

export interface Requirement {
  id: number
  code: string
  title: string
  description: string
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface CreateRequirementRequest {
  code: string
  title: string
  description: string
}

export interface UpdateRequirementRequest {
  title: string
  description: string
}

export const requirementApi = {
  getAll: (status?: string) => {
    const params = status ? { status } : {}
    return api.get<Requirement[]>('/requirements', { params })
  },

  getById: (id: number) => {
    return api.get<Requirement>(`/requirements/${id}`)
  },

  create: (data: CreateRequirementRequest) => {
    return api.post<Requirement>('/requirements', data)
  },

  update: (id: number, data: UpdateRequirementRequest) => {
    return api.put<Requirement>(`/requirements/${id}`, data)
  },

  updateStatus: (id: number, status: string) => {
    return api.patch<Requirement>(`/requirements/${id}/status`, null, {
      params: { status }
    })
  },

  delete: (id: number) => {
    return api.delete(`/requirements/${id}`)
  }
}