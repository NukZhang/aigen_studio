import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const workspacePath = resolve(process.cwd(), 'src/views/Workspace.vue')
const workspaceSource = readFileSync(workspacePath, 'utf-8')

const matchBlock = (selector: string) => {
  const regex = new RegExp(`${selector}\\s*\\{[\\s\\S]*?\\}`, 'm')
  const match = workspaceSource.match(regex)
  expect(match, `Expected to find CSS block for ${selector}`).not.toBeNull()
  return match![0]
}

describe('Workspace layout width containment', () => {
  it('keeps the chat panel from expanding horizontally', () => {
    const chatPanelBlock = matchBlock('\\.chat-panel')
    expect(chatPanelBlock).toMatch(/overflow:\s*hidden/)

    const chatHistoryBlock = matchBlock('\\.chat-history')
    expect(chatHistoryBlock).toMatch(/overflow-x:\s*hidden/)
  })

  it('wraps tool call pre blocks to avoid width overflow', () => {
    const toolPreBlock = workspaceSource.match(/\.tool-arguments pre,[\s\S]*?\}/)
    expect(toolPreBlock, 'Expected tool call pre block').not.toBeNull()
    expect(toolPreBlock![0]).toMatch(/max-width:\s*100%/)
    expect(toolPreBlock![0]).toMatch(/white-space:\s*pre-wrap/)
    expect(toolPreBlock![0]).toMatch(/word-break:\s*break-word/)
  })

  it('prevents message content from forcing width expansion', () => {
    const messageContentBlock = matchBlock('\\.message-content')
    expect(messageContentBlock).toMatch(/max-width:\s*100%/)
    expect(messageContentBlock).toMatch(/overflow:\s*hidden/)
  })
})
