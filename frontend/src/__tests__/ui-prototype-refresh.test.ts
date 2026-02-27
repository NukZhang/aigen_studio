import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UIPrototypePanel from '@/views/UIPrototypePanel.vue'

const apiMocks = vi.hoisted(() => ({
  getUIPrototype: vi.fn(),
  confirmUIPrototype: vi.fn(),
  regenerateUIPrototype: vi.fn()
}))

vi.mock('@/api/ui-prototype', () => ({
  uiPrototypeApi: {
    getUIPrototype: apiMocks.getUIPrototype,
    confirmUIPrototype: apiMocks.confirmUIPrototype,
    regenerateUIPrototype: apiMocks.regenerateUIPrototype
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn()
  }
}))

describe('UIPrototypePanel refresh preview', () => {
  beforeEach(() => {
    apiMocks.getUIPrototype.mockReset()
    apiMocks.confirmUIPrototype.mockReset()
    apiMocks.regenerateUIPrototype.mockReset()
  })

  it('refreshes preview when user clicks refresh button', async () => {
    apiMocks.getUIPrototype.mockResolvedValue({
      data: {
        id: 1,
        stage: 'UI_READY',
        uiConfirmed: false,
        uiPrototypeContent: '<div>demo</div>',
        htmlContent: '<div>demo</div>'
      }
    })

    const wrapper = mount(UIPrototypePanel, {
      props: { conversationId: 1 },
      global: {
        stubs: {
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' }
        }
      }
    })

    await flushPromises()
    await flushPromises()
    const initialCalls = apiMocks.getUIPrototype.mock.calls.length

    const refreshButton = wrapper
      .findAll('button')
      .find(button => button.text().includes('刷新预览'))

    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.getUIPrototype.mock.calls.length).toBeGreaterThan(initialCalls)

    wrapper.unmount()
  })
})
