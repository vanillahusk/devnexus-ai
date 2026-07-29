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
