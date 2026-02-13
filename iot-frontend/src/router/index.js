import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据大屏' }
  },
  {
    path: '/devices',
    name: 'Devices',
    component: () => import('@/views/Devices.vue'),
    meta: { title: '设备管理' }
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('@/views/Models.vue'),
    meta: { title: '物模型管理' }
  },
  {
    path: '/parse-rules',
    name: 'ParseRules',
    component: () => import('@/views/ParseRules.vue'),
    meta: { title: '解析规则管理' }
  },
  {
    path: '/alarm-rules',
    name: 'AlarmRules',
    component: () => import('@/views/AlarmRules.vue'),
    meta: { title: '告警规则管理' }
  },
  {
    path: '/offline-rules',
    name: 'OfflineRules',
    component: () => import('@/views/OfflineRules.vue'),
    meta: { title: '离线规则管理' }
  },
  {
    path: '/metric-data',
    name: 'MetricData',
    component: () => import('@/views/MetricData.vue'),
    meta: { title: '数据上报' }
  },
  {
    path: '/alarms',
    name: 'Alarms',
    component: () => import('@/views/Alarms.vue'),
    meta: { title: '告警列表' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router