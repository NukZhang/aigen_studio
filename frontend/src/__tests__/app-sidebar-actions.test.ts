import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'
import App from '@/App.vue'

const createTestRouter = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/workspace', component: { template: '<div />' } }]
  })
  await router.push('/workspace')
  await router.isReady()
  return router
}

const mountApp = async () => {
  const router = await createTestRouter()
  const wrapper = mount(App, {
    global: {
      plugins: [router],
      stubs: {
        'el-container': { template: '<div><slot /></div>' },
        'el-aside': { template: '<aside><slot /></aside>' },
        'el-main': { template: '<main><slot /></main>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-tooltip': { template: '<span><slot /></span>' },
        'router-view': { template: '<div />' }
      }
    }
  })
  return { wrapper, router }
}

describe('App sidebar actions', () => {
  it('renders history/new buttons and updates route query', async () => {
    const { wrapper, router } = await mountApp()
    const actionButtons = wrapper.findAll('[data-action]')

    expect(actionButtons).toHaveLength(2)
    expect(actionButtons.map(btn => btn.attributes('data-action'))).toEqual(['new', 'history'])

    await actionButtons[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.action).toBe('new')

    await actionButtons[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.action).toBe('history')
  })
})
