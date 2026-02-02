<template>
  <div class="file-browser">
    <div class="browser-header">
      <h3>文件浏览器</h3>
      <el-button size="small" @click="refreshFileTree">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div class="browser-content">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>加载中...</p>
      </div>

      <div v-else-if="!fileTree || fileTree.length === 0" class="empty-state">
        <el-icon size="48"><FolderOpened /></el-icon>
        <p>暂无文件</p>
      </div>

      <div v-else class="file-tree">
        <FileTreeNode
          v-for="node in fileTree"
          :key="node.path"
          :node="node"
          :expanded-nodes="expandedNodes"
          :selected-node="selectedNode"
          @toggle="toggleNode"
          @select="selectNode"
        />
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  FolderOpened,
  Refresh,
  Loading
} from '@element-plus/icons-vue'
import { fileApi, type FileNode } from '../api/job'
import FileTreeNode from '../components/FileTreeNode.vue'

interface Props {
  conversationId?: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  fileSelected: [node: FileNode]
}>()

const loading = ref(false)
const fileTree = ref<FileNode[]>([])
const expandedNodes = ref<Set<string>>(new Set())
const selectedNode = ref<FileNode | null>(null)

onMounted(() => {
  loadFileTree()
})

watch(() => props.conversationId, () => {
  loadFileTree()
})

const loadFileTree = async () => {
  loading.value = true
  try {
    if (!props.conversationId) {
      fileTree.value = []
      return
    }
    const response = await fileApi.getConversationFileTree(props.conversationId)
    fileTree.value = response.data
    // 默认展开第一层目录
    if (fileTree.value.length > 0 && fileTree.value[0].directory) {
      expandedNodes.value.add(fileTree.value[0].path)
    }
  } catch (error) {
    console.error('Failed to load file tree:', error)
    ElMessage.error('加载文件树失败')
  } finally {
    loading.value = false
  }
}

const refreshFileTree = () => {
  loadFileTree()
}

const toggleNode = (node: FileNode) => {
  if (expandedNodes.value.has(node.path)) {
    expandedNodes.value.delete(node.path)
  } else {
    expandedNodes.value.add(node.path)
  }
}

const selectNode = (node: FileNode) => {
  selectedNode.value = node
  emit('fileSelected', node)
}

</script>

<style scoped>
.file-browser {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.browser-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.browser-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.browser-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  gap: 12px;
}

.file-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-actions {
  padding: 12px 0;
  border-top: 1px solid #3a3a3a;
  display: flex;
  gap: 8px;
}
</style>
