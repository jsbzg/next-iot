<template>
  <div class="devices-container">
    <!-- 搜索和操作栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceCode" placeholder="请输入设备编码" clearable />
        </el-form-item>
        <el-form-item label="物模型">
          <el-input v-model="searchForm.modelCode" placeholder="请输入物模型编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增设备</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="deviceCode" label="设备编码" min-width="150" />
        <el-table-column prop="deviceName" label="设备名称" min-width="180" />
        <el-table-column prop="modelCode" label="物模型" min-width="120" />
        <el-table-column prop="gatewayType" label="网关类型" min-width="100">
          <template #default="{ row }">
            <el-tag :type="getGatewayTypeTag(row.gatewayType)">
              {{ row.gatewayType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="online" label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'danger'">
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSeenTs" label="最后上报时间" min-width="180">
          <template #default="{ row }">
            {{ row.lastSeenTs ? formatDate(row.lastSeenTs) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="deviceFormRef"
        :model="deviceForm"
        :rules="deviceRules"
        label-width="100px"
      >
        <el-form-item label="设备编码" prop="deviceCode">
          <el-input v-model="deviceForm.deviceCode" placeholder="请输入设备编码" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="deviceForm.deviceName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="物模型" prop="modelCode">
          <el-input v-model="deviceForm.modelCode" placeholder="请输入物模型编码" />
        </el-form-item>
        <el-form-item label="网关类型" prop="gatewayType">
          <el-select v-model="deviceForm.gatewayType" placeholder="请选择网关类型" style="width: 100%">
            <el-option label="MQTT" value="MQTT" />
            <el-option label="HTTP" value="HTTP" />
            <el-option label="TCP" value="TCP" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import dayjs from 'dayjs'

// 搜索表单
const searchForm = reactive({
  deviceCode: '',
  modelCode: ''
})

// 表格数据
const tableData = ref([])
const loading = ref(false)

// 对话框状态
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogMode = ref('add') // add | edit
const submitting = ref(false)

// 表单数据
const deviceFormRef = ref(null)
const deviceForm = reactive({
  deviceCode: '',
  deviceName: '',
  modelCode: '',
  gatewayType: ''
})

// 表单验证规则
const deviceRules = {
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  modelCode: [{ required: true, message: '请输入物模型编码', trigger: 'blur' }],
  gatewayType: [{ required: true, message: '请选择网关类型', trigger: 'change' }]
}

// 加载设备列表
const loadDevices = async () => {
  loading.value = true
  try {
    const res = await deviceApi.getList()
    if (res.data) {
      tableData.value = res.data.filter(item => {
        let match = true
        if (searchForm.deviceCode && !item.deviceCode.includes(searchForm.deviceCode)) {
          match = false
        }
        if (searchForm.modelCode && !item.modelCode.includes(searchForm.modelCode)) {
          match = false
        }
        return match
      })
    }
  } catch (error) {
    console.error('加载设备列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  loadDevices()
}

// 重置
const handleReset = () => {
  searchForm.deviceCode = ''
  searchForm.modelCode = ''
  loadDevices()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增设备'
  dialogMode.value = 'add'
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  dialogTitle.value = '编辑设备'
  dialogMode.value = 'edit'
  Object.assign(deviceForm, {
    deviceCode: row.deviceCode,
    deviceName: row.deviceName || '',
    modelCode: row.modelCode,
    gatewayType: row.gatewayType
  })
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除设备 ${row.deviceCode} 吗？`, '提示', {
      type: 'warning'
    })

    await deviceApi.delete(row.deviceCode)
    ElMessage.success('删除成功')
    loadDevices()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除设备失败:', error)
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!deviceFormRef.value) return

  await deviceFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (dialogMode.value === 'add') {
        await deviceApi.create(deviceForm)
        ElMessage.success('添加成功')
      } else {
        await deviceApi.update(deviceForm)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadDevices()
      // 重置表单
      resetForm()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  deviceFormRef.value?.resetFields()
  Object.assign(deviceForm, {
    deviceCode: '',
    deviceName: '',
    modelCode: '',
    gatewayType: ''
  })
}

// 格式化时间
const formatDate = (timestamp) => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

// 获取网关类型标签样式
const getGatewayTypeTag = (type) => {
  const map = {
    MQTT: '',
    HTTP: 'warning',
    TCP: 'info'
  }
  return map[type] || ''
}

// 监听全局刷新事件
onMounted(() => {
  loadDevices()
  window.addEventListener('refresh-data', loadDevices)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadDevices)
})
</script>

<style scoped lang="scss">
.devices-container {
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