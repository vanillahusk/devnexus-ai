<template>
  <HeaderBar />

  <main class="agent-page page-shell">
    <aside class="agent-sidebar">
      <div>
        <p class="agent-sidebar__eyebrow">CONTROLLED AGENT</p>
        <h1>社区知识助手</h1>
        <p class="agent-sidebar__intro">
          只读取已发布社区文章，通过混合检索、精排与引用回答技术问题。
        </p>
      </div>

      <div class="guardrails">
        <h2>服务端硬约束</h2>
        <div v-for="guardrail in guardrails" :key="guardrail.label">
          <span>{{ guardrail.icon }}</span>
          <p><strong>{{ guardrail.label }}</strong><small>{{ guardrail.note }}</small></p>
        </div>
      </div>

      <div class="session-card">
        <span>SESSION</span>
        <code>{{ shortSessionId }}</code>
        <button type="button" @click="chat.clear">新建会话</button>
      </div>
    </aside>

    <section class="agent-workspace">
      <div class="workspace-header">
        <div>
          <span class="workspace-header__status"><i></i> Agent 默认受控运行</span>
          <h2>从社区文章中查找可信答案</h2>
        </div>
        <RouterLink to="/about">查看实现说明 →</RouterLink>
      </div>

      <div ref="messageViewport" class="message-viewport">
        <div v-if="!chat.hasMessages.value" class="welcome-panel">
          <div class="welcome-panel__mark">✦</div>
          <p>你好，我是 DevNexus 知识助手</p>
          <h3>你想了解哪条工程链路？</h3>
          <div class="suggestion-grid">
            <button
              v-for="question in suggestions"
              :key="question"
              type="button"
              @click="ask(question)"
            >
              <span>{{ question }}</span>
              <i>↗</i>
            </button>
          </div>
        </div>

        <article
          v-for="message in chat.messages.value"
          :key="message.id"
          class="message"
          :class="`message--${message.role}`"
        >
          <div class="message__avatar">{{ message.role === 'user' ? '你' : 'D' }}</div>
          <div class="message__body">
            <div class="message__meta">
              <strong>{{ message.role === 'user' ? '你的问题' : 'DevNexus Agent' }}</strong>
              <span v-if="message.result">{{ modeLabel(message.result.mode) }}</span>
            </div>

            <div v-if="message.status === 'waiting'" class="agent-running">
              <span></span><span></span><span></span>
              正在规划并检索可信资料
            </div>
            <p
              v-else
              class="message__content"
              :class="{ 'message__content--error': message.status === 'error' }"
            >
              {{ message.content }}
            </p>

            <AgentResultPanel v-if="message.result" :result="message.result" />
          </div>
        </article>
      </div>

      <form class="composer" @submit.prevent="ask(input)">
        <textarea
          v-model="input"
          :disabled="chat.running.value"
          maxlength="500"
          rows="3"
          placeholder="例如：为什么 Outbox 不能保证绝对只投递一次？"
          @keydown.ctrl.enter.prevent="ask(input)"
        ></textarea>
        <div class="composer__footer">
          <span>{{ input.length }} / 500 · Ctrl + Enter 发送</span>
          <button
            v-if="chat.running.value"
            class="cancel-button"
            type="button"
            @click="chat.cancel"
          >
            停止等待
          </button>
          <button
            v-else
            class="send-button"
            type="submit"
            :disabled="!input.trim()"
          >
            发送问题
            <span>↑</span>
          </button>
        </div>
      </form>
      <p class="composer-note">
        Agent 可能出错，请根据引用原文核对。停止等待只会取消浏览器请求，不代表服务端任务已经撤销。
      </p>
    </section>
  </main>

  <Footer />
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { RouterLink } from 'vue-router'
import HeaderBar from '@/components/layout/HeaderBar.vue'
import Footer from '@/components/layout/Footer.vue'
import { useAgentChat } from '@/features/agent/useAgentChat'
import AgentResultPanel from '@/features/agent/AgentResultPanel.vue'
import type { AiAgentReply } from '@/http/ResponseTypes/AiAgentResponseType'

const chat = useAgentChat()
const input = ref('')
const messageViewport = ref<HTMLElement | null>(null)

const guardrails = [
  { icon: '01', label: '最多 3 步', note: '状态机强制限制' },
  { icon: '02', label: '最多 2 次检索', note: '重复调用自动阻断' },
  { icon: '30s', label: '总超时边界', note: '失败降级到可信 RAG' },
  { icon: 'RO', label: '只读工具', note: '禁止写操作与任意 SQL' }
]

const suggestions = [
  '为什么 Outbox 不能保证绝对只投递一次？',
  '点赞取消操作如何防止旧事件覆盖新状态？',
  '评论链路为什么去掉了 Redis 二级队列？',
  'RAG 全量重建期间如何避免索引回退？'
]

const shortSessionId = computed(() => `${chat.sessionId.value.slice(0, 8)}…`)

async function ask(question: string): Promise<void> {
  if (!question.trim() || chat.running.value) {
    return
  }
  input.value = ''
  await chat.submit(question)
  await nextTick()
  messageViewport.value?.scrollTo({
    top: messageViewport.value.scrollHeight,
    behavior: 'smooth'
  })
}

function modeLabel(mode: AiAgentReply['mode']): string {
  const labels: Record<AiAgentReply['mode'], string> = {
    AGENT: 'Agent',
    DIRECT: '直接回答',
    RAG_FALLBACK: 'RAG 降级',
    CONTROLLED_FAILURE: '受控失败'
  }
  return labels[mode]
}

</script>

<style scoped>
.agent-page {
  display: grid;
  grid-template-columns: 18rem minmax(0, 1fr);
  gap: var(--space-5);
  min-height: calc(100vh - var(--header-height));
  padding-block: var(--space-8);
}

.agent-sidebar,
.agent-workspace {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.agent-sidebar {
  display: flex;
  height: max-content;
  min-height: 39rem;
  flex-direction: column;
  padding: var(--space-6);
}

.agent-sidebar__eyebrow {
  color: var(--color-brand-strong);
  font-size: 0.65rem;
  font-weight: 780;
  letter-spacing: 0.14em;
}

.agent-sidebar h1 {
  margin-top: var(--space-3);
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: 1.65rem;
  font-weight: 760;
  letter-spacing: -0.04em;
}

.agent-sidebar__intro {
  margin-top: var(--space-4);
  color: var(--color-text-muted);
  font-size: 0.8rem;
  line-height: 1.7;
}

.guardrails {
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border-subtle);
}

.guardrails h2 {
  margin-bottom: var(--space-4);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  font-weight: 700;
}

.guardrails > div {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding-block: var(--space-3);
}

.guardrails > div > span {
  display: grid;
  width: 2.1rem;
  height: 2.1rem;
  flex: none;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: 0.58rem;
  font-weight: 780;
}

.guardrails strong,
.guardrails small {
  display: block;
}

.guardrails strong {
  color: var(--color-text-secondary);
  font-size: 0.76rem;
  font-weight: 700;
}

.guardrails small {
  margin-top: 0.15rem;
  color: var(--color-text-muted);
  font-size: 0.65rem;
}

.session-card {
  margin-top: auto;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.session-card span,
.session-card code {
  display: block;
}

.session-card span {
  color: var(--color-text-muted);
  font-size: 0.55rem;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.session-card code {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 0.75rem;
}

.session-card button {
  width: 100%;
  margin-top: var(--space-4);
  padding: 0.58rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  background: white;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 0.7rem;
  font-weight: 680;
}

.agent-workspace {
  display: flex;
  min-width: 0;
  min-height: 42rem;
  flex-direction: column;
  overflow: hidden;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--color-border-subtle);
}

.workspace-header__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: #087463;
  font-size: 0.62rem;
  font-weight: 700;
}

.workspace-header__status i {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 99px;
  background: var(--color-accent);
}

.workspace-header h2 {
  margin-top: var(--space-1);
  color: var(--color-text);
  font-size: 0.95rem;
  font-weight: 700;
}

.workspace-header a {
  color: var(--color-brand-strong);
  font-size: 0.68rem;
  font-weight: 680;
  text-decoration: none;
}

.message-viewport {
  flex: 1;
  max-height: 62vh;
  overflow-y: auto;
  padding: var(--space-8);
  background:
    radial-gradient(circle at 50% 0, rgb(99 91 255 / 5%), transparent 28rem),
    #fbfbfd;
}

.welcome-panel {
  display: grid;
  min-height: 26rem;
  place-items: center;
  align-content: center;
  text-align: center;
}

.welcome-panel__mark {
  display: grid;
  width: 3.4rem;
  height: 3.4rem;
  place-items: center;
  border-radius: var(--radius-lg);
  background: linear-gradient(145deg, var(--color-brand), #8b7fff);
  box-shadow: 0 16px 32px rgb(99 91 255 / 25%);
  color: white;
  font-size: 1.35rem;
}

.welcome-panel > p {
  margin-top: var(--space-5);
  color: var(--color-brand-strong);
  font-size: 0.68rem;
  font-weight: 720;
}

.welcome-panel h3 {
  margin-top: var(--space-2);
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: clamp(1.65rem, 3vw, 2.35rem);
  font-weight: 760;
  letter-spacing: -0.04em;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  width: min(100%, 42rem);
  margin-top: var(--space-8);
}

.suggestion-grid button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: white;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 0.75rem;
  line-height: 1.5;
  text-align: left;
  transition: 160ms ease;
}

.suggestion-grid button:hover {
  border-color: color-mix(in srgb, var(--color-brand) 35%, white);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.suggestion-grid i {
  color: var(--color-brand);
  font-style: normal;
}

.message {
  display: flex;
  gap: var(--space-4);
  width: min(100%, 48rem);
  margin-inline: auto;
}

.message + .message {
  margin-top: var(--space-8);
}

.message__avatar {
  display: grid;
  width: 2rem;
  height: 2rem;
  flex: none;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-text);
  color: white;
  font-size: 0.7rem;
  font-weight: 780;
}

.message--assistant .message__avatar {
  background: linear-gradient(145deg, var(--color-brand), #8b7fff);
}

.message__body {
  min-width: 0;
  flex: 1;
}

.message__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.message__meta strong {
  color: var(--color-text);
  font-size: 0.75rem;
  font-weight: 720;
}

.message__meta span {
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: 0.55rem;
  font-weight: 720;
}

.message__content {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.85;
  white-space: pre-wrap;
}

.message--user .message__content {
  display: inline-block;
  padding: 0.7rem 0.9rem;
  border-radius: 0 var(--radius-md) var(--radius-md) var(--radius-md);
  background: var(--color-surface-muted);
}

.message__content--error {
  color: var(--color-danger);
}

.agent-running {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: var(--space-4);
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.agent-running span {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 99px;
  background: var(--color-brand);
  animation: pulse 1s infinite alternate;
}

.agent-running span:nth-child(2) {
  animation-delay: 160ms;
}

.agent-running span:nth-child(3) {
  margin-right: var(--space-2);
  animation-delay: 320ms;
}

@keyframes pulse {
  to {
    opacity: 0.25;
    transform: translateY(-3px);
  }
}

.composer {
  margin: var(--space-4) var(--space-6) 0;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: white;
  box-shadow: 0 10px 28px rgb(24 32 51 / 7%);
}

.composer:focus-within {
  border-color: color-mix(in srgb, var(--color-brand) 50%, white);
}

.composer textarea {
  width: 100%;
  min-height: 4.5rem;
  padding: var(--space-4);
  border: 0;
  background: transparent;
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.82rem;
  line-height: 1.6;
  resize: none;
}

.composer textarea:focus {
  border: 0;
  outline: 0;
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 0 var(--space-3) var(--space-3) var(--space-4);
}

.composer__footer > span {
  color: var(--color-text-muted);
  font-size: 0.58rem;
}

.send-button,
.cancel-button {
  padding: 0.58rem 0.75rem;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.7rem;
  font-weight: 700;
}

.send-button {
  background: var(--color-text);
  color: white;
}

.send-button span {
  margin-left: var(--space-2);
}

.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.cancel-button {
  background: #fff0f0;
  color: var(--color-danger);
}

.composer-note {
  padding: var(--space-2) var(--space-6) var(--space-5);
  color: var(--color-text-muted);
  font-size: 0.58rem;
  text-align: center;
}

@media (max-width: 900px) {
  .agent-page {
    grid-template-columns: 1fr;
  }

  .agent-sidebar {
    min-height: 0;
  }

  .guardrails {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 0 var(--space-4);
  }

  .guardrails h2 {
    grid-column: 1 / -1;
  }

  .session-card {
    margin-top: var(--space-5);
  }
}

@media (max-width: 600px) {
  .agent-page {
    padding-top: var(--space-4);
  }

  .guardrails,
  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .workspace-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .message-viewport {
    padding: var(--space-5) var(--space-4);
  }

  .message {
    gap: var(--space-3);
  }

  .composer {
    margin-inline: var(--space-3);
  }
}
</style>
