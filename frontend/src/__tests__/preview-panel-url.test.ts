import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import PreviewPanel from '@/views/PreviewPanel.vue'

const apiMocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  startPreview: vi.fn(),
  stopPreview: vi.fn(),
  restartPreview: vi.fn()
}))

vi.mock('@/api/job', () => ({
  previewApi: {
    getStatus: apiMocks.getStatus,
    startPreview: apiMocks.startPreview,
    stopPreview: apiMocks.stopPreview,
    restartPreview: apiMocks.restartPreview
  }
}))

describe('PreviewPanel url resolution', () => {
  const originalLocation = window.location

  beforeEach(() => {
    apiMocks.getStatus.mockReset()
    apiMocks.startPreview.mockReset()
    apiMocks.stopPreview.mockReset()
    apiMocks.restartPreview.mockReset()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      configurable: true
    })
  })

  it('uses proxy path when preview proxy is enabled', async () => {
    vi.stubEnv('VITE_PREVIEW_PROXY', 'true')
    Object.defineProperty(window, 'location', {
      value: new URL('https://dev-host:3000'),
      configurable: true
    })

    apiMocks.getStatus.mockResolvedValueOnce({
      data: {
        conversationId: 1,
        running: true,
        frontendRunning: true,
        backendRunning: false,
        frontendPort: 3002,
        backendPort: 8081,
        frontendUrl: 'http://localhost:3002',
        backendUrl: null
      }
    })

    const wrapper = mount(PreviewPanel, {
      props: { conversationId: 1 },
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' }
        }
      }
    })

    await flushPromises()

    expect(wrapper.find('.url').text()).toBe('https://dev-host:3000/__preview__/')

    wrapper.unmount()
  })

  it('defaults to proxy path when env flag is not set', async () => {
    vi.stubEnv('VITE_PREVIEW_PROXY', '')
    Object.defineProperty(window, 'location', {
      value: new URL('http://dev-host:3000'),
      configurable: true
    })

    apiMocks.getStatus.mockResolvedValueOnce({
      data: {
        conversationId: 1,
        running: true,
        frontendRunning: true,
        backendRunning: false,
        frontendPort: 3002,
        backendPort: 8081,
        frontendUrl: 'http://localhost:3002',
        backendUrl: null
      }
    })

    const wrapper = mount(PreviewPanel, {
      props: { conversationId: 1 },
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' }
        }
      }
    })

    await flushPromises()

    expect(wrapper.find('.url').text()).toBe('http://dev-host:3000/__preview__/')

    wrapper.unmount()
  })
})
