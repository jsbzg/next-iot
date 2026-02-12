import request from '@/utils/request'

/**
 * 解析规则管理 API
 */
export const parseRuleApi = {
  // 查询所有解析规则
  getList() {
    return request({
      url: '/api/parse-rule/list',
      method: 'get'
    })
  },

  // 根据网关类型查询解析规则
  getByGatewayType(gatewayType) {
    return request({
      url: `/api/parse-rule/gateway/${gatewayType}`,
      method: 'get'
    })
  },

  // 创建解析规则
  create(data) {
    return request({
      url: '/api/parse-rule/create',
      method: 'post',
      data
    })
  },

  // 更新解析规则
  update(data) {
    return request({
      url: '/api/parse-rule/update',
      method: 'post',
      data
    })
  },

  // 删除解析规则
  delete(id) {
    return request({
      url: `/api/parse-rule/${id}`,
      method: 'delete'
    })
  }
}

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

  // 根据规则编码查询告警规则
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

/**
 * 离线规则管理 API
 */
export const offlineRuleApi = {
  // 查询所有离线规则
  getList() {
    return request({
      url: '/api/offline-rule/list',
      method: 'get'
    })
  },

  // 根据设备编码查询离线规则
  getByDeviceCode(deviceCode) {
    return request({
      url: `/api/offline-rule/device/${deviceCode}`,
      method: 'get'
    })
  },

  // 创建离线规则
  create(data) {
    return request({
      url: '/api/offline-rule/create',
      method: 'post',
      data
    })
  },

  // 更新离线规则
  update(data) {
    return request({
      url: '/api/offline-rule/update',
      method: 'post',
      data
    })
  },

  // 删除离线规则
  delete(deviceCode) {
    return request({
      url: `/api/offline-rule/${deviceCode}`,
      method: 'delete'
    })
  }
}

/**
 * 告警管理 API
 */
export const alarmApi = {
  // 查询告警列表
  getList(params) {
    return request({
      url: '/alarm/alarm/list',
      method: 'get',
      params
    })
  },

  // 查询告警详情
  getById(id) {
    return request({
      url: `/alarm/alarm/${id}`,
      method: 'get'
    })
  },

  // 确认告警
  ack(id, user = 'admin') {
    return request({
      url: `/alarm/alarm/${id}/ack`,
      method: 'post',
      params: { user }
    })
  },

  // 恢复告警
  recover(data) {
    return request({
      url: '/alarm/alarm/recover',
      method: 'post',
      data
    })
  }
}