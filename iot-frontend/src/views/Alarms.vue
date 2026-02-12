<template>
  <div class="alarms-container">
    <!-- 搜索和操作栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="已确认" value="ACKED" />
            <el-option label="已恢复" value="RECOVERED" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceCode" placeholder="请输入设备编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Check" @click="handleBatchAck" :disabled="!hasActiveAlarms">
            批量确认
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 告警列表 -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="ruleCode" label="规则编码" width="150" />
        <el-table-column prop="deviceCode" label="设备编码" width="150" />
        <el-table-column prop="propertyCode" label="点位" width="120" />
        <el-table-column prop="value" label="数值" width="100">
          <template #default="{ row }">
            {{ row.value !== null && row.value !== undefined ? row.value : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelTag(row.level)">{{ getLevelName(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggerTime" label="触发时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastTriggerTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="success"
              link
              :icon="Check"
              @click="handleAck(row)"
            >
              确认
            </el-button>
            <el-button
              v-else
              disabled
              link
            >
              确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadAlarms"
        @current-change="loadAlarms"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 告警详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="告警详情" width="600px">
      <el-descriptions :column="2" border v-if="currentAlarm">
        <el-descriptions-item label="规则编码">{{ currentAlarm.ruleCode }}</el-descriptions-item>
        <el-descriptions-item label="设备编码">{{ currentAlarm.deviceCode }}</el-descriptions-item>
        <el-descriptions-item label="点位编码">{{ currentAlarm.propertyCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数值">
          {{ currentAlarm.value !== null && currentAlarm.value !== undefined ? currentAlarm.value : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getLevelTag(currentAlarm.level)">
            {{ getLevelName(currentAlarm.level) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTag(currentAlarm.status)">
            {{ getStatusName(currentAlarm.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentAlarm.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次触发时间">{{ formatDate(currentAlarm.firstTriggerTime) }}</el-descriptions-item>
        <el-descriptions-item label="最后触发时间">{{ formatDate(currentAlarm.lastTriggerTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="currentAlarm.ackTime" label="确认时间">{{ formatDate(currentAlarm.ackTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="currentAlarm.ackUser" label="确认用户">{{ currentAlarm.ackUser }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="currentAlarm && currentAlarm.status === 'ACTIVE'"
          type="primary"
          :icon="Check"
          @click="handleAckFromDetail"
        >
          确认告警
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Check } from '@element-plus/icons-vue'
import { alarmApi } from '@/api/alarm'
import dayjs from 'dayjs'

// 搜索表单
const searchForm = reactive({
  status: '',
  deviceCode: ''
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 数据
const tableData = ref([])
const loading = ref(false)
const selectedAlarms = ref([])

// 详情对话框
const detailDialogVisible = ref(false)
const currentAlarm = ref(null)

// 是否有活跃告警
const hasActiveAlarms = computed(() => {
  return selectedAlarms.value.some(a => a.status === 'ACTIVE')
})

// 多选变化
const handleSelectionChange = (selection) => {
  selectedAlarms.value = selection
}

// 加载告警列表
const loadAlarms = async () => {
  loading.value = true
  try {
    const res = await alarmApi.getList(searchForm)
    if (res.data) {
      tableData.value = res.data
      pagination.total = res.data.length
    }
  } catch (error) {
    console.error('加载告警列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  loadAlarms()
}

// 重置
const handleReset = () => {
  searchForm.status = ''
  searchForm.deviceCode = ''
  pagination.page = 1
  loadAlarms()
}

// 确认告警
const handleAck = async (row) => {
  try {
    await alarmApi.ack(row.id)
    ElMessage.success('确认成功')
    loadAlarms()
  } catch (error) {
    console.error('确认失败:', error)
  }
}

// 批量确认
const handleBatchAck = async () => {
  const activeAlarms = selectedAlarms.value.filter(a => a.status === 'ACTIVE')
  if (activeAlarms.length === 0) {
    ElMessage.warning('请选择需要确认的活跃告警')
    return
  }

  try {
    await ElMessageBox.confirm(`确认这 ${activeAlarms.length} 条告警吗？`, '批量确认', { type: 'warning' })

    for (const alarm of activeAlarms) {
      await alarmApi.ack(alarm.id)
    }

    ElMessage.success(`成功确认 ${activeAlarms.length} 条告警`)
    loadAlarms()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量确认失败:', error)
    }
  }
}

// 确认详情中的告警
const handleAckFromDetail = async () => {
  await handleAck(currentAlarm.value)
  detailDialogVisible.value = false
}

// 获取级别名称
const getLevelName = (level) => {
  const levels = ['提示', '警告', '严重', '紧急']
  return levels[level] || '未知'
}

// 获取级别标签
const getLevelTag = (level) => {
  const tags = ['info', 'warning', 'danger', 'danger']
  return tags[level] || ''
}

// 获取状态名称
const getStatusName = (status) => {
  const names = { 'ACTIVE': '活跃', 'ACKED': '已确认', 'RECOVERED': '已恢复' }
  return names[status] || status
}

// 获取状态标签
const getStatusTag = (status) => {
  const tags = { 'ACTIVE': 'danger', 'ACKED': 'warning', 'RECOVERED': 'success' }
  return tags[status] || ''
}

// 格式化时间
const formatDate = (timestamp) => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  loadAlarms()
  window.addEventListener('refresh-data', loadAlarms)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadAlarms)
})
</script>

<style scoped lang="scss">
.alarms-container {
  .search-card, .table-card {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .search-form {
    margin-bottom: 0;
  }
}
</style>