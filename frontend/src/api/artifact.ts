import api from './index'

export interface Artifact {
  id: number
  jobId: number
  name: string
  type: string
  path: string
  preview: string | null
  fileSize: number | null
  gitlabProjectId: number | null
  gitlabProjectUrl: string | null
  gitlabBranch: string | null
  gitlabCommitId: string | null
  gitlabFilePath: string | null
  createdAt: string
  // 作业的 GitLab 信息
  gitlabGroupId: number | null
  gitlabFrontendProjectId: number | null
  gitlabBackendProjectId: number | null
  gitlabFrontendProjectUrl: string | null
  gitlabBackendProjectUrl: string | null
}

export const artifactApi = {
  getAll: () => {
    return api.get<Artifact[]>('/artifacts')
  },

  getByJobId: (jobId: number) => {
    return api.get<Artifact[]>(`/artifacts/job/${jobId}`)
  },

  getByType: (jobId: number, type: string) => {
    return api.get<Artifact[]>(`/artifacts/job/${jobId}/type/${type}`)
  },

  getById: (id: number) => {
    return api.get<Artifact>(`/artifacts/${id}`)
  },

  deliverToGitLab: (id: number) => {
    return api.post(`/artifacts/${id}/deliver-gitlab`)
  },

  // 获取作业的交付日志
  getDeliveryLogs: (jobId: number) => {
    return api.get<string>(`/jobs/${jobId}/delivery-logs`)
  }
}