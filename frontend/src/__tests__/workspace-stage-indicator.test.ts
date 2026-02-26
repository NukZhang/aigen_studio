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

const buildConversation = (stage: string) => ({
  data: {
    id: 1,
    projectName: 'Test Project',
    stage,
    messages: []
  }
})

const mountWorkspaceWithStage = async (stage: string) => {
  apiMocks.getNewConversation.mockResolvedValueOnce(buildConversation(stage))
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

describe('Workspace stage indicator', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    apiMocks.getNewConversation.mockReset()
    apiMocks.getActiveConversations.mockReset()
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('does not include service start/preview stages', async () => {
    const wrapper = await mountWorkspaceWithStage('PREVIEWING')
    const labels = wrapper.findAll('.stage-item .stage-label').map(node => node.text())

    expect(labels).toEqual(['需求输入', '理解需求', '生成UI', '生成代码', '确认启动'])

    wrapper.unmount()
  })

  it('shows generation failed status at the end', async () => {
    const wrapper = await mountWorkspaceWithStage('FAILED')
    const items = wrapper.findAll('.stage-item')
    const lastItem = items[items.length - 1]
    const lastLabel = lastItem.find('.stage-label').text()

    expect(lastLabel).toBe('生成失败')
    expect(lastItem.classes()).toContain('failed')

    wrapper.unmount()
  })

  it('shows generation completed status at the end', async () => {
    const wrapper = await mountWorkspaceWithStage('COMPLETED')
    const items = wrapper.findAll('.stage-item')
    const lastLabel = items[items.length - 1].find('.stage-label').text()

    expect(lastLabel).toBe('生成完成')

    wrapper.unmount()
  })

  it('treats UI_READY as generating UI stage', async () => {
    const wrapper = await mountWorkspaceWithStage('UI_READY')
    const items = wrapper.findAll('.stage-item')
    const labels = items.map(item => item.find('.stage-label').text())
    const uiIndex = labels.indexOf('生成UI')

    expect(uiIndex).toBeGreaterThanOrEqual(0)
    expect(items[uiIndex].classes()).toContain('active')

    wrapper.unmount()
  })

  it('treats CLARIFYING as understanding stage', async () => {
    const wrapper = await mountWorkspaceWithStage('CLARIFYING')
    const items = wrapper.findAll('.stage-item')
    const labels = items.map(item => item.find('.stage-label').text())
    const understandingIndex = labels.indexOf('理解需求')

    expect(understandingIndex).toBeGreaterThanOrEqual(0)
    expect(items[understandingIndex].classes()).toContain('active')

    wrapper.unmount()
  })
})
