import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import Workspace from '@/views/Workspace.vue'

vi.mock('marked', () => ({
  marked: (text: string) => text
}))

const apiMocks = vi.hoisted(() => ({
  getNewConversation: vi.fn(),
  getActiveConversations: vi.fn(),
  createNewConversation: vi.fn(),
  sendMessageToNewConversation: vi.fn(),
  confirmUnderstanding: vi.fn(),
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
    confirmUnderstanding: apiMocks.confirmUnderstanding
  },
  previewApi: {
    startPreview: apiMocks.startPreview,
    stopPreview: apiMocks.stopPreview,
    restartPreview: apiMocks.restartPreview,
    getStatus: apiMocks.getStatus
  }
}))

const conversationSnapshot = (message: string) => ({
  data: {
    id: 1,
    projectName: 'Test Project',
    stage: 'NEED_INPUT',
    messages: [
      {
        id: 1,
        role: 'assistant',
        content: message,
        timestamp: new Date().toISOString(),
        senderName: 'AI'
      }
    ]
  }
})

const createRouterWithConversation = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/workspace', component: Workspace }]
  })
  await router.push({ path: '/workspace', query: { conversationId: '1' } })
  await router.isReady()
  return router
}

const createWrapper = async () => {
  const router = await createRouterWithConversation()
  const wrapper = mount(Workspace, {
    global: {
      plugins: [router],
      stubs: {
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-drawer': { template: '<div><slot /></div>' },
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot /></div>' },
        'el-input': { template: '<textarea />' },
        PreviewPanel: { template: '<div />' },
        FileBrowser: { template: '<div />' },
        CodeEditor: { template: '<div />' }
      }
    }
  })
  return { wrapper, router }
}

describe('Workspace auto refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    apiMocks.getNewConversation.mockReset()
    apiMocks.getActiveConversations.mockReset()
    apiMocks.getActiveConversations.mockResolvedValue({ data: [] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('refreshes conversation content without page reload', async () => {
    apiMocks.getNewConversation
      .mockResolvedValueOnce(conversationSnapshot('first'))
      .mockResolvedValueOnce(conversationSnapshot('second'))

    const { wrapper } = await createWrapper()

    await flushPromises()
    expect(apiMocks.getNewConversation).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('first')

    vi.advanceTimersByTime(3000)
    await flushPromises()

    expect(apiMocks.getNewConversation).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('second')
  })
})
