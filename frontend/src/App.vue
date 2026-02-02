<template>
  <div id="app">
    <el-container class="app-container">
      <!-- 侧边栏 -->
      <el-aside width="64px" class="sidebar">
        <div class="sidebar-header">
          <div class="logo">AI</div>
        </div>
        <div class="sidebar-menu">
          <div
            class="menu-item"
            :class="{ active: currentRoute === 'workspace' }"
            @click="navigateTo('workspace')"
          >
            <el-icon><HomeFilled /></el-icon>
            <span class="menu-label">工作区</span>
          </div>
          
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-container class="main-container">
        <el-main class="main-content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const currentRoute = computed(() => {
  const path = route.path
  if (path.startsWith('/workspace')) return 'workspace'
  return 'workspace'
})

const navigateTo = (routeName: string) => {
  router.push(`/${routeName}`)
}
</script>

<style scoped>
#app {
  min-height: 100vh;
  background-color: #1a1a1a;
  color: #e0e0e0;
}

.app-container {
  height: 100vh;
}

/* 侧边栏样式 */
.sidebar {
  background-color: #2a2a2a;
  border-right: 1px solid #3a3a3a;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
}

.sidebar-header {
  margin-bottom: 24px;
}

.logo {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
  color: white;
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}

.menu-item {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #b0b0b0;
}

.menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.menu-item.active {
  background-color: rgba(102, 126, 234, 0.2);
  color: #667eea;
}

.menu-item .el-icon {
  font-size: 20px;
}

.menu-label {
  font-size: 10px;
  margin-top: 2px;
}

/* 主内容区样式 */
.main-container {
  flex: 1;
  background-color: #1a1a1a;
}

.main-content {
  padding: 0;
  overflow: hidden;
}
</style>
