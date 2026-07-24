export interface AiAssistantReference {
  sourceType: string;
  title: string;
  snippet: string;
  articleId?: string | number | null;
  commentId?: string | number | null;
  configKey?: string | null;
}

export interface AiAssistantHistoryItem {
  question: string;
  answer: string;
  askTime: string;
  route: string;
  degraded: boolean;
}

export interface AiAssistantReply {
  articleId?: string | number | null;
  sessionId: string;
  answer: string;
  route: string;
  degraded: boolean;
  degradeReason?: string | null;
  references: AiAssistantReference[];
  history: AiAssistantHistoryItem[];
}

export const defaultAiAssistantReply: AiAssistantReply = {
  articleId: null,
  sessionId: '',
  answer: '',
  route: 'local',
  degraded: false,
  degradeReason: '',
  references: [],
  history: [],
}
