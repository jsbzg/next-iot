import request from '@/utils/request'

/**
 * 告警实例 API
 */
export const alarmApi = {
  // 查询所有告警
  getList(params = {}) {
    return request({
      url: '/api/alarm/list',
      method: 'get',
      params
    })
  },

  // 查询单个告警详情
  getById(id) {
    return request({
      url: `/api/alarm/${id}`,
      method: 'get'
    })
  },

  // 确认告警
  ack(id, user = 'admin') {
    return request({
      url: `/api/alarm/${id}/ack`,
      method: 'post',
      params: { user }
    })
  },

  // 恢复告警
  recover(ruleCode, deviceCode) {
    return request({
      url: '/api/alarm/recover',
      method: 'post',
      params: { ruleCode, deviceCode }
    })
  }
}