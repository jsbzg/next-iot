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

  // 根据网关类型查询
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