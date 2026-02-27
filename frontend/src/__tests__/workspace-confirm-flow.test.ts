import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Workspace from '@/views/Workspace.vue'
import { workspaceTestStubs } from './helpers/workspace-test-stubs'

vi.mock('marked', () => ({
  marked: (text: string) => text
}))

const apiMocks = vi.hoisted(() => ({
  getNewConversation: vi.fn(),
  getActiveConversations: vi.fn(),
  createNewConversation: vi.fn(),
  sendMessageToNewConversation: vi.fn(),
  confirmUnderstanding: vi.fn(),
  confirmUnderstandingSdac: vi.fn(),
  designUi: vi.fn(),
  startPreview: vi.fn(),
  stopPreview: vi.fn(),
  restartPreview: vi.fn(),
  getStatus: vi.fn()
}))

vi.mock('@/api/job', () => ({
  conversationApi: {
    getNewConversation: apiMocks.getNewConversation,
    getActiveConversations: apiMocks.getActiveConversations,
    createNewConversation: apiMocks.createNewConversation,
    sendMessageToNewConversation: apiMocks.sendMessageToNewConversation,
    confirmUnderstanding: apiMocks.confirmUnderstanding,
    confirmUnderstandingSdac: apiMocks.confirmUnderstandingSdac,
    designUi: apiMocks.designUi
  },
  previewApi: {
    startPreview: apiMocks.startPreview,
    stopPreview: apiMocks.stopPreview,
    restartPreview: apiMocks.restartPreview,
    getStatus: apiMocks.getStatus
  }
}))

vi.mock('@/api/tutorial', () => ({
  getTutorialTree: vi.fn().mockResolvedValue({ data: [] }),
  getTutorialContent: vi.fn().mockResolvedValue({
    data: {
      path: '',
      content: '',
      headings: []
    }
  })
}))

describe('Workspace confirm requirement flow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    apiMocks.getNewConversation.mockReset()
    apiMocks.getActiveConversations.mockReset()
    apiMocks.confirmUnderstanding.mockReset()
    apiMocks.confirmUnderstandingSdac.mockReset()
    apiMocks.designUi.mockReset()
    apiMocks.getActiveConversations.mockResolvedValue({ data: [] })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('auto starts UI design after requirement confirmation', async () => {
    apiMocks.getNewConversation
      .mockResolvedValueOnce({
        data: {
          id: 1,
          projectName: 'Test Project',
          stage: 'UNDERSTANDING_CONFIRMED',
          aiUnderstanding: '理解内容',
          understandingConfirmed: false,
          messages: []
        }
      })
      .mockResolvedValueOnce({
        data: {
          id: 1,
          projectName: 'Test Project',
          stage: 'UNDERSTANDING_CONFIRMED',
          aiUnderstanding: '理解内容',
          understandingConfirmed: true,
          messages: []
        }
      })
      .mockResolvedValueOnce({
        data: {
          id: 1,
          projectName: 'Test Project',
          stage: 'UI_DESIGNING',
          aiUnderstanding: '理解内容',
          understandingConfirmed: true,
          messages: []
        }
      })
    apiMocks.confirmUnderstandingSdac.mockResolvedValue({ data: {} })
    apiMocks.designUi.mockResolvedValue({ data: {} })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/workspace', component: Workspace }]
    })
    await router.push({ path: '/workspace', query: { conversationId: '1' } })
    await router.isReady()

    const wrapper = mount(Workspace, {
      global: {
        plugins: [router],
        stubs: workspaceTestStubs
      }
    })

    await flushPromises()

    const confirmButton = wrapper.findAll('button').find(button => button.text().includes('确认需求'))
    expect(confirmButton).toBeDefined()

    await confirmButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.confirmUnderstandingSdac).toHaveBeenCalledWith(1, true)
    expect(apiMocks.designUi).toHaveBeenCalledWith(1)

    wrapper.unmount()
  })
})
