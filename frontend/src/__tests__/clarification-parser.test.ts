import { describe, expect, it } from 'vitest'
import { parseClarificationPayload } from '@/utils/clarification'

describe('parseClarificationPayload', () => {
  it('parses clarification payload from ai understanding text', () => {
    const content = `
<REQUIREMENT_GATE>
NEXT_ACTION: ASK_CLARIFICATION
MISSING_INFO_COUNT: 1
</REQUIREMENT_GATE>

<CLARIFICATION_PAYLOAD>
{
  "questions": [
    {
      "id": "platform",
      "question": "请选择平台",
      "options": ["微信小程序", "H5"]
    }
  ]
}
</CLARIFICATION_PAYLOAD>
    `

    const result = parseClarificationPayload(content)
    expect(result).not.toBeNull()
    expect(result?.questions).toHaveLength(1)
    expect(result?.questions[0].id).toBe('platform')
    expect(result?.questions[0].question).toBe('请选择平台')
    expect(result?.questions[0].options).toEqual(['微信小程序', 'H5'])
  })

  it('returns null when payload block is missing', () => {
    const result = parseClarificationPayload('普通理解文本')
    expect(result).toBeNull()
  })

  it('filters invalid questions and keeps valid items', () => {
    const content = `
<CLARIFICATION_PAYLOAD>
{
  "questions": [
    { "id": "", "question": "", "options": [] },
    { "id": "ranking", "question": "排行榜规则", "options": ["按发送量", "按收到量"] }
  ]
}
</CLARIFICATION_PAYLOAD>
    `

    const result = parseClarificationPayload(content)
    expect(result).not.toBeNull()
    expect(result?.questions).toHaveLength(1)
    expect(result?.questions[0].id).toBe('ranking')
  })
})
