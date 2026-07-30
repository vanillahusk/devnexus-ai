import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'
import { useAgentChat } from '../useAgentChat'

function reply(): AiAgentReply {
  return {
    traceId: 'trace-1',
    mode: 'AGENT',
    answer: 'Outbox 采用至少一次投递。[ref:c1]',
    fallback: false,
    failureCode: '',
    toolCalls: [
      {
        toolName: 'searchKnowledge',
        status: 'SUCCESS',
        citationCount: 1
      }
    ],
    citations: [
      {
        chunkId: 'c1',
        articleId: '12',
        articleVersion: '6',
        title: '可靠消息',
        headingPath: 'Outbox',
        snippet: '状态更新失败会导致重复投递'
      }
    ],
    usage: {
      steps: 2,
      toolCalls: 1,
      retrievalCalls: 1,
      rerankCalls: 1,
      modelCalls: 2,
      estimatedTokens: 620,
      modelName: 'hy3',
      remainingMillis: 18_000
    }
  }
}

describe('useAgentChat', () => {
  it('保存问题、公开回答、工具摘要与引用', async () => {
    const execute = vi.fn().mockResolvedValue(reply())
    const chat = useAgentChat(execute)

    await chat.submit('为什么 Outbox 会重复投递？')

    expect(execute).toHaveBeenCalledOnce()
    expect(chat.messages.value).toHaveLength(2)
    expect(chat.messages.value[1].status).toBe('done')
    expect(chat.messages.value[1].result?.citations[0].articleId).toBe('12')
  })

  it('浏览器取消后明确标记为停止等待', async () => {
    const execute = vi.fn(
      (_, signal?: AbortSignal) =>
        new Promise<AiAgentReply>((_, reject) => {
          signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError'))
          )
        })
    )
    const chat = useAgentChat(execute)

    const pending = chat.submit('测试取消')
    await nextTick()
    chat.cancel()
    await pending

    expect(chat.running.value).toBe(false)
    expect(chat.messages.value[1]).toMatchObject({
      status: 'cancelled',
      content: '已停止等待本次回答。'
    })
  })

  it('请求失败时保留问题并呈现可读错误', async () => {
    const execute = vi.fn().mockRejectedValue(new Error('Agent 服务暂时不可用'))
    const chat = useAgentChat(execute)

    await chat.submit('失败场景')

    expect(chat.messages.value[0].content).toBe('失败场景')
    expect(chat.messages.value[1]).toMatchObject({
      status: 'error',
      content: 'Agent 服务暂时不可用'
    })
    expect(chat.running.value).toBe(false)
  })

  it('真实流事件逐段更新回答，并以 result 作为最终权威结果', async () => {
    const finalReply = reply()
    const stream = vi.fn(async (_, handlers) => {
      handlers.onEvent({
        type: 'accepted',
        requestId: 'req-1',
        traceId: 'trace-1'
      })
      handlers.onEvent({
        type: 'status',
        requestId: 'req-1',
        phase: 'GENERATING'
      })
      handlers.onEvent({
        type: 'delta',
        requestId: 'req-1',
        text: '临时增量'
      })
      handlers.onEvent({
        type: 'result',
        requestId: 'req-1',
        result: finalReply
      })
      handlers.onEvent({
        type: 'done',
        requestId: 'req-1',
        finishReason: 'COMPLETED'
      })
    })
    const chat = useAgentChat(vi.fn(), {
      streamingEnabled: true,
      stream
    })

    await chat.submit('流式问题')

    expect(stream).toHaveBeenCalledOnce()
    expect(chat.messages.value[1]).toMatchObject({
      requestId: 'req-1',
      streamPhase: 'GENERATING',
      content: finalReply.answer,
      result: finalReply,
      status: 'done'
    })
  })

  it('流式等待时同时中断本地连接并发送服务端协作取消', async () => {
    const cancelRemote = vi.fn().mockResolvedValue(undefined)
    const stream = vi.fn(
      (_, handlers, signal?: AbortSignal) =>
        new Promise<void>((_, reject) => {
          handlers.onEvent({
            type: 'accepted',
            requestId: 'req-cancel',
            traceId: 'trace-cancel'
          })
          signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError'))
          )
        })
    )
    const chat = useAgentChat(vi.fn(), {
      streamingEnabled: true,
      stream,
      cancelRemote
    })

    const pending = chat.submit('取消流式问题')
    await nextTick()
    chat.cancel()
    await pending

    expect(cancelRemote).toHaveBeenCalledWith('req-cancel')
    expect(chat.messages.value[1]).toMatchObject({
      status: 'cancelled',
      content: '已停止接收回答，并已请求服务端取消任务。'
    })
  })
})
