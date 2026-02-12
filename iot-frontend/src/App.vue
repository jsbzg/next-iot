<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="sidebar">
      <div class="logo">
        <el-icon><DataLine /></el-icon>
        <span>IoT 数据中台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>数据大屏</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/models">
          <el-icon><Grid /></el-icon>
          <span>物模型管理</span>
        </el-menu-item>
        <el-menu-item index="/parse-rules">
          <el-icon><Setting /></el-icon>
          <span>解析规则</span>
        </el-menu-item>
        <el-menu-item index="/alarm-rules">
          <el-icon><Warning /></el-icon>
          <span>告警规则</span>
        </el-menu-item>
        <el-menu-item index="/offline-rules">
          <el-icon><Clock /></el-icon>
          <span>离线规则</span>
        </el-menu-item>
        <el-menu-item index="/alarms">
          <el-icon><Bell /></el-icon>
          <span>告警列表</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h2>{{ pageTitle }}</h2>
          <div class="header-actions">
            <el-button :icon="Refresh" circle @click="refreshData" :loading="loading" />
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  Odometer, Monitor, Grid, Setting, Warning, Clock,
  Bell, Refresh, DataLine
} from '@element-plus/icons-vue'

const route = useRoute()
const loading = ref(false)

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 页面标题映射
const titleMap = {
  '/dashboard': '数据大屏',
  '/devices': '设备管理',
  '/models': '物模型管理',
  '/parse-rules': '解析规则管理',
  '/alarm-rules': '告警规则管理',
  '/offline-rules': '离线规则管理',
  '/alarms': '告警列表'
}

// 页面标题
const pageTitle = computed(() => titleMap[route.path] || 'IoT 数据中台')

// 刷新数据
const refreshData = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    // 触发全局事件通知组件刷新
    window.dispatchEvent(new Event('refresh-data'))
  }, 500)
}
</script>

<style scoped layout-container>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  gap: 10px;
  background-color: #002140;
}

.header {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
}

.header-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-content h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>