import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import type {
  AgentStreamEvent,
  AiAgentReply
} from '@/http/ResponseTypes/AiAgentResponseType'
import { AI_AGENT_QUERY_URL, AI_AGENT_STREAM_URL } from '@/http/URL'
import { runtimeConfig } from '@/config/runtime'
import { httpClient, readToken } from '@/services/http/client'
import { LOCALSTORAGE_AUTHORIZATION } from '@/constants/LocalStorageConstants'

export interface AgentQuery {
  question: string
  sessionId: string
}

export async function queryAgent(
  request: AgentQuery,
  signal?: AbortSignal
): Promise<AiAgentReply> {
  const response = await httpClient.post<CommonResponse<AiAgentReply>>(
    AI_AGENT_QUERY_URL,
    request,
    { signal }
  )
  return response.data.result
}

export interface AgentStreamHandlers {
  onEvent: (event: AgentStreamEvent) => void
}

interface RawSseEvent {
  event: string
  data: string
}

function apiUrl(path: string): string {
  return `${runtimeConfig.apiBaseUrl}${path}`
}

export function parseSseFrames(buffer: string): {
  events: RawSseEvent[]
  remainder: string
} {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const frames = normalized.split('\n\n')
  const remainder = frames.pop() ?? ''
  const events = frames.flatMap((frame) => {
    let event = 'message'
    const data: string[] = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice('event:'.length).trim()
      } else if (line.startsWith('data:')) {
        data.push(line.slice('data:'.length).trimStart())
      }
    }
    return data.length ? [{ event, data: data.join('\n') }] : []
  })
  return { events, remainder }
}

function decodeEvent(raw: RawSseEvent): AgentStreamEvent | null {
  const supported = new Set([
    'accepted',
    'status',
    'delta',
    'result',
    'error',
    'done'
  ])
  if (!supported.has(raw.event)) {
    return null
  }
  const payload = JSON.parse(raw.data) as Record<string, unknown>
  return { type: raw.event, ...payload } as AgentStreamEvent
}

export async function streamAgent(
  request: AgentQuery,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const headers = new Headers({
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  })
  const token = readToken()
  if (token) {
    headers.set(LOCALSTORAGE_AUTHORIZATION, token)
  }

  const response = await fetch(apiUrl(AI_AGENT_STREAM_URL), {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(request),
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error(`Agent 流式请求失败（HTTP ${response.status}）`)
  }
  if (!response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new Error('Agent 服务未返回 SSE 响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false
  let streamEnded = false
  try {
    while (!streamEnded) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const parsed = parseSseFrames(buffer)
      buffer = parsed.remainder
      for (const raw of parsed.events) {
        const event = decodeEvent(raw)
        if (!event) {
          continue
        }
        handlers.onEvent(event)
        if (event.type === 'done') {
          completed = true
        }
      }
      if (done) {
        streamEnded = true
      }
    }
  } finally {
    reader.releaseLock()
  }
  if (!completed && !signal?.aborted) {
    throw new Error('Agent 流式响应提前结束')
  }
}

export async function cancelAgent(requestId: string): Promise<void> {
  const headers = new Headers()
  const token = readToken()
  if (token) {
    headers.set(LOCALSTORAGE_AUTHORIZATION, token)
  }
  const response = await fetch(
    `${apiUrl(AI_AGENT_STREAM_URL)}/${encodeURIComponent(requestId)}`,
    {
      method: 'DELETE',
      credentials: 'include',
      headers,
      keepalive: true
    }
  )
  if (!response.ok) {
    throw new Error(`Agent 取消请求失败（HTTP ${response.status}）`)
  }
}
