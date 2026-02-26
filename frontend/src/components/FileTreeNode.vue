<template>
  <div class="tree-node" :class="{ selected: isSelected }">
    <div class="node-content" @click="handleToggle">
      <el-icon class="folder-icon" v-if="node.directory">
        <component :is="isExpanded ? FolderOpened : Folder" />
      </el-icon>
      <el-icon class="file-icon" v-else>
        <component :is="nodeIcon" />
      </el-icon>
      <span class="node-name">{{ node.name }}</span>
    </div>
    <div class="node-children" v-if="node.directory && isExpanded && node.children">
      <FileTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :expanded-nodes="expandedNodes"
        :selected-node="selectedNode"
        @toggle="$emit('toggle', $event)"
        @select="$emit('select', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Folder, FolderOpened, Document, Picture } from '@element-plus/icons-vue'

interface Props {
  node: {
    name: string
    path: string
    directory: boolean
    children?: any[]
    type?: string
  }
  expandedNodes: Set<string>
  selectedNode?: any
}

const props = defineProps<Props>()

const emit = defineEmits<{
  toggle: [node: any]
  select: [node: any]
}>()

const nodeIcon = computed(() => {
  if (props.node.directory) {
    return Folder
  }

  const typeIcons: Record<string, unknown> = {
    javascript: Document,
    java: Document,
    python: Document,
    html: Document,
    css: Document,
    json: Document,
    xml: Document,
    markdown: Document,
    image: Picture,
    pdf: Document,
    archive: Folder
  }

  return typeIcons[props.node.type || 'unknown'] || Document
})

const isExpanded = computed(() => {
  return props.expandedNodes.has(props.node.path)
})

const isSelected = computed(() => {
  return props.selectedNode?.path === props.node.path
})

const handleToggle = () => {
  if (props.node.directory) {
    emit('toggle', props.node)
  } else {
    emit('select', props.node)
  }
}
</script>

<style scoped>
.tree-node {
  border-radius: 6px;
  overflow: hidden;
}

.tree-node.selected > .node-content {
  background-color: #667eea;
  color: white;
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.2s;
  user-select: none;
}

.node-content:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.tree-node.selected > .node-content:hover {
  background-color: #667eea;
}

.folder-icon,
.file-icon {
  font-size: 16px;
  color: #888;
}

.tree-node.selected > .node-content .folder-icon,
.tree-node.selected > .node-content .file-icon {
  color: white;
}

.node-name {
  font-size: 13px;
  color: #e0e0e0;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-children {
  padding-left: 20px;
  margin-top: 4px;
}
</style>
