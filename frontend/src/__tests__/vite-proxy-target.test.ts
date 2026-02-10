/* @vitest-environment node */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import viteConfig from '../../vite.config'

describe('vite preview proxy target', () => {
  const envKey = 'VITE_PREVIEW_PROXY_TARGET'
  const apiEnvKey = 'VITE_PREVIEW_API_PROXY_TARGET'
  const original = process.env[envKey]
  const apiOriginal = process.env[apiEnvKey]

  beforeEach(() => {
    delete process.env[envKey]
    delete process.env[apiEnvKey]
  })

  afterEach(() => {
    if (original === undefined) {
      delete process.env[envKey]
    } else {
      process.env[envKey] = original
    }
    if (apiOriginal === undefined) {
      delete process.env[apiEnvKey]
    } else {
      process.env[apiEnvKey] = apiOriginal
    }
  })

  it('defaults to localhost preview target when env is unset', () => {
    const config = viteConfig({ mode: 'development' }) as { server?: { proxy?: Record<string, { target?: string }> } }
    const target = config.server?.proxy?.['/__preview__']?.target

    expect(target).toBe('http://localhost:3002')
  })

  it('defaults subapi proxy target to localhost backend preview', () => {
    const config = viteConfig({ mode: 'development' }) as { server?: { proxy?: Record<string, { target?: string }> } }
    const target = config.server?.proxy?.['/subapi']?.target

    expect(target).toBe('http://localhost:8081')
  })
})
