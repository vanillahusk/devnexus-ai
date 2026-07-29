import { computed, ref } from 'vue'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'
import type { AgentQuery } from '@/services/agent'
import { queryAgent } from '@/services/agent'

export type AgentMessageStatus = 'done' | 'waiting' | 'error' | 'cancelled'

export interface AgentMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: AgentMessageStatus
  result?: AiAgentReply
}

type AgentQueryFunction = (
  request: AgentQuery,
  signal?: AbortSignal
) => Promise<AiAgentReply>

function createSessionId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

export function useAgentChat(execute: AgentQueryFunction = queryAgent) {
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

    try {
      const result = await execute(
        {
          question: normalized,
          sessionId: sessionId.value
        },
        controller.signal
      )
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
        message.content = '已停止等待本次回答。'
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

  function cancel(): void {
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
