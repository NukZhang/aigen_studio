import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import Workspace from '@/views/Workspace.vue'
import { workspaceTestStubs } from './helpers/workspace-test-stubs'

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
      stubs: workspaceTestStubs
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
