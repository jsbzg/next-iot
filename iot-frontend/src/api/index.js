import request from '@/utils/request'

/**
 * 设备管理 API
 */
export const deviceApi = {
  // 查询所有设备
  getList() {
    return request({
      url: '/api/device/list',
      method: 'get'
    })
  },

  // 查询单个设备
  getByDeviceCode(deviceCode) {
    return request({
      url: `/api/device/${deviceCode}`,
      method: 'get'
    })
  },

  // 创建设备
  create(data) {
    return request({
      url: '/api/device/create',
      method: 'post',
      data
    })
  },

  // 更新设备
  update(data) {
    return request({
      url: '/api/device/update',
      method: 'post',
      data
    })
  },

  // 删除设备
  delete(deviceCode) {
    return request({
      url: `/api/device/${deviceCode}`,
      method: 'delete'
    })
  }
}

/**
 * 物模型管理 API
 */
export const modelApi = {
  // 查询所有物模型
  getList() {
    return request({
      url: '/api/model/list',
      method: 'get'
    })
  },

  // 创建物模型
  create(data) {
    return request({
      url: '/api/model/create',
      method: 'post',
      data
    })
  },

  // 更新物模型
  update(data) {
    return request({
      url: '/api/model/update',
      method: 'post',
      data
    })
  },

  // 删除物模型
  delete(modelCode) {
    return request({
      url: `/api/model/${modelCode}`,
      method: 'delete'
    })
  },

  // 根据物模型查询点位列表
  getProperties(modelCode) {
    return request({
      url: `/api/model/${modelCode}/properties`,
      method: 'get'
    })
  },

  // 创建点位
  createProperty(data) {
    return request({
      url: '/api/model/property/create',
      method: 'post',
      data
    })
  },

  // 更新点位
  updateProperty(data) {
    return request({
      url: '/api/model/property/update',
      method: 'post',
      data
    })
  },

  // 删除点位
  deleteProperty(id) {
    return request({
      url: `/api/model/property/${id}`,
      method: 'delete'
    })
  }
}

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
 * 告警列表 API
 */
export const alarmApi = {
  // 查询所有告警
  getList() {
    return request({
      url: '/alarm/list',
      method: 'get'
    })
  },

  // 查询单个告警
  getById(id) {
    return request({
      url: `/alarm/${id}`,
      method: 'get'
    })
  },

  // 确认告警
  ack(id, user = 'admin') {
    return request({
      url: `/alarm/${id}/ack?user=${user}`,
      method: 'post'
    })
  },

  // 恢复告警
  recover(ruleCode, deviceCode) {
    return request({
      url: '/alarm/recover',
      method: 'post',
      params: { ruleCode, deviceCode }
    })
  }
}