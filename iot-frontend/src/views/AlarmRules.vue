<template>
  <div class="alarm-rules-container">
    <!-- 操作栏 -->
    <el-card class="action-card" shadow="never">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增告警规则</el-button>
    </el-card>

    <!-- 告警规则列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="ruleCode" label="规则编码" width="150" />
        <el-table-column prop="deviceCode" label="设备编码" width="120">
          <template #default="{ row }">
            {{ row.deviceCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="propertyCode" label="点位编码" width="120" />
        <el-table-column prop="conditionExpr" label="条件表达式" show-overflow-tooltip />
        <el-table-column prop="triggerType" label="触发类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ getTriggerTypeName(row.triggerType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerN" label="触发参数" width="100">
          <template #default="{ row }">
            {{ row.triggerType === 'CONTINUOUS_N' ? `连续${row.triggerN}次` : `窗口${row.windowSeconds}秒` }}
          </template>
        </el-table-column>
        <el-table-column prop="suppressSeconds" label="抑制时间" width="100">
          <template #default="{ row }">
            {{ row.suppressSeconds }}秒
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelTag(row.level)">{{ getLevelName(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑告警规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="ruleRules"
        label-width="140px"
      >
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" placeholder="如: TEMP_HIGH_3" :disabled="!!ruleForm.id" />
        </el-form-item>
        <el-form-item label="设备编码" prop="deviceCode">
          <el-input v-model="ruleForm.deviceCode" placeholder="留空表示按模型配置" clearable>
            <el-tooltip content="留空表示该规则适用于所有该物模型的设备" placement="top">
              <el-icon :size="16" color="#409EFF"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-input>
        </el-form-item>
        <el-form-item label="点位编码" prop="propertyCode">
          <el-input v-model="ruleForm.propertyCode" placeholder="如: temperature" />
        </el-form-item>
        <el-form-item label="条件表达式" prop="conditionExpr">
          <el-input v-model="ruleForm.conditionExpr" placeholder='如: value > 80（Aviator表达式）' />
          <template #append>
            <el-tooltip content="Aviator 表达式，用于判断是否触发告警" placement="top">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-form-item>
        <el-form-item label="触发类型" prop="triggerType">
          <el-select v-model="ruleForm.triggerType" placeholder="请选择触发类型" style="width: 100%">
            <el-option label="连续N次" value="CONTINUOUS_N" />
            <el-option label="时间窗口" value="WINDOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发参数N" prop="triggerN">
          <el-input-number
            v-model="ruleForm.triggerN"
            :min="1"
            :max="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="ruleForm.triggerType === 'WINDOW'" label="窗口大小（秒）" prop="windowSeconds">
          <el-input-number
            v-model="ruleForm.windowSeconds"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="流内抑制时间（秒）" prop="suppressSeconds">
          <el-input-number
            v-model="ruleForm.suppressSeconds"
            :min="1"
            :max="3600"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="告警级别" prop="level">
          <el-select v-model="ruleForm.level" placeholder="请选择告警级别" style="width: 100%">
            <el-option label="提示(INFO)" :value="0" />
            <el-option label="警告(WARNING)" :value="1" />
            <el-option label="严重(CRITICAL)" :value="2" />
            <el-option label="紧急(EMERGENCY)" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="ruleForm.description" placeholder="如: 温度连续N次超过阈值" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="ruleForm.enabled" />
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
import { Plus, Delete, Edit, QuestionFilled } from '@element-plus/icons-vue'
import { alarmRuleApi } from '@/api/alarmRule'

// 表格数据
const tableData = ref([])
const loading = ref(false)

// 对话框状态
const dialogVisible = ref(false)
const dialogTitle = ref('新增告警规则')
const submitting = ref(false)

// 表单数据
const ruleFormRef = ref(null)
const ruleForm = reactive({
  id: null,
  ruleCode: '',
  deviceCode: '',
  propertyCode: 'temperature',
  conditionExpr: 'value > 80',
  triggerType: 'CONTINUOUS_N',
  triggerN: 3,
  windowSeconds: 60,
  suppressSeconds: 60,
  level: 2,
  description: '温度连续N次超过阈值',
  enabled: true
})

// 验证规则
const ruleRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  propertyCode: [{ required: true, message: '请输入点位编码', trigger: 'blur' }],
  conditionExpr: [{ required: true, message: '请输入条件表达式', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }],
  triggerN: [{ required: true, message: '请输入触发参数', trigger: 'blur' }],
  suppressSeconds: [{ required: true, message: '请输入抑制时间', trigger: 'blur' }],
  level: [{ required: true, message: '请选择告警级别', trigger: 'change' }]
}

// 加载告警规则
const loadAlarmRules = async () => {
  loading.value = true
  try {
    const res = await alarmRuleApi.getList()
    if (res.data) {
      tableData.value = res.data
    }
  } catch (error) {
    console.error('加载告警规则失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取触发类型名称
const getTriggerTypeName = (type) => {
  const map = {
    CONTINUOUS_N: '连续N次',
    WINDOW: '时间窗口'
  }
  return map[type] || type
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

// 新增
const handleAdd = () => {
  Object.assign(ruleForm, {
    id: null,
    ruleCode: `RULE_${Date.now()}`,
    deviceCode: '',
    propertyCode: 'temperature',
    conditionExpr: 'value > 80',
    triggerType: 'CONTINUOUS_N',
    triggerN: 3,
    windowSeconds: 60,
    suppressSeconds: 60,
    level: 2,
    description: '温度连续N次超过阈值',
    enabled: true
  })
  dialogTitle.value = '新增告警规则'
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  Object.assign(ruleForm, row)
  dialogTitle.value = '编辑告警规则'
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除告警规则 ${row.ruleCode} 吗？`, '提示', { type: 'warning' })
    await alarmRuleApi.delete(row.ruleCode)
    ElMessage.success('删除成功')
    loadAlarmRules()
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
        await alarmRuleApi.update(ruleForm)
        ElMessage.success('更新成功，规则已实时同步到 Flink')
      } else {
        await alarmRuleApi.create(ruleForm)
        ElMessage.success('添加成功，规则已实时同步到 Flink')
      }
      dialogVisible.value = false
      loadAlarmRules()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadAlarmRules()
  window.addEventListener('refresh-data', loadAlarmRules)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadAlarmRules)
})
</script>

<style scoped lang="scss">
.alarm-rules-container {
  .action-card, .table-card {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }
}
</style>