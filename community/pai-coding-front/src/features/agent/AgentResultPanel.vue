<template>
  <div class="agent-result">
    <div v-if="result.toolCalls.length" class="tool-summary">
      <div class="result-heading">
        <strong>执行摘要</strong>
        <span>不展示内部思维与工具正文</span>
      </div>
      <div class="tool-summary__items">
        <span
          v-for="tool in result.toolCalls"
          :key="`${tool.toolName}-${tool.citationCount}`"
        >
          <i>✓</i>
          {{ toolLabel(tool.toolName) }}
          · {{ tool.citationCount }} 条引用
        </span>
      </div>
    </div>

    <div v-if="result.citations.length" class="citations">
      <div class="result-heading">
        <strong>引用来源</strong>
        <span>{{ result.citations.length }} 条可信片段</span>
      </div>
      <RouterLink
        v-for="citation in result.citations"
        :key="citation.chunkId"
        :to="`/article/detail/${citation.articleId}`"
        class="citation-card"
        data-testid="agent-citation"
      >
        <div>
          <small>{{ citation.headingPath || '文章片段' }}</small>
          <strong>{{ citation.title }}</strong>
          <p>{{ citation.snippet }}</p>
        </div>
        <span>打开原文 ↗</span>
      </RouterLink>
    </div>

    <div class="usage-bar">
      <span>{{ result.usage.steps }} 步</span>
      <span>{{ result.usage.retrievalCalls }} 次检索</span>
      <span>{{ result.usage.modelCalls }} 次模型调用</span>
      <span>约 {{ result.usage.estimatedTokens }} Tokens</span>
      <code>Trace {{ shortTrace(result.traceId) }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'

defineOptions({
  name: 'AgentResultPanel'
})

defineProps<{
  result: AiAgentReply
}>()

function toolLabel(name: string): string {
  const labels: Record<string, string> = {
    searchKnowledge: '检索社区知识',
    rewriteAndSearch: '改写并再次检索',
    getArticleDetail: '读取文章详情',
    getRelatedArticles: '查询相关文章'
  }
  return labels[name] ?? name
}

function shortTrace(traceId?: string): string {
  if (!traceId) {
    return 'unavailable'
  }
  return traceId.length > 12 ? `${traceId.slice(0, 12)}…` : traceId
}
</script>

<style scoped>
.tool-summary,
.citations {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border-subtle);
}

.result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.result-heading strong {
  color: var(--color-text);
  font-size: 0.72rem;
  font-weight: 720;
}

.result-heading span {
  color: var(--color-text-muted);
  font-size: 0.62rem;
}

.tool-summary__items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.tool-summary__items > span {
  padding: 0.42rem 0.65rem;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: #087463;
  font-size: 0.66rem;
  font-weight: 650;
}

.tool-summary__items i {
  margin-right: 0.2rem;
  font-style: normal;
}

.citation-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  margin-top: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
  color: inherit;
  text-decoration: none;
  transition: 160ms ease;
}

.citation-card:hover {
  border-color: color-mix(in srgb, var(--color-brand) 35%, var(--color-border-subtle));
  transform: translateY(-1px);
}

.citation-card div {
  min-width: 0;
}

.citation-card small,
.citation-card strong,
.citation-card p {
  display: block;
}

.citation-card small {
  color: var(--color-brand-strong);
  font-size: 0.62rem;
  font-weight: 700;
}

.citation-card strong {
  margin-top: var(--space-1);
  color: var(--color-text);
  font-size: 0.76rem;
}

.citation-card p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.7rem;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.citation-card > span {
  flex: none;
  color: var(--color-brand-strong);
  font-size: 0.66rem;
  font-weight: 680;
}

.usage-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-4);
  color: var(--color-text-muted);
  font-size: 0.62rem;
}

.usage-bar code {
  margin-left: auto;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .result-heading,
  .citation-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .usage-bar code {
    width: 100%;
    margin-left: 0;
  }
}
</style>
