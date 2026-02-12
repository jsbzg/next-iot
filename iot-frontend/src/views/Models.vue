<template>
  <div class="models-container">
    <!-- 搜索和操作栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="模型编码">
          <el-input v-model="searchForm.modelCode" placeholder="请输入模型编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="handleAddModel">新增物模型</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 物模型列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="modelCode" label="模型编码" width="150" />
        <el-table-column prop="modelName" label="模型名称" width="200" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewProperties(row)">查看点位</el-button>
            <el-button type="primary" link :icon="Plus" @click="handleAddProperty(row)">新增点位</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDeleteModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增物模型对话框 -->
    <el-dialog v-model="modelDialogVisible" title="新增物模型" width="500px">
      <el-form ref="modelFormRef" :model="modelForm" :rules="modelRules" label-width="100px">
        <el-form-item label="模型编码" prop="modelCode">
          <el-input v-model="modelForm.modelCode" placeholder="如: SENSOR_TPH" />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="如: 温湿度传感器" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitModel">确定</el-button>
      </template>
    </el-dialog>

    <!-- 点位列表对话框 -->
    <el-dialog v-model="propertyDialogVisible" title="点位列表" width="800px">
      <el-button type="primary" size="small" :icon="Plus" @click="handleAddProperty(currentModel)" style="margin-bottom: 15px">新增点位</el-button>
      <el-table :data="properties" border stripe max-height="400">
        <el-table-column prop="propertyCode" label="点位编码" width="150" />
        <el-table-column prop="propertyName" label="点位名称" width="150" />
        <el-table-column prop="dataType" label="数据类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getDataTypeTag(row.dataType)">
              {{ row.dataType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDeleteProperty(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新增点位的对话框 -->
    <el-dialog v-model="propertyFormDialogVisible" title="新增点位" width="500px">
      <el-form ref="propertyFormRef" :model="propertyForm" :rules="propertyRules" label-width="120px">
        <el-form-item label="点位编码" prop="propertyCode">
          <el-input v-model="propertyForm.propertyCode" placeholder="如: temperature" />
        </el-form-item>
        <el-form-item label="点位名称" prop="propertyName">
          <el-input v-model="propertyForm.propertyName" placeholder="如: 温度" />
        </el-form-item>
        <el-form-item label="数据类型" prop="dataType">
          <el-select v-model="propertyForm.dataType" placeholder="请选择数据类型" style="width: 100%">
            <el-option label="整数" value="int" />
            <el-option label="浮点数" value="double" />
            <el-option label="字符串" value="string" />
            <el-option label="布尔值" value="bool" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="propertyForm.unit" placeholder="如: °C" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="propertyFormDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitProperty">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, View, Delete } from '@element-plus/icons-vue'
import { modelApi } from '@/api/model'
import dayjs from 'dayjs'

// 搜索表单
const searchForm = reactive({
  modelCode: ''
})

// 数据
const tableData = ref([])
const loading = ref(false)
const properties = ref([])
const currentModel = ref(null)

// 对话框状态
const modelDialogVisible = ref(false)
const propertyDialogVisible = ref(false)
const propertyFormDialogVisible = ref(false)
const submitting = ref(false)

// 表单
const modelFormRef = ref(null)
const modelForm = reactive({
  modelCode: '',
  modelName: ''
})

const propertyFormRef = ref(null)
const propertyForm = reactive({
  modelCode: '',
  propertyCode: '',
  propertyName: '',
  dataType: 'double',
  unit: ''
})

// 验证规则
const modelRules = {
  modelCode: [{ required: true, message: '请输入模型编码', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const propertyRules = {
  propertyCode: [{ required: true, message: '请输入点位编码', trigger: 'blur' }],
  propertyName: [{ required: true, message: '请输入点位名称', trigger: 'blur' }],
  dataType: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
}

// 加载物模型
const loadModels = async () => {
  loading.value = true
  try {
    const res = await modelApi.getList()
    if (res.data) {
      tableData.value = res.data.filter(item => {
        return !searchForm.modelCode || item.modelCode.includes(searchForm.modelCode)
      })
    }
  } catch (error) {
    console.error('加载物模型失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  loadModels()
}

// 重置
const handleReset = () => {
  searchForm.modelCode = ''
  loadModels()
}

// 新增物模型
const handleAddModel = () => {
  Object.assign(modelForm, { modelCode: '', modelName: '' })
  modelDialogVisible.value = true
}

// 提交物模型
const handleSubmitModel = async () => {
  await modelFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await modelApi.create(modelForm)
      ElMessage.success('添加成功')
      modelDialogVisible.value = false
      loadModels()
    } catch (error) {
      console.error('添加失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

// 查看点位
const handleViewProperties = async (row) => {
  currentModel.value = row
  await loadProperties(row.modelCode)
  propertyDialogVisible.value = true
}

// 加载点位
const loadProperties = async (modelCode) => {
  try {
    const res = await modelApi.getProperties(modelCode)
    if (res.data) {
      properties.value = res.data
    }
  } catch (error) {
    console.error('加载点位失败:', error)
  }
}

// 新增点位
const handleAddProperty = (row) => {
  currentModel.value = row
  Object.assign(propertyForm, {
    modelCode: row.modelCode,
    propertyCode: '',
    propertyName: '',
    dataType: 'double',
    unit: ''
  })
  propertyFormDialogVisible.value = true
}

// 提交点位
const handleSubmitProperty = async () => {
  await propertyFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await modelApi.createProperty(propertyForm)
      ElMessage.success('添加成功')
      propertyFormDialogVisible.value = false
      await loadProperties(propertyForm.modelCode)
    } catch (error) {
      console.error('添加失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

// 删除点位
const handleDeleteProperty = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该点位吗？', '提示', { type: 'warning' })
    await modelApi.deleteProperty(row.id)
    ElMessage.success('删除成功')
    await loadProperties(row.modelCode)
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 删除物模型
const handleDeleteModel = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该物模型吗？', '提示', { type: 'warning' })
    await modelApi.delete(row.modelCode)
    ElMessage.success('删除成功')
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 格式化时间
const formatDate = (timestamp) => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

// 获取数据类型标签
const getDataTypeTag = (type) => {
  const map = {
    int: 'primary',
    double: 'success',
    string: 'warning',
    bool: 'info'
  }
  return map[type] || ''
}

onMounted(() => {
  loadModels()
  window.addEventListener('refresh-data', loadModels)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-data', loadModels)
})
</script>

<style scoped lang="scss">
.models-container {
  .search-card, .table-card {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }
}
</style>