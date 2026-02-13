<template>
  <div class="offline-rules-container">
    <!-- 操作栏 -->
    <el-card class="action-card" shadow="never">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增离线规则</el-button>
    </el-card>

    <!-- 离线规则列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="deviceCode" label="设备编码" min-width="180" />
        <el-table-column prop="timeoutSeconds" label="超时时长" min-width="150">
          <template #default="{ row }">
            {{ row.timeoutSeconds }} 秒 ({{ Math.floor(row.timeoutSeconds / 60) }}分钟)
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑离线规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="ruleRules"
        label-width="140px"
      >
        <el-form-item label="设备编码" prop="deviceCode">
          <el-input v-model="ruleForm.deviceCode" placeholder="如: dev_001" :disabled="!!ruleForm.id" />
        </el-form-item>
        <el-form-item label="超时时长（秒）" prop="timeoutSeconds">
          <el-input-number
            v-model="ruleForm.timeoutSeconds"
            :min="10"
            :max="3600"
            :step="60"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-alert
          title="说明"
          type="warning"
          :closable="false"
          style="margin-top: 20px"
        >
          设备在设定的超时时长内未上报数据，将触发离线告警。
        </el-alert>
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
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import { offlineRuleApi } from '@/api/offlineRule'
import dayjs from 'dayjs'

// 表格数据
const tableData = ref([])
const loading = ref(false)

// 对话框状态
const dialogVisible = ref(false)
const dialogTitle = ref('新增离线规则')
const submitting = ref(false)

// 表单数据
const ruleFormRef = ref(null)
const ruleForm = reactive({
  id: null,
  deviceCode: '',
  timeoutSeconds: 300,
  enabled: true
})

// 验证规则
const ruleRules = {
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  timeoutSeconds: [{ required: true, message: '请输入超时时长', trigger: 'blur' }]
}

// 加载离线规则
const loadOfflineRules = async () => {
  loading.value = true
  try {
    const res = await offlineRuleApi.getList()
    if (res.data) {
      tableData.value = res.data
    }
  } catch (error) {
    console.error('加载离线规则失败:', error)
  } finally {
    loading.value = false
  }
}

// 新增
const handleAdd = () => {
  Object.assign(ruleForm, {
    id: null,
    deviceCode: '',
    timeoutSeconds: 300,
    enabled: true
  })
  dialogTitle.value = '新增离线规则'
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  Object.assign(ruleForm, row)
  dialogTitle.value = '编辑离线规则'
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除设备 ${row.deviceCode} 的离线规则吗？`, '提示', { type: 'warning' })
    await offlineRuleApi.delete(row.deviceCode)
    ElMessage.success('删除成功')
    loadOfflineRules()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (ruleForm.id) {
        await offlineRuleApi.update(ruleForm)
        ElMessage.success('更新成功，规则已实时同步到 Flink')
      } else {
        await offlineRuleApi.create(ruleForm)
        ElMessage.success('添加成功，规则已实时同步到 Flink')
      }
      dialogVisible.value = false
      loadOfflineRules()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

// 格式化时间
const formatDate = (timestamp) => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  loadOfflineRules()
  window.addEventListener('refresh-data', loadOfflineRules)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadOfflineRules)
})
</script>

<style scoped lang="scss">
.offline-rules-container {
  .action-card, .table-card {
    margin-bottom: 20px;
    &:last-child {
      margin-bottom: 0;
    }
  }
}
</style>