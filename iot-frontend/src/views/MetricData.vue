<template>
  <div class="metric-data-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceCode" placeholder="输入设备编码" clearable />
        </el-form-item>
        <el-form-item label="点位编码">
          <el-input v-model="searchForm.propertyCode" placeholder="输入点位编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="deviceCode" label="设备编码" width="150" />
        <el-table-column prop="propertyCode" label="点位编码" width="150" />
        <el-table-column prop="value" label="数值" width="120">
          <template #default="{ row }">
            {{ row.value !== null ? row.value : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="strValue" label="文本值" show-overflow-tooltip />
        <el-table-column prop="ts" label="上报时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.ts) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="系统入库时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { metricDataApi } from '@/api/metricData'
import dayjs from 'dayjs'

// 搜索表单
const searchForm = reactive({
  deviceCode: '',
  propertyCode: ''
})

// 表格数据
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      ...searchForm
    }
    const res = await metricDataApi.getPage(params)
    if (res.data) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('加载指标数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

// 重置
const resetSearch = () => {
  searchForm.deviceCode = ''
  searchForm.propertyCode = ''
  currentPage.value = 1
  loadData()
}

// 分页切换
const handleSizeChange = (val) => {
  pageSize.value = val
  loadData()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadData()
}

// 格式化时间
const formatDate = (timestamp) => {
  if (!timestamp) return '-'
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.metric-data-container {
  .search-card {
    margin-bottom: 20px;
  }
  
  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
