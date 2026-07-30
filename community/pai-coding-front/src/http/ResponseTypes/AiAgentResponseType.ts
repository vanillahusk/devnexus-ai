export interface AgentToolCallSummary {
  toolName: string
  status: string
  citationCount: number
}

export interface AgentCitation {
  chunkId: string
  articleId: string
  articleVersion: string
  title: string
  headingPath: string
  snippet: string
  retrievalScore?: number | null
  rerankScore?: number | null
}

export interface AgentUsageSummary {
  steps: number
  toolCalls: number
  retrievalCalls: number
  rerankCalls: number
  modelCalls: number
  estimatedTokens: number
  modelName: string
  remainingMillis: number
}

export interface AiAgentReply {
  traceId: string
  mode: 'AGENT' | 'DIRECT' | 'RAG_FALLBACK' | 'CONTROLLED_FAILURE'
  answer: string
  fallback: boolean
  failureCode: string
  toolCalls: AgentToolCallSummary[]
  citations: AgentCitation[]
  usage: AgentUsageSummary
}

export type AgentStreamPhase =
  | 'PLANNING'
  | 'RETRIEVING'
  | 'RERANKING'
  | 'GENERATING'
  | 'FALLBACK'

export type AgentStreamFinishReason =
  | 'COMPLETED'
  | 'CANCELLED'
  | 'CONTROLLED_FAILURE'
  | 'TIMEOUT'

export type AgentStreamEvent =
  | {
      type: 'accepted'
      requestId: string
      traceId: string
    }
  | {
      type: 'status'
      requestId: string
      phase: AgentStreamPhase
    }
  | {
      type: 'delta'
      requestId: string
      text: string
    }
  | {
      type: 'result'
      requestId: string
      result: AiAgentReply
    }
  | {
      type: 'error'
      requestId?: string
      code: string
      message: string
      retryable: boolean
    }
  | {
      type: 'done'
      requestId: string
      finishReason: AgentStreamFinishReason
    }
