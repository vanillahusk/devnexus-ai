import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AgentResultPanel from '../AgentResultPanel.vue'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'

const result: AiAgentReply = {
  traceId: 'trace-1234567890',
  mode: 'AGENT',
  answer: 'Outbox 采用至少一次投递。',
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
      chunkId: 'chunk-1',
      articleId: '42',
      articleVersion: '7',
      title: 'Outbox 可靠消息设计',
      headingPath: '重复投递',
      snippet: '发送成功后状态更新失败，消息可能再次投递。'
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

describe('AgentResultPanel', () => {
  it('渲染低敏工具摘要、引用链接和用量信息', () => {
    const wrapper = mount(AgentResultPanel, {
      props: { result },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('检索社区知识')
    expect(wrapper.text()).toContain('Outbox 可靠消息设计')
    expect(wrapper.text()).toContain('620 Tokens')
    expect(wrapper.get('[data-testid="agent-citation"]').attributes('href')).toBe(
      '/article/detail/42'
    )
    expect(wrapper.text()).not.toContain('system prompt')
  })
})
