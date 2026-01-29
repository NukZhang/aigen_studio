<template>
  <div class="jobs-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>作业管理</span>
          <el-select v-model="statusFilter" placeholder="筛选状态" clearable @change="loadJobs" style="width: 150px">
            <el-option label="待执行" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </div>
      </template>

      <el-table :data="jobs" v-loading="loading" stripe>
        <el-table-column prop="jobCode" label="作业编号" width="150" />
        <el-table-column prop="requirementId" label="需求ID" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row.id)">
              查看详情
            </el-button>
            <el-button
              link
              type="success"
              size="small"
              @click="executeJob(row.id)"
              :disabled="row.status !== 'PENDING'"
            >
              执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi, type GenerationJob } from '@/api/job'

const router = useRouter()
const loading = ref(false)
const jobs = ref<GenerationJob[]>([])
const statusFilter = ref('')

const loadJobs = async () => {
  loading.value = true
  try {
    const { data } = await jobApi.getAll(statusFilter.value)
    jobs.value = data
  } catch (error) {
    ElMessage.error('加载作业列表失败')
  } finally {
    loading.value = false
  }
}

const executeJob = async (jobId: number) => {
  // 先更新本地状态为执行中
  const job = jobs.value.find(j => j.id === jobId)
  if (job) {
    job.status = 'RUNNING'
  }
  
  try {
    await jobApi.execute(jobId)
    ElMessage.success('作业执行已启动')
    loadJobs()
  } catch (error) {
    // 如果失败,恢复状态
    if (job) {
      job.status = 'PENDING'
    }
    ElMessage.error('启动作业失败')
  }
}

const viewDetail = (id: number) => {
  router.push(`/jobs/${id}`)
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

onMounted(() => {
  loadJobs()
})
</script>

<style scoped>
.jobs-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>