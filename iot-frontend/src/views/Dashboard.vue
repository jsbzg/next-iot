<template>
  <div class="dashboard-container">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon device">
              <el-icon :size="40"><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.deviceCount }}</div>
              <div class="stat-label">设备总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon online">
              <el-icon :size="40"><Connection /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.onlineCount }}</div>
              <div class="stat-label">在线设备</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon alarm">
              <el-icon :size="40"><Bell /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.alarmCount }}</div>
              <div class="stat-label">活跃告警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon critical">
              <el-icon :size="40"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.criticalCount }}</div>
              <div class="stat-label">严重告警</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>设备在线统计</span>
            </div>
          </template>
          <div ref="deviceChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>告警级别分布</span>
            </div>
          </template>
          <div ref="alarmChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :span="24">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>告警趋势（近7天）</span>
            </div>
          </template>
          <div ref="trendChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { deviceApi } from '@/api/device'
import { alarmApi } from '@/api/alarm'
import { Monitor, Connection, Bell, Warning } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

// 统计数据
const stats = ref({
  deviceCount: 0,
  onlineCount: 0,
  alarmCount: 0,
  criticalCount: 0
})

// 图表引用
const deviceChartRef = ref(null)
const alarmChartRef = ref(null)
const trendChartRef = ref(null)

// 图表实例
let deviceChart = null
let alarmChart = null
let trendChart = null

// 加载统计数据
const loadStats = async () => {
  try {
    // 获取设备数据
    const deviceRes = await deviceApi.getList()
    if (deviceRes.data) {
      const devices = deviceRes.data
      stats.value.deviceCount = devices.length
      stats.value.onlineCount = devices.filter(d => d.online).length
    }

    // 获取告警数据
    const alarmRes = await alarmApi.getList()
    if (alarmRes.data) {
      const alarms = alarmRes.data
      stats.value.alarmCount = alarms.filter(a => a.status === 'ACTIVE').length
      stats.value.criticalCount = alarms.filter(a => a.level >= 2).length

      // 更新图表
      updateAlarmChart(alarms)
      updateTrendChart(alarms)
    }

    // 更新设备图表
    updateDeviceChart()
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 更新设备在线统计图表
const updateDeviceChart = () => {
  if (!deviceChart || !deviceChartRef.value) return

  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      right: '10%',
      top: 'center'
    },
    series: [
      {
        name: '设备状态',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: [
          { value: stats.value.onlineCount, name: '在线', itemStyle: { color: '#67C23A' } },
          {
            value: stats.value.deviceCount - stats.value.onlineCount,
            name: '离线',
            itemStyle: { color: '#F56C6C' }
          }
        ]
      }
    ]
  }

  deviceChart.setOption(option)
}

// 更新告警级别分布图表
const updateAlarmChart = (alarms) => {
  if (!alarmChart || !alarmChartRef.value) return

  const levelNames = ['提示', '警告', '严重', '紧急']
  const levelColors = ['#909399', '#E6A23C', '#F56C6C', '#990000']
  const levelCounts = [0, 0, 0, 0]

  alarms.forEach(alarm => {
    if (alarm.level >= 0 && alarm.level < 4) {
      levelCounts[alarm.level]++
    }
  })

  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      right: '10%',
      top: 'center'
    },
    series: [
      {
        name: '告警级别',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: levelNames.map((name, index) => ({
          value: levelCounts[index],
          name,
          itemStyle: { color: levelColors[index] }
        }))
      }
    ]
  }

  alarmChart.setOption(option)
}

// 更新告警趋势图表
const updateTrendChart = (alarms) => {
  if (!trendChart || !trendChartRef.value) return

  // 生成最近7天的日期
  const dates = []
  const counts = []

  for (let i = 6; i >= 0; i--) {
    const date = dayjs().subtract(i, 'day').format('MM-DD')
    dates.push(date)

    // 统计当天的告警数量
    const dayStart = dayjs().subtract(i, 'day').startOf('day').valueOf()
    const dayEnd = dayjs().subtract(i, 'day').endOf('day').valueOf()

    const count = alarms.filter(a =>
      a.lastTriggerTime >= dayStart && a.lastTriggerTime <= dayEnd
    ).length

    counts.push(count)
  }

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '告警数量'
    },
    series: [
      {
        name: '告警数量',
        type: 'line',
        data: counts,
        smooth: true,
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(245, 108, 108, 0.3)' },
              { offset: 1, color: 'rgba(245, 108, 108, 0.05)' }
            ]
          }
        },
        itemStyle: {
          color: '#F56C6C'
        }
      }
    ]
  }

  trendChart.setOption(option)
}

// 初始化图表
const initCharts = () => {
  deviceChart = echarts.init(deviceChartRef.value)
  alarmChart = echarts.init(alarmChartRef.value)
  trendChart = echarts.init(trendChartRef.value)
}

// 监听窗口大小变化
const handleResize = () => {
  deviceChart?.resize()
  alarmChart?.resize()
  trendChart?.resize()
}

onMounted(() => {
  initCharts()
  loadStats()
  window.addEventListener('resize', handleResize)
  window.addEventListener('refresh-data', loadStats)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('refresh-data', loadStats)
  deviceChart?.dispose()
  alarmChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  .stats-row {
    margin-bottom: 20px;
  }

  .stat-card {
    :deep(.el-card__body) {
      padding: 20px;
    }

    .stat-content {
      display: flex;
      align-items: center;
      gap: 20px;

      .stat-icon {
        width: 70px;
        height: 70px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;

        &.device {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }

        &.online {
          background: linear-gradient(135deg, #36D1DC 0%, #5B86E5 100%);
        }

        &.alarm {
          background: linear-gradient(135deg, #F093FB 0%, #F5576C 100%);
        }

        &.critical {
          background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
        }
      }

      .stat-info {
        flex: 1;

        .stat-value {
          font-size: 32px;
          font-weight: bold;
          color: #303133;
          line-height: 1;
          margin-bottom: 8px;
        }

        .stat-label {
          font-size: 14px;
          color: #909399;
        }
      }
    }
  }

  .charts-row {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .chart-card {
    .card-header {
      font-size: 16px;
      font-weight: 500;
      color: #303133;
    }
  }
}
</style>