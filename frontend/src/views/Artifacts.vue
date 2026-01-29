<template>
  <div class="artifacts-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>产出物管理</span>
          <div>
            <el-select v-model="jobFilter" placeholder="筛选作业" clearable @change="loadArtifacts" style="width: 200px; margin-right: 10px">
              <el-option
                v-for="job in distinctJobs"
                :key="job.jobId"
                :label="job.jobCode"
                :value="job.jobId"
              />
            </el-select>
            <el-select v-model="typeFilter" placeholder="筛选类型" clearable @change="loadArtifacts" style="width: 150px">
              <el-option label="前端代码" value="FRONTEND_CODE" />
              <el-option label="后端代码" value="BACKEND_CODE" />
              <el-option label="OpenAPI 规范" value="OPENAPI_SPEC" />
              <el-option label="SDK" value="SDK" />
              <el-option label="文档" value="DOCUMENTATION" />
              <el-option label="证据" value="EVIDENCE" />
              <el-option label="项目" value="PROJECT" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="filteredArtifacts" v-loading="loading" stripe>
        <el-table-column prop="jobId" label="作业ID" width="100" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="type" label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.type)">{{ getTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="300" />
        <el-table-column label="Git 地址" width="200">
          <template #default="{ row }">
            <el-link
              v-if="row.gitlabProjectUrl"
              :href="getGitFileUrl(row)"
              target="_blank"
              type="primary"
              :underline="false"
            >
              <el-icon><Link /></el-icon>
              {{ row.gitlabBranch || 'main' }} / {{ row.gitlabFilePath || row.path }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ row.fileSize ? formatFileSize(row.fileSize) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewArtifact(row)">
              查看
            </el-button>
            <el-button link type="success" size="small" @click="downloadArtifact(row)">
              下载
            </el-button>
            <el-button 
              link 
              type="warning" 
              size="small" 
              @click="deliverToGitLab(row)"
              :loading="deliveringArtifactId === row.id"
            >
              交付到GitLab
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filteredArtifacts.length === 0" description="暂无产出物" />
    </el-card>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="detailVisible" title="产出物详情" width="60%">
      <el-descriptions v-if="selectedArtifact" :column="2" border>
        <el-descriptions-item label="ID">{{ selectedArtifact.id }}</el-descriptions-item>
        <el-descriptions-item label="作业ID">{{ selectedArtifact.jobId }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ selectedArtifact.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="getTypeColor(selectedArtifact.type)">{{ getTypeText(selectedArtifact.type) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="路径" :span="2">{{ selectedArtifact.path }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ selectedArtifact.fileSize ? formatFileSize(selectedArtifact.fileSize) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ selectedArtifact.createdAt }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="selectedArtifact?.preview" style="margin-top: 20px">
        <h4>预览</h4>
        <el-card>
          <pre class="preview-content">{{ selectedArtifact.preview }}</pre>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { artifactApi, type Artifact } from '@/api/artifact'
import { jobApi } from '@/api/job'

const loading = ref(false)
const artifacts = ref<Artifact[]>([])
const jobFilter = ref<number | null>(null)
const typeFilter = ref<string>('')
const detailVisible = ref(false)
const selectedArtifact = ref<Artifact | null>(null)
const distinctJobs = ref<Array<{ jobId: number; jobCode: string }>>([])
const deliveringArtifactId = ref<number | null>(null)

const filteredArtifacts = computed(() => {
  let result = artifacts.value

  if (jobFilter.value) {
    result = result.filter(a => a.jobId === jobFilter.value)
  }

  if (typeFilter.value) {
    result = result.filter(a => a.type === typeFilter.value)
  }

  return result
})

const loadArtifacts = async () => {
  loading.value = true
  try {
    const { data } = await artifactApi.getAll()
    artifacts.value = data

    // 提取唯一的作业
    const jobsMap = new Map<number, string>()
    data.forEach(artifact => {
      if (!jobsMap.has(artifact.jobId)) {
        jobsMap.set(artifact.jobId, `作业 #${artifact.jobId}`)
      }
    })

    // 获取作业代码
    const jobIds = Array.from(jobsMap.keys())
    if (jobIds.length > 0) {
      try {
        const jobsResponse = await jobApi.getAll()
        jobsResponse.data.forEach(job => {
          if (jobsMap.has(job.id)) {
            jobsMap.set(job.id, job.jobCode)
          }
        })
      } catch (error) {
        console.error('获取作业列表失败', error)
      }
    }

    distinctJobs.value = Array.from(jobsMap.entries()).map(([jobId, jobCode]) => ({ jobId, jobCode }))
  } catch (error) {
    ElMessage.error('加载产出物失败')
  } finally {
    loading.value = false
  }
}

const viewArtifact = (artifact: Artifact) => {
  selectedArtifact.value = artifact
  detailVisible.value = true
}

const downloadArtifact = (artifact: Artifact) => {
  ElMessage.info(`下载产出物: ${artifact.name}`)
  // TODO: 实现下载功能
}

const deliverToGitLab = async (artifact: Artifact) => {
  deliveringArtifactId.value = artifact.id
  try {
    await artifactApi.deliverToGitLab(artifact.id)
    ElMessage.success('交付到 GitLab 成功')
    // 刷新列表
    await loadArtifacts()
  } catch (error: any) {
    ElMessage.error('交付失败: ' + (error.response?.data?.message || error.message))
  } finally {
    deliveringArtifactId.value = null
  }
}

const getTypeColor = (type: string) => {
  const map: Record<string, string> = {
    FRONTEND_CODE: 'success',
    BACKEND_CODE: 'primary',
    OPENAPI_SPEC: 'warning',
    SDK: 'info',
    DOCUMENTATION: '',
    EVIDENCE: 'danger',
    PROJECT: 'success'
  }
  return map[type] || ''
}

const getTypeText = (type: string) => {
  const map: Record<string, string> = {
    FRONTEND_CODE: '前端代码',
    BACKEND_CODE: '后端代码',
    OPENAPI_SPEC: 'OpenAPI 规范',
    SDK: 'SDK',
    DOCUMENTATION: '文档',
    EVIDENCE: '证据',
    PROJECT: '项目'
  }
  return map[type] || type
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const getGitFileUrl = (artifact: Artifact) => {
  if (!artifact.gitlabProjectUrl) return ''
  const branch = artifact.gitlabBranch || 'main'
  const filePath = artifact.gitlabFilePath || artifact.path
  return `${artifact.gitlabProjectUrl}/-/blob/${branch}/${filePath}`
}

onMounted(() => {
  loadArtifacts()
})
</script>

<style scoped>
.artifacts-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-content {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>