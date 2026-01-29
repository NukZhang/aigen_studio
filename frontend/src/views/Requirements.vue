<template>
  <div class="requirements-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>需求管理</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            新建需求
          </el-button>
        </div>
      </template>

      <el-table :data="requirements" v-loading="loading" stripe>
        <el-table-column prop="code" label="需求编号" width="150" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row.id)">
              查看详情
            </el-button>
            <el-button link type="primary" size="small" @click="editRequirement(row)">
              编辑
            </el-button>
            <el-popconfirm title="确定删除此需求吗？" @confirm="deleteRequirement(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建需求对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建需求" width="500px">
      <el-form :model="createForm" :rules="rules" ref="createFormRef" label-width="80px">
        <el-form-item label="需求编号" prop="code">
          <el-input v-model="createForm.code" placeholder="例如: REQ-2024-001" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="输入需求标题" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="输入需求描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createRequirement">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑需求对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑需求" width="500px">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="editForm.title" placeholder="输入需求标题" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="输入需求描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="updateRequirement">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { requirementApi, type Requirement, type CreateRequirementRequest, type UpdateRequirementRequest } from '@/api/requirement'

const router = useRouter()
const loading = ref(false)
const requirements = ref<Requirement[]>([])

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const createFormRef = ref()
const editFormRef = ref()

const createForm = ref<CreateRequirementRequest>({
  code: '',
  title: '',
  description: ''
})

const editForm = ref<UpdateRequirementRequest & { id: number }>({
  id: 0,
  title: '',
  description: ''
})

const rules = {
  code: [{ required: true, message: '请输入需求编号', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

const editRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

const loadRequirements = async () => {
  loading.value = true
  try {
    const { data } = await requirementApi.getAll()
    requirements.value = data
  } catch (error) {
    ElMessage.error('加载需求列表失败')
  } finally {
    loading.value = false
  }
}

const createRequirement = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      await requirementApi.create(createForm.value)
      ElMessage.success('需求创建成功')
      showCreateDialog.value = false
      createForm.value = { code: '', title: '', description: '' }
      loadRequirements()
    } catch (error) {
      ElMessage.error('创建需求失败')
    }
  })
}

const updateRequirement = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      await requirementApi.update(editForm.value.id, editForm.value)
      ElMessage.success('需求更新成功')
      showEditDialog.value = false
      loadRequirements()
    } catch (error) {
      ElMessage.error('更新需求失败')
    }
  })
}

const editRequirement = (row: Requirement) => {
  editForm.value = {
    id: row.id,
    title: row.title,
    description: row.description
  }
  showEditDialog.value = true
}

const deleteRequirement = async (id: number) => {
  try {
    await requirementApi.delete(id)
    ElMessage.success('需求删除成功')
    loadRequirements()
  } catch (error) {
    ElMessage.error('删除需求失败')
  }
}

const viewDetail = (id: number) => {
  router.push(`/requirements/${id}`)
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

onMounted(() => {
  loadRequirements()
})
</script>

<style scoped>
.requirements-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>