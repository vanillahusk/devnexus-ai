import { computed, ref } from 'vue'
import type {
  AgentStreamEvent,
  AgentStreamPhase,
  AiAgentReply
} from '@/http/ResponseTypes/AiAgentResponseType'
import type { AgentQuery } from '@/services/agent'
import {
  cancelAgent,
  queryAgent,
  streamAgent,
  type AgentStreamHandlers
} from '@/services/agent'
import { runtimeConfig } from '@/config/runtime'

export type AgentMessageStatus = 'done' | 'waiting' | 'error' | 'cancelled'

export interface AgentMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: AgentMessageStatus
  result?: AiAgentReply
  requestId?: string
  streamPhase?: AgentStreamPhase
}

type AgentQueryFunction = (
  request: AgentQuery,
  signal?: AbortSignal
) => Promise<AiAgentReply>

type AgentStreamFunction = (
  request: AgentQuery,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal
) => Promise<void>

type AgentCancelFunction = (requestId: string) => Promise<void>

interface AgentChatOptions {
  streamingEnabled?: boolean
  stream?: AgentStreamFunction
  cancelRemote?: AgentCancelFunction
}

function createSessionId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

export function useAgentChat(
  execute: AgentQueryFunction = queryAgent,
  options: AgentChatOptions = {}
) {
  const streamingEnabled =
    options.streamingEnabled ?? runtimeConfig.agentStreamEnabled
  const executeStream = options.stream ?? streamAgent
  const cancelRemote = options.cancelRemote ?? cancelAgent
  const sessionId = ref(createSessionId())
  const messages = ref<AgentMessage[]>([])
  const running = ref(false)
  const activeController = ref<AbortController | null>(null)

  const hasMessages = computed(() => messages.value.length > 0)

  async function submit(question: string): Promise<void> {
    const normalized = question.trim()
    if (!normalized || running.value) {
      return
    }

    const requestId = Date.now().toString(36)
    const assistantId = `assistant_${requestId}`
    messages.value.push({
      id: `user_${requestId}`,
      role: 'user',
      content: normalized,
      status: 'done'
    })
    messages.value.push({
      id: assistantId,
      role: 'assistant',
      content: '',
      status: 'waiting'
    })

    const controller = new AbortController()
    activeController.value = controller
    running.value = true

    const request = {
      question: normalized,
      sessionId: sessionId.value
    }

    try {
      if (streamingEnabled) {
        await executeStream(
          request,
          {
            onEvent: (event) => applyStreamEvent(assistantId, event)
          },
          controller.signal
        )
        const message = messages.value.find((item) => item.id === assistantId)
        if (!message || controller.signal.aborted) {
          return
        }
        if (!message.result) {
          throw new Error('Agent 流式响应缺少最终结果')
        }
        message.content = message.result.answer
        message.status = message.result.answer ? 'done' : 'error'
        return
      }

      const result = await execute(request, controller.signal)
      const message = messages.value.find((item) => item.id === assistantId)
      if (!message || controller.signal.aborted) {
        return
      }
      message.content = result.answer
      message.result = result
      message.status = result.answer ? 'done' : 'error'
    } catch (error) {
      const message = messages.value.find((item) => item.id === assistantId)
      if (!message) {
        return
      }
      if (controller.signal.aborted) {
        message.status = 'cancelled'
        message.content = message.requestId
          ? '已停止接收回答，并已请求服务端取消任务。'
          : '已停止等待本次回答。'
      } else {
        message.status = 'error'
        message.content =
          error instanceof Error ? error.message : 'Agent 请求失败，请稍后重试。'
      }
    } finally {
      if (activeController.value === controller) {
        activeController.value = null
        running.value = false
      }
    }
  }

  function applyStreamEvent(assistantId: string, event: AgentStreamEvent): void {
    const message = messages.value.find((item) => item.id === assistantId)
    if (!message) {
      return
    }
    if ('requestId' in event && event.requestId) {
      message.requestId = event.requestId
    }
    if (event.type === 'status') {
      message.streamPhase = event.phase
    } else if (event.type === 'delta') {
      message.content += event.text
    } else if (event.type === 'result') {
      message.result = event.result
      message.content = event.result.answer
    } else if (event.type === 'error') {
      throw new Error(event.message || 'Agent 流式请求失败')
    }
  }

  function cancel(): void {
    const pending = messages.value.findLast(
      (message) => message.role === 'assistant' && message.status === 'waiting'
    )
    if (streamingEnabled && pending?.requestId) {
      void cancelRemote(pending.requestId).catch(() => {
        // The local stream is still aborted. A failed cooperative cancel must
        // not leave the browser waiting indefinitely.
      })
    }
    activeController.value?.abort()
  }

  function clear(): void {
    cancel()
    messages.value = []
    sessionId.value = createSessionId()
  }

  return {
    sessionId,
    messages,
    running,
    hasMessages,
    submit,
    cancel,
    clear
  }
}
