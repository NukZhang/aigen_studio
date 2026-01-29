<template>
  <div class="job-detail-page">
    <el-page-header @back="goBack" title="返回" />
    
    <el-card style="margin-top: 20px" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>作业详情</span>
          <el-button
            type="success"
            @click="executeJob"
            :disabled="job?.status !== 'PENDING'"
          >
            <el-icon><VideoPlay /></el-icon>
            执行作业
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="job" :column="2" border>
        <el-descriptions-item label="作业编号">{{ job.jobCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(job.status)">{{ getStatusText(job.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="需求ID">{{ job.requirementId }}</el-descriptions-item>
        <el-descriptions-item label="IR 文档ID">{{ job.irDocumentId }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ job.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ job.createdAt }}</el-descriptions-item>
        <el-descriptions-item v-if="job.gitlabBranch" label="Git 分支">{{ job.gitlabBranch }}</el-descriptions-item>
        <el-descriptions-item v-if="job.gitlabCommitId" label="Git 提交ID">{{ job.gitlabCommitId }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="job && job.errorMessage" style="margin-top: 20px">
        <el-alert title="执行错误" type="error" :description="job.errorMessage" :closable="false" />
      </div>
    </el-card>

    <!-- 执行日志 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>执行日志</span>
          <el-button @click="refreshLogs" :loading="logLoading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div class="log-container">
        <pre class="log-content">{{ job?.logOutput || '暂无日志' }}</pre>
      </div>
    </el-card>

    <!-- 产出物 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <span>产出物</span>
      </template>

      <el-table :data="artifacts" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="type" label="类型" width="150" />
        <el-table-column prop="path" label="路径" />
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Refresh, Link } from '@element-plus/icons-vue'
import { jobApi, type GenerationJob } from '@/api/job'
import { artifactApi, type Artifact } from '@/api/artifact'

const route = useRoute()
const router = useRouter()
const jobId = Number(route.params.id)

const loading = ref(false)
const logLoading = ref(false)
const job = ref<GenerationJob | null>(null)
const artifacts = ref<Artifact[]>([])
const deliveringArtifactId = ref<number | null>(null)

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await jobApi.getById(jobId)
    job.value = data
    await loadArtifacts()
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadArtifacts = async () => {
  try {
    const { data } = await artifactApi.getByJobId(jobId)
    artifacts.value = data
  } catch (error) {
    console.error('加载产出物失败', error)
  }
}

const refreshLogs = async () => {
  logLoading.value = true
  try {
    await loadData()
  } finally {
    logLoading.value = false
  }
}

const executeJob = async () => {
  // 先更新本地状态为执行中
  if (job.value) {
    job.value.status = 'RUNNING'
  }
  
  try {
    await jobApi.execute(jobId)
    ElMessage.success('作业执行已启动')
    
    // 轮询日志
    const pollInterval = setInterval(async () => {
      await refreshLogs()
      if (job.value?.status === 'SUCCESS' || job.value?.status === 'FAILED') {
        clearInterval(pollInterval)
      }
    }, 2000)
  } catch (error) {
    // 如果失败,恢复状态
    if (job.value) {
      job.value.status = 'PENDING'
    }
    ElMessage.error('启动作业失败')
  }
}

const viewArtifact = (artifact: Artifact) => {
  ElMessage.info('查看产出物: ' + artifact.name)
}

const downloadArtifact = (artifact: Artifact) => {
  ElMessage.info('下载产出物: ' + artifact.name)
}

const deliverToGitLab = async (artifact: Artifact) => {
  deliveringArtifactId.value = artifact.id
  try {
    await artifactApi.deliverToGitLab(artifact.id)
    ElMessage.success('交付到 GitLab 成功')
    // 刷新产出物列表
    await loadArtifacts()
  } catch (error: any) {
    ElMessage.error('交付失败: ' + (error.response?.data?.message || error.message))
  } finally {
    deliveringArtifactId.value = null
  }
}

const goBack = () => {
  router.back()
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return map[status] || status
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
  loadData()
})
</script>

<style scoped>
.job-detail-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-container {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
}

.log-content {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>