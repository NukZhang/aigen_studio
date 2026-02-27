import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
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

const clarificationUnderstanding = `
<REQUIREMENT_GATE>
NEXT_ACTION: ASK_CLARIFICATION
MISSING_INFO_COUNT: 2
</REQUIREMENT_GATE>
<CLARIFICATION_PAYLOAD>
{
  "questions": [
    {
      "id": "platform",
      "question": "请选择目标平台",
      "options": ["微信小程序", "H5"]
    },
    {
      "id": "ranking",
      "question": "请选择排行榜规则",
      "options": ["按收到量", "按发送量"]
    }
  ]
}
</CLARIFICATION_PAYLOAD>
`

type ConversationOverrides = {
  currentQuestionIndex?: number
  answeredQuestionIds?: string[]
}

const mountWorkspace = async (overrides: ConversationOverrides = {}) => {
  const conversationData = {
    id: 1,
    projectName: '春节祝福',
    stage: 'CLARIFYING',
    aiUnderstanding: clarificationUnderstanding,
    currentQuestionIndex: 1,
    answeredQuestionIds: ['platform'],
    messages: [],
    ...overrides
  }

  apiMocks.getNewConversation
    .mockResolvedValueOnce({
      data: conversationData
    })
    .mockResolvedValue({
      data: conversationData
    })
  apiMocks.getActiveConversations.mockResolvedValue({ data: [] })
  apiMocks.sendMessageToNewConversation.mockResolvedValue({
    data: {
      id: 200,
      role: 'assistant',
      content: '收到补充信息',
      timestamp: new Date().toISOString(),
      senderName: 'AI 开发者'
    }
  })

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

describe('Workspace clarifying panel', () => {
  it('renders clarification panel in a scrollable layout so submit stays reachable', async () => {
    const wrapper = await mountWorkspace({
      currentQuestionIndex: 0,
      answeredQuestionIds: []
    })

    const panel = wrapper.find('.clarifying-panel')
    expect(panel.exists()).toBe(true)
    expect(panel.classes()).toContain('clarifying-panel-scrollable')

    const questionList = wrapper.find('.clarifying-question-list')
    expect(questionList.exists()).toBe(true)
    expect(questionList.classes()).toContain('clarifying-question-scroll')

    const actions = wrapper.find('.clarifying-batch-actions')
    expect(actions.exists()).toBe(true)
    expect(actions.classes()).toContain('clarifying-batch-actions-sticky')
  })

  it('renders all clarification questions and submits batch answers once', async () => {
    const wrapper = await mountWorkspace({
      currentQuestionIndex: 0,
      answeredQuestionIds: []
    })

    const panel = wrapper.find('.clarifying-panel')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('请选择目标平台')
    expect(panel.text()).toContain('请选择排行榜规则')
    const optionButtons = wrapper.findAll('.clarifying-option-button')
    expect(optionButtons.length).toBeGreaterThanOrEqual(4)

    await optionButtons.find(button => button.text().includes('微信小程序'))?.trigger('click')
    await optionButtons.find(button => button.text().includes('按收到量'))?.trigger('click')

    const submitButton = wrapper.find('.clarifying-batch-submit')
    expect(submitButton.exists()).toBe(true)
    await submitButton.trigger('click')
    await flushPromises()

    expect(apiMocks.sendMessageToNewConversation).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        content: expect.stringContaining('澄清回答：请选择目标平台\n答案：微信小程序')
      })
    )
    expect(apiMocks.sendMessageToNewConversation.mock.calls[0][1].content)
      .toContain('澄清回答：请选择排行榜规则\n答案：按收到量')
  })

  it('disables batch submit when some questions are unanswered', async () => {
    const wrapper = await mountWorkspace({
      currentQuestionIndex: undefined,
      answeredQuestionIds: ['platform']
    })

    const submitButton = wrapper.find('.clarifying-batch-submit')
    expect(submitButton.exists()).toBe(true)
    expect((submitButton.element as HTMLButtonElement).disabled).toBe(true)
  })
})
