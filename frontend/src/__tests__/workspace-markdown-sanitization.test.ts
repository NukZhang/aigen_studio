import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import Workspace from '@/views/Workspace.vue'

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

const buildConversation = (content: string) => ({
  data: {
    id: 1,
    projectName: 'Test Project',
    stage: 'UNDERSTANDING',
    messages: [
      {
        id: 101,
        role: 'assistant',
        content,
        timestamp: new Date().toISOString(),
        senderName: 'AI'
      }
    ]
  }
})

const mountWorkspace = async (content: string) => {
  apiMocks.getNewConversation.mockResolvedValueOnce(buildConversation(content))
  apiMocks.getActiveConversations.mockResolvedValue({ data: [] })

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/workspace', component: Workspace }]
  })
  await router.push({ path: '/workspace', query: { conversationId: '1' } })
  await router.isReady()

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

  await flushPromises()
  return wrapper
}

describe('Workspace markdown sanitization', () => {
  it('does not render style tags from message content', async () => {
    const wrapper = await mountWorkspace('<style>.app-container{max-width:480px}</style><p>Hello</p>')

    const styleTag = wrapper.find('.content-text style')
    expect(styleTag.exists()).toBe(false)

    wrapper.unmount()
  })
})
