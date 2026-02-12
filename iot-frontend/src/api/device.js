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
  getByCode(deviceCode) {
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