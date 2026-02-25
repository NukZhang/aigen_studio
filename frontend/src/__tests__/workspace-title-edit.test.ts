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
  updateConversationTitle: vi.fn(),
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
    updateConversationTitle: apiMocks.updateConversationTitle
  },
  previewApi: {
    startPreview: apiMocks.startPreview,
    stopPreview: apiMocks.stopPreview,
    restartPreview: apiMocks.restartPreview,
    getStatus: apiMocks.getStatus
  }
}))

const mountWorkspace = async () => {
  apiMocks.getNewConversation.mockResolvedValue({
    data: {
      id: 1,
      projectName: '新对话',
      stage: 'NEED_INPUT',
      messages: []
    }
  })
  apiMocks.getActiveConversations.mockResolvedValue({
    data: [
      {
        id: 1,
        projectName: '新对话',
        stage: 'NEED_INPUT',
        messages: [],
        createdAt: new Date().toISOString()
      }
    ]
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
        'el-button': { template: '<button><slot /></button>' },
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

describe('Workspace title editing', () => {
  it('updates conversation title manually', async () => {
    apiMocks.updateConversationTitle.mockResolvedValue({
      data: {
        id: 1,
        projectName: '库存管理系统',
        stage: 'NEED_INPUT',
        messages: []
      }
    })

    const wrapper = await mountWorkspace()

    await wrapper.find('.title-edit-button').trigger('click')
    await flushPromises()

    const input = wrapper.find('.title-edit-input')
    await input.setValue('库存管理系统')

    const saveButton = wrapper.findAll('.title-edit-action').at(0)
    await saveButton?.trigger('click')
    await flushPromises()

    expect(apiMocks.updateConversationTitle).toHaveBeenCalledWith(1, '库存管理系统')
    expect(wrapper.find('.project-title').text()).toBe('库存管理系统')
  })
})
