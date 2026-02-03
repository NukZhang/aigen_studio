/* @vitest-environment node */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import viteConfig from '../../vite.config'

describe('vite preview proxy target', () => {
  const envKey = 'VITE_PREVIEW_PROXY_TARGET'
  const original = process.env[envKey]

  beforeEach(() => {
    delete process.env[envKey]
  })

  afterEach(() => {
    if (original === undefined) {
      delete process.env[envKey]
      return
    }
    process.env[envKey] = original
  })

  it('defaults to localhost preview target when env is unset', () => {
    const config = viteConfig({ mode: 'development' }) as { server?: { proxy?: Record<string, { target?: string }> } }
    const target = config.server?.proxy?.['/__preview__']?.target

    expect(target).toBe('http://localhost:3002')
  })
})
