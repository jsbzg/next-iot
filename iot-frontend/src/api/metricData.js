import request from '@/utils/request'

/**
 * 设备指标数据 API
 */
export const metricDataApi = {
  // 分页查询指标数据
  getPage(params) {
    return request({
      url: '/api/metric-data/page',
      method: 'get',
      params
    })
  }
}
