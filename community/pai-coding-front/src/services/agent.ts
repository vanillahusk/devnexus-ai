import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'
import { AI_AGENT_QUERY_URL } from '@/http/URL'
import { httpClient } from '@/services/http/client'

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
