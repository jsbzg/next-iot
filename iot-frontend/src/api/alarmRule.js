import request from '@/utils/request'

/**
 * 告警规则管理 API
 */
export const alarmRuleApi = {
  // 查询所有告警规则
  getList() {
    return request({
      url: '/api/alarm-rule/list',
      method: 'get'
    })
  },

  // 根据规则编码查询
  getByRuleCode(ruleCode) {
    return request({
      url: `/api/alarm-rule/rule/${ruleCode}`,
      method: 'get'
    })
  },

  // 创建告警规则
  create(data) {
    return request({
      url: '/api/alarm-rule/create',
      method: 'post',
      data
    })
  },

  // 更新告警规则
  update(data) {
    return request({
      url: '/api/alarm-rule/update',
      method: 'post',
      data
    })
  },

  // 删除告警规则
  delete(ruleCode) {
    return request({
      url: `/api/alarm-rule/${ruleCode}`,
      method: 'delete'
    })
  }
}