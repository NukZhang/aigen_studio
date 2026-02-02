<template>
  <div class="code-editor">
    <div class="editor-header">
      <div class="file-info">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ selectedFile?.name || '未选择文件' }}</span>
        <span v-if="selectedFile" class="file-type">{{ getFileTypeLabel(selectedFile.type) }}</span>
      </div>
      <div class="editor-actions">
        <el-button size="small" @click="copyContent" :disabled="!displayContent">
          <el-icon><CopyDocument /></el-icon>
          复制
        </el-button>
        <el-button
          size="small"
          type="primary"
          @click="saveContent"
          :loading="isSaving"
          :disabled="!canSave"
        >
          <el-icon><Check /></el-icon>
          保存
        </el-button>
      </div>
    </div>

    <div class="editor-content">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>加载中...</p>
      </div>

      <div v-else-if="!selectedFile" class="empty-state">
        <el-icon size="48"><Edit /></el-icon>
        <p>请从文件浏览器选择一个文件</p>
      </div>

      <div v-else-if="error" class="error-state">
        <el-icon size="48"><Warning /></el-icon>
        <p>{{ error }}</p>
      </div>

      <div v-else-if="isBinaryFile" class="empty-state">
        <el-icon size="48"><Warning /></el-icon>
        <p>该文件类型不支持编辑</p>
      </div>

      <div v-else-if="isEditable" class="editor-textarea">
        <el-input
          v-model="editedContent"
          type="textarea"
          :rows="24"
          class="code-input"
        />
      </div>

      <div v-else-if="!content" class="empty-state">
        <el-icon size="48"><Document /></el-icon>
        <p>文件内容为空</p>
      </div>

      <pre v-else class="code-display" :class="`language-${selectedFile?.type}`">{{ content }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Document,
  Edit,
  Warning,
  CopyDocument,
  Loading,
  Check
} from '@element-plus/icons-vue'
import { fileApi, type FileNode } from '../api/job'

interface Props {
  conversationId?: number
  selectedFile: FileNode | null
}

const props = defineProps<Props>()

const loading = ref(false)
const content = ref('')
const editedContent = ref('')
const error = ref('')
const isSaving = ref(false)

const isBinaryFile = computed(() => {
  const type = props.selectedFile?.type
  return type === 'image' || type === 'pdf' || type === 'archive'
})

const isEditable = computed(() => {
  return !!props.conversationId && !!props.selectedFile && !props.selectedFile.directory && !isBinaryFile.value
})

const canSave = computed(() => {
  return isEditable.value && editedContent.value !== content.value && !isSaving.value
})

const displayContent = computed(() => {
  return isEditable.value ? editedContent.value : content.value
})

watch(() => props.selectedFile, async (newFile) => {
  if (newFile && !newFile.directory) {
    await loadFileContent(newFile.path)
  } else {
    content.value = ''
    editedContent.value = ''
    error.value = ''
  }
})

const loadFileContent = async (filePath: string) => {
  loading.value = true
  error.value = ''
  content.value = ''
  editedContent.value = ''

  try {
    if (!props.conversationId) {
      error.value = '请先创建对话'
      return
    }
    const response = await fileApi.getConversationFileContent(props.conversationId, filePath)
    if (response.data.success && response.data.content !== undefined) {
      content.value = response.data.content || ''
      editedContent.value = response.data.content || ''
    } else {
      error.value = response.data.error || '加载文件内容失败'
    }
  } catch (err) {
    console.error('Failed to load file content:', err)
    error.value = '加载文件内容失败'
  } finally {
    loading.value = false
  }
}

const copyContent = async () => {
  if (!displayContent.value) return

  try {
    await navigator.clipboard.writeText(displayContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch (err) {
    console.error('Failed to copy:', err)
    ElMessage.error('复制失败')
  }
}

const saveContent = async () => {
  if (!props.conversationId || !props.selectedFile || !isEditable.value) return
  if (!canSave.value) return

  isSaving.value = true
  try {
    const response = await fileApi.saveConversationFileContent(
      props.conversationId,
      props.selectedFile.path,
      editedContent.value
    )
    if (response.data.success) {
      content.value = editedContent.value
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(response.data.error || '保存失败')
    }
  } catch (err) {
    console.error('Failed to save file content:', err)
    ElMessage.error('保存失败')
  } finally {
    isSaving.value = false
  }
}

const getFileTypeLabel = (type: string) => {
  const labels: Record<string, string> = {
    javascript: 'JavaScript',
    java: 'Java',
    python: 'Python',
    html: 'HTML',
    css: 'CSS',
    json: 'JSON',
    xml: 'XML/YAML',
    markdown: 'Markdown',
    text: '文本',
    image: '图片',
    pdf: 'PDF',
    archive: '压缩包',
    unknown: '未知',
    directory: '目录'
  }

  return labels[type] || type
}
</script>

<style scoped>
.code-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #3a3a3a;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-type {
  padding: 2px 8px;
  background-color: rgba(102, 126, 234, 0.2);
  color: #667eea;
  border-radius: 4px;
  font-size: 12px;
  flex-shrink: 0;
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.editor-content {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  position: relative;
}

.loading-state,
.empty-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  gap: 12px;
}

.code-display {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  background-color: #1a1a1a;
  border: 1px solid #3a3a3a;
  border-radius: 8px;
  font-family: 'Fira Code', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #e0e0e0;
}

.editor-textarea {
  height: 100%;
}

.editor-textarea :deep(.el-textarea__inner) {
  height: 100%;
  background-color: #1a1a1a;
  border-color: #3a3a3a;
  color: #e0e0e0;
  font-family: 'Fira Code', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.code-display::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.code-display::-webkit-scrollbar-track {
  background-color: #2a2a2a;
}

.code-display::-webkit-scrollbar-thumb {
  background-color: #3a3a3a;
  border-radius: 4px;
}

.code-display::-webkit-scrollbar-thumb:hover {
  background-color: #4a4a4a;
}

/* 基本语法高亮 */
.code-display {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.code-display > code {
  background-color: transparent;
  padding: 0;
  color: inherit;
  white-space: pre;
}
</style>
