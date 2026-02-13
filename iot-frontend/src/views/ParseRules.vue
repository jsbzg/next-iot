<template>
  <div class="parse-rules-container">
    <!-- 操作栏 -->
    <el-card class="action-card" shadow="never">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增解析规则</el-button>
    </el-card>

    <!-- 解析规则列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="gatewayType" label="网关类型" width="100" />
        <el-table-column prop="protocolType" label="协议类型" width="100" />
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">
            <el-tag type="primary">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchExpr" label="匹配表达式" show-overflow-tooltip />
        <el-table-column prop="parseScript" label="解析脚本" show-overflow-tooltip />
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

    <!-- 新增/编辑解析规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="ruleRules"
        label-width="120px"
      >
        <el-form-item label="网关类型" prop="gatewayType">
          <el-input v-model="ruleForm.gatewayType" placeholder="如: MQTT" :disabled="!!ruleForm.id" />
        </el-form-item>
        <el-form-item label="协议类型" prop="protocolType">
          <el-select v-model="ruleForm.protocolType" placeholder="请选择协议类型" style="width: 100%">
            <el-option label="MQTT" value="mqtt" />
            <el-option label="HTTP" value="http" />
            <el-option label="TCP" value="tcp" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配表达式" prop="matchExpr">
          <el-tooltip content="Aviator 表达式，用于判断是否命中该规则" placement="top">
            <el-input v-model="ruleForm.matchExpr" placeholder='如: deviceCode != nil' />
          </el-tooltip>
        </el-form-item>
        <el-form-item label="解析脚本" prop="parseScript">
          <el-tooltip content="Aviator 脚本，从原始报文提取字段（选填）" placement="top">
            <el-input
              v-model="ruleForm.parseScript"
              type="textarea"
              :rows="3"
              placeholder='可选，用于从原始报文中提取字段'
            />
          </el-tooltip>
        </el-form-item>
        <el-form-item label="映射脚本" prop="mappingScript">
          <el-tooltip content="Aviator 脚本，映射为标准格式（选填）" placement="top">
            <el-input
              v-model="ruleForm.mappingScript"
              type="textarea"
              :rows="3"
              placeholder='如: {"deviceCode": raw.deviceCode}'
            />
          </el-tooltip>
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>

        <!-- Aviator 脚本编辑器提示 -->
        <el-alert
          title="Aviator 表达式语法提示"
          type="info"
          :closable="false"
          style="margin-top: 20px"
        >
          <template #default>
            <div class="aviator-tips">
              <p>• 存在判断: <code>deviceCode != nil</code></p>
              <p>• 字符串匹配: <code>string.contains(raw.some_key, "keyword")</code></p>
              <p>• 数值比较: <code>value > 80</code></p>
              <p>• JSON 取值: <code>raw.deviceCode</code> 或直接 <code>deviceCode</code></p>
            </div>
          </template>
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
import { parseRuleApi } from '@/api/parseRule'

// 表格数据
const tableData = ref([])
const loading = ref(false)

// 对话框状态
const dialogVisible = ref(false)
const dialogTitle = ref('新增解析规则')
const submitting = ref(false)

// 表单数据
const ruleFormRef = ref(null)
const ruleForm = reactive({
  id: null,
  gatewayType: 'MQTT',
  protocolType: 'mqtt',
  matchExpr: 'deviceCode != nil',
  parseScript: '',
  mappingScript: '{"deviceCode": raw.deviceCode}',
  enabled: true,
  version: 0
})

// 验证规则
const ruleRules = {
  gatewayType: [{ required: true, message: '请输入网关类型', trigger: 'blur' }],
  protocolType: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  matchExpr: [{ required: true, message: '请输入匹配表达式', trigger: 'blur' }]
}

// 加载解析规则
const loadParseRules = async () => {
  loading.value = true
  try {
    const res = await parseRuleApi.getList()
    if (res.data) {
      tableData.value = res.data
    }
  } catch (error) {
    console.error('加载解析规则失败:', error)
  } finally {
    loading.value = false
  }
}

// 新增
const handleAdd = () => {
  Object.assign(ruleForm, {
    id: null,
    gatewayType: 'MQTT',
    protocolType: 'mqtt',
    matchExpr: 'deviceCode != nil',
    parseScript: '',
    mappingScript: '{"deviceCode": raw.deviceCode}',
    enabled: true,
    version: 0
  })
  dialogTitle.value = '新增解析规则'
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  Object.assign(ruleForm, row)
  dialogTitle.value = '编辑解析规则'
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除该解析规则吗？`, '提示', { type: 'warning' })
    await parseRuleApi.delete(row.id)
    ElMessage.success('删除成功')
    loadParseRules()
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
        // 编辑模式：版本号自动在后端递增（或前端传递，取决于实现偏好，Impl中实现是Create递增）
        // 实际上后端 Update 实现没有自动递增版本，为了确保 Flink 更新，我们在前端手动递增版本
        const updateData = { ...ruleForm, version: (ruleForm.version || 0) + 1 }
        await parseRuleApi.update(updateData)
        ElMessage.success('更新成功，规则版本已升至 v' + updateData.version + ' 并同步到 Flink')
      } else {
        await parseRuleApi.create(ruleForm)
        ElMessage.success('添加成功，规则已同步到 Flink')
      }
      dialogVisible.value = false
      loadParseRules()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadParseRules()
  window.addEventListener('refresh-data', loadParseRules)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadParseRules)
})
</script>

<style scoped lang="scss">
.parse-rules-container {
  .action-card, .table-card {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .aviator-tips {
    code {
      background: #f5f7fa;
      padding: 2px 6px;
      border-radius: 4px;
      color: #409EFF;
    }

    p {
      margin: 5px 0;
      font-size: 13px;
    }

    a {
      color: #409EFF;
    }
  }
}
</style>