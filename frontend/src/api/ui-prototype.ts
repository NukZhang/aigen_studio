import axios from './index'

export const uiPrototypeApi = {
  /**
   * 获取 UI 原型
   */
  getUIPrototype(conversationId: number) {
    return axios.get(`/api/ui-prototype/${conversationId}`)
  },

  /**
   * 确认 UI 设计
   */
  confirmUIPrototype(conversationId: number) {
    return axios.post(`/api/ui-prototype/${conversationId}/confirm`)
  },

  /**
   * 重新生成 UI 原型
   */
  regenerateUIPrototype(conversationId: number) {
    return axios.post(`/api/ui-prototype/${conversationId}/regenerate`)
  }
}