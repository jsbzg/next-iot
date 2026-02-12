import request from '@/utils/request'

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

  // 根据设备编码查询
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