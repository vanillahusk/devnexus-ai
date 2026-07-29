import { api } from "@/services/api";
import {
  createStreamResponse,
  type StreamHandlers
} from "@/hooks/useStreamResponse";
import { buildQuery } from "@/utils/helpers";
import { storage } from "@/utils/storage";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export interface ChatStreamRequest {
  question: string;
  conversationId?: string;
  deepThinking?: boolean;
}

export function createChatStream(
  request: ChatStreamRequest,
  handlers: StreamHandlers
) {
  const query = buildQuery({
    question: request.question,
    conversationId: request.conversationId,
    deepThinking: request.deepThinking
  });
  const token = storage.getToken();
  return createStreamResponse(
    {
      url: `${API_BASE_URL}/rag/v3/chat${query}`,
      headers: token ? { Authorization: token } : undefined,
      retryCount: 1
    },
    handlers
  );
}

export async function stopTask(taskId: string) {
  return api.post<void>(`/rag/v3/stop?taskId=${encodeURIComponent(taskId)}`);
}

export async function submitFeedback(messageId: string, vote: number) {
  return api.post<void>(`/conversations/messages/${messageId}/feedback`, {
    vote
  });
}
