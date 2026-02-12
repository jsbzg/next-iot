import request from '@/utils/request'

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

  // 查询点位列表
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