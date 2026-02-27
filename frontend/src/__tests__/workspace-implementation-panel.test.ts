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

const codeGeneratingConversation = {
  data: {
    id: 1,
    projectName: 'Test Project',
    stage: 'CODE_GENERATING',
    evidenceManifestPath: '/tmp/evidence.json',
    implementationPlanJson: JSON.stringify({
      scope: ['A'],
      verifications: ['cd backend && mvn test']
    }),
    messages: []
  }
}

describe('Workspace implementation panel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    apiMocks.getNewConversation.mockReset()
    apiMocks.getActiveConversations.mockReset()
    apiMocks.getNewConversation.mockResolvedValue(codeGeneratingConversation)
    apiMocks.getActiveConversations.mockResolvedValue({ data: [] })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('shows code generating status after evidence is created', async () => {
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

    expect(wrapper.text()).toContain('Evidence 已生成，正在生成代码，请等待完成。')
    expect(wrapper.text()).toContain('代码生成中')
    expect(wrapper.text()).not.toContain('生成 Evidence')

    wrapper.unmount()
  })
})
