import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
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

const mountWorkspace = async () => {
  apiMocks.getNewConversation
    .mockResolvedValueOnce({
      data: {
        id: 1,
        projectName: '春节祝福',
        stage: 'CLARIFYING',
        aiUnderstanding: clarificationUnderstanding,
        currentQuestionIndex: 1,
        answeredQuestionIds: ['platform'],
        messages: []
      }
    })
    .mockResolvedValue({
      data: {
        id: 1,
        projectName: '春节祝福',
        stage: 'CLARIFYING',
        aiUnderstanding: clarificationUnderstanding,
        currentQuestionIndex: 1,
        answeredQuestionIds: ['platform'],
        messages: []
      }
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
      stubs: {
        'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-drawer': { template: '<div><slot /></div>' },
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot /></div>' },
        'el-input': {
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        PreviewPanel: { template: '<div />' },
        FileBrowser: { template: '<div />' },
        CodeEditor: { template: '<div />' },
        UIPrototypePanel: { template: '<div />' }
      }
    }
  })

  await flushPromises()
  return wrapper
}

describe('Workspace clarifying panel', () => {
  it('renders current clarification question from backend index and sends selected answer', async () => {
    const wrapper = await mountWorkspace()

    const panel = wrapper.find('.clarifying-panel')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('请选择排行榜规则')
    expect(panel.text()).not.toContain('请选择目标平台')

    const optionButton = wrapper.find('.clarifying-option-button')
    expect(optionButton.exists()).toBe(true)
    expect(optionButton.text()).toContain('按收到量')

    await optionButton.trigger('click')
    await flushPromises()

    expect(apiMocks.sendMessageToNewConversation).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        content: expect.stringContaining('按收到量'),
        clarificationQuestionId: 'ranking'
      })
    )
  })
})
