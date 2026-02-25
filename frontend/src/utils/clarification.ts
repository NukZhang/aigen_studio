export interface ClarificationQuestion {
  id: string
  question: string
  options: string[]
}

export interface ClarificationPayload {
  questions: ClarificationQuestion[]
}

const CLARIFICATION_PAYLOAD_PATTERN = /<CLARIFICATION_PAYLOAD>\s*([\s\S]*?)\s*<\/CLARIFICATION_PAYLOAD>/i

function normalizeQuestion(raw: any, index: number): ClarificationQuestion | null {
  if (!raw || typeof raw !== 'object') {
    return null
  }

  const question = typeof raw.question === 'string' ? raw.question.trim() : ''
  if (!question) {
    return null
  }

  const idValue = typeof raw.id === 'string' ? raw.id.trim() : ''
  const id = idValue || `q${index + 1}`

  const options = Array.isArray(raw.options)
    ? raw.options
      .map(option => typeof option === 'string' ? option.trim() : '')
      .filter(option => option.length > 0)
    : []

  return { id, question, options }
}

export function parseClarificationPayload(content: string | null | undefined): ClarificationPayload | null {
  const source = typeof content === 'string' ? content : ''
  const match = source.match(CLARIFICATION_PAYLOAD_PATTERN)
  if (!match || !match[1]) {
    return null
  }

  try {
    const parsed = JSON.parse(match[1].trim())
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.questions)) {
      return null
    }

    const questions = parsed.questions
      .map((item: unknown, index: number) => normalizeQuestion(item, index))
      .filter((item: ClarificationQuestion | null): item is ClarificationQuestion => item !== null)

    if (questions.length === 0) {
      return null
    }

    return { questions }
  } catch (error) {
    return null
  }
}

export function stripClarificationPayload(content: string | null | undefined): string {
  const source = typeof content === 'string' ? content : ''
  return source.replace(CLARIFICATION_PAYLOAD_PATTERN, '').trim()
}
