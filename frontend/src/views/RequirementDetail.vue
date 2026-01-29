<template>
  <div class="requirement-detail-page">
    <el-page-header @back="goBack" title="返回" />
    
    <el-card style="margin-top: 20px" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>需求详情</span>
          <el-button type="primary" @click="editRequirement">
            <el-icon><Edit /></el-icon>
            编辑需求
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="requirement" :column="2" border>
        <el-descriptions-item label="需求编号">{{ requirement.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(requirement.status)">{{ getStatusText(requirement.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ requirement.title }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ requirement.description }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ requirement.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ requirement.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- IR 编辑器 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>IR 文档</span>
          <div>
            <el-button type="primary" @click="validateIR" :disabled="!irDocument">
              <el-icon><Check /></el-icon>
              验证
            </el-button>
            <el-button type="success" @click="saveIR" :disabled="!irDocument">
              <el-icon><Document /></el-icon>
              保存
            </el-button>
          </div>
        </div>
      </template>

      <div v-if="irDocument" class="ir-editor">
        <el-alert
          v-if="irDocument.status === 'VALID'"
          title="IR 文档验证通过"
          type="success"
          :closable="false"
          style="margin-bottom: 10px"
        />
        <el-alert
          v-else-if="irDocument.status === 'INVALID'"
          :title="'验证失败: ' + irDocument.validationErrors"
          type="error"
          :closable="false"
          style="margin-bottom: 10px"
        />
        <el-input
          v-model="irContent"
          type="textarea"
          :rows="20"
          placeholder="输入 IR 文档内容 (JSON 格式)"
          @input="irChanged = true"
        />
      </div>
      <el-empty v-else description="暂无 IR 文档，请创建">
        <el-button type="primary" @click="createIRDocument">创建 IR 文档</el-button>
      </el-empty>
    </el-card>

    <!-- 作业管理 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>作业列表</span>
          <el-button type="primary" @click="createJob" :disabled="!canCreateJob">
            <el-icon><VideoPlay /></el-icon>
            发起作业
          </el-button>
        </div>
      </template>

      <el-table :data="jobs" stripe>
        <el-table-column prop="jobCode" label="作业编号" width="150" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getJobStatusType(row.status)">{{ getJobStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewJobDetail(row.id)">
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Check, Document, VideoPlay } from '@element-plus/icons-vue'
import { requirementApi, type Requirement } from '@/api/requirement'
import { irDocumentApi, type IRDocument } from '@/api/irDocument'
import { jobApi, type GenerationJob } from '@/api/job'

const route = useRoute()
const router = useRouter()
const requirementId = Number(route.params.id)

const loading = ref(false)
const requirement = ref<Requirement | null>(null)
const irDocument = ref<IRDocument | null>(null)
const jobs = ref<GenerationJob[]>([])
const irContent = ref('')
const irChanged = ref(false)

const canCreateJob = computed(() => {
  return irDocument.value && irDocument.value.status === 'VALID'
})

const loadData = async () => {
  loading.value = true

  // 加载需求信息
  try {
    const reqRes = await requirementApi.getById(requirementId)
    requirement.value = reqRes.data
  } catch (error) {
    ElMessage.error('加载需求信息失败')
    loading.value = false
    return
  }

  // 加载作业列表（可能返回 404，这是正常情况）
  try {
    const jobRes = await jobApi.getByRequirementId(requirementId)
    jobs.value = jobRes.data
  } catch (jobError: any) {
    // 如果是 404 错误，说明还没有作业，这是正常情况
    if (jobError.response?.status !== 404) {
      console.error('加载作业列表失败', jobError)
    }
    // jobs 保持为空数组
  }

  // 加载 IR 文档（可能返回 404，这是正常情况）
  try {
    const irRes = await irDocumentApi.getByRequirementId(requirementId)
    if (irRes.data) {
      irDocument.value = irRes.data
      irContent.value = irRes.data.content
    }
  } catch (irError: any) {
    // 如果是 404 错误，说明还未创建 IR 文档，这是正常情况
    if (irError.response?.status !== 404) {
      console.error('加载 IR 文档失败', irError)
    }
    // irDocument 保持为 null，页面会显示创建按钮
  }

  loading.value = false
}

const createIRDocument = async () => {
  try {
    const defaultIR = JSON.stringify({
      projectName: "MyProject",
      modules: [
        {
          name: "frontend",
          type: "vue3",
          features: []
        },
        {
          name: "backend",
          type: "springboot",
          features: []
        }
      ]
    }, null, 2)

    await irDocumentApi.create(requirementId, defaultIR)
    ElMessage.success('IR 文档创建成功')
    loadData()
  } catch (error) {
    ElMessage.error('创建 IR 文档失败')
  }
}

const saveIR = async () => {
  if (!irDocument.value) return
  try {
    await irDocumentApi.update(irDocument.value.id, irContent.value)
    ElMessage.success('IR 文档保存成功')
    irChanged.value = false
    loadData()
  } catch (error) {
    ElMessage.error('保存 IR 文档失败')
  }
}

const validateIR = async () => {
  if (!irDocument.value) return
  try {
    await irDocumentApi.validate(irDocument.value.id)
    ElMessage.success('IR 验证完成')
    loadData()
  } catch (error) {
    ElMessage.error('验证失败')
  }
}

const createJob = async () => {
  if (!irDocument.value) return
  try {
    await jobApi.create(requirementId, irDocument.value.id)
    ElMessage.success('作业创建成功')
    loadData()
  } catch (error) {
    ElMessage.error('创建作业失败')
  }
}

const executeJob = async (jobId: number) => {
  try {
    await jobApi.execute(jobId)
    ElMessage.success('作业执行已启动')
    loadData()
  } catch (error) {
    ElMessage.error('启动作业失败')
  }
}

const viewJobDetail = (jobId: number) => {
  router.push(`/jobs/${jobId}`)
}

const editRequirement = () => {
  ElMessage.info('编辑功能待实现')
}

const goBack = () => {
  router.back()
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    CANCELLED: 'danger'
  }
  return map[status] || ''
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const getJobStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

const getJobStatusText = (status: string) => {
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
  loadData()
})
</script>

<style scoped>
.requirement-detail-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ir-editor {
  margin-top: 10px;
}
</style>