<template>
  <div id="app">
    <el-container>
      <el-header>
        <div class="header-content">
          <h1 class="logo">AIGen Studio</h1>
          <el-menu
            :default-active="activeMenu"
            mode="horizontal"
            router
            class="nav-menu"
          >
            <el-menu-item index="/requirements">需求管理</el-menu-item>
            <el-menu-item index="/jobs">作业管理</el-menu-item>
            <el-menu-item index="/artifacts">产出物</el-menu-item>
          </el-menu>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
#app {
  min-height: 100vh;
}

.el-header {
  background-color: #409eff;
  color: white;
  padding: 0;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px;
}

.logo {
  margin: 0 40px 0 0;
  font-size: 24px;
  font-weight: bold;
}

.nav-menu {
  background-color: transparent;
  border: none;
  flex: 1;
}

.nav-menu .el-menu-item {
  color: white;
  font-size: 16px;
}

.nav-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.nav-menu .el-menu-item.is-active {
  background-color: rgba(255, 255, 255, 0.2);
  border-bottom: 2px solid white;
}

.el-main {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  width: 100%;
}
</style>