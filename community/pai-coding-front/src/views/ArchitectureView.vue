<template>
  <HeaderBar />

  <main class="architecture-page">
    <section class="architecture-hero page-shell">
      <div>
        <p class="eyebrow">SYSTEM ARCHITECTURE</p>
        <h1>组件只是手段，<br />边界和恢复路径才是架构。</h1>
      </div>
      <div class="architecture-hero__summary">
        <p>
          DevNexus AI 保留社区主业务的聚合边界，只拆出认证、消息与知识能力。
          同步调用有超时和降级，异步事件具备幂等、版本控制和失败补偿。
        </p>
        <RouterLink to="/evidence">查看验证证据 <span>→</span></RouterLink>
      </div>
    </section>

    <section class="system-canvas page-shell" aria-label="DevNexus AI 完整系统架构">
      <div class="system-canvas__entry">
        <span>PUBLIC CLIENT</span>
        <strong>Vue Community & Portfolio</strong>
        <small>社区浏览 · AI 助手 · 作品集</small>
      </div>
      <div class="connector"><i></i><span>HTTP · Trace ID</span><i></i></div>
      <div class="system-canvas__gateway">
        <span>UNIFIED ENTRY</span>
        <strong>Spring Cloud Gateway</strong>
        <small>路由 · Token 校验 · Sentinel 限流与降级</small>
      </div>
      <div class="connector connector--branch"><i></i><span>同步调用与异步事件分离</span><i></i></div>
      <div class="service-grid">
        <article v-for="service in services" :key="service.title" :class="{ accent: service.accent }">
          <span>{{ service.eyebrow }}</span>
          <strong>{{ service.title }}</strong>
          <small>{{ service.description }}</small>
          <ul>
            <li v-for="item in service.items" :key="item">{{ item }}</li>
          </ul>
        </article>
      </div>
      <div class="infra-row">
        <span v-for="item in infrastructure" :key="item.name">
          <small>{{ item.type }}</small>
          <strong>{{ item.name }}</strong>
        </span>
      </div>
      <div class="observability-row">
        <strong>OBSERVABILITY</strong>
        <span>Micrometer</span><i>·</i>
        <span>Prometheus</span><i>·</i>
        <span>Grafana</span><i>·</i>
        <span>SkyWalking</span><i>·</i>
        <span>Dynamic TP</span>
      </div>
    </section>

    <section class="decision-section page-shell">
      <div class="section-heading">
        <p class="eyebrow">CORE FLOWS</p>
        <h2>四条可以完整解释的工程链路</h2>
        <span>每条链路都从业务问题出发，并明确事实源、失败入口和验证边界。</span>
      </div>
      <div class="flow-list">
        <article v-for="flow in flows" :key="flow.index">
          <div class="flow-list__head">
            <span>{{ flow.index }}</span>
            <div><small>{{ flow.problem }}</small><h3>{{ flow.title }}</h3></div>
          </div>
          <div class="flow-list__path">
            <template v-for="(step, index) in flow.steps" :key="step">
              <span>{{ step }}</span>
              <i v-if="index < flow.steps.length - 1">→</i>
            </template>
          </div>
          <p>{{ flow.result }}</p>
        </article>
      </div>
    </section>

    <section class="choices-section page-shell">
      <div>
        <p class="eyebrow">WHY THIS DESIGN</p>
        <h2>刻意没有做的事情</h2>
      </div>
      <div class="choice-grid">
        <article v-for="choice in choices" :key="choice.title">
          <span>×</span>
          <div>
            <h3>{{ choice.title }}</h3>
            <p>{{ choice.description }}</p>
          </div>
        </article>
      </div>
    </section>
  </main>

  <Footer />
  <LoginDialog :clicked="loginDialogClicked" />
</template>

<script setup lang="ts">
import { provide, ref } from 'vue'
import { RouterLink } from 'vue-router'
import HeaderBar from '@/components/layout/HeaderBar.vue'
import Footer from '@/components/layout/Footer.vue'
import LoginDialog from '@/components/dialog/LoginDialog.vue'

const services = [
  {
    eyebrow: 'IDENTITY',
    title: 'Auth Service',
    description: '登录、Token 与用户身份',
    items: ['Nacos', 'OpenFeign']
  },
  {
    eyebrow: 'BUSINESS CORE',
    title: 'Community Service',
    description: '文章、评论、点赞与用户主页',
    items: ['MySQL', 'Redis', 'Outbox']
  },
  {
    eyebrow: 'ASYNC BOUNDARY',
    title: 'Message Service',
    description: '通知消费、查询与已读状态',
    items: ['RocketMQ', '幂等投影']
  },
  {
    eyebrow: 'KNOWLEDGE',
    title: 'RAG & Agent Service',
    description: '索引、检索、精排与受控工具',
    items: ['BM25', 'Dense', 'Reranker'],
    accent: true
  }
]

const infrastructure = [
  { type: 'FACT SOURCE', name: 'MySQL' },
  { type: 'FAST STATE', name: 'Redis' },
  { type: 'EVENT BUS', name: 'RocketMQ' },
  { type: 'VECTOR STORE', name: 'PostgreSQL · pgvector' },
  { type: 'SERVICE GOV', name: 'Nacos · Sentinel' }
]

const flows = [
  {
    index: '01',
    problem: '高频状态写入与乱序',
    title: '点赞可靠链路',
    steps: ['Redis 削峰', '版本持久化', 'Outbox', 'RocketMQ', '通知投影'],
    result: 'MySQL 保存最终状态，通知只消费事件；迟到版本不会覆盖用户最新操作。'
  },
  {
    index: '02',
    problem: '突发写入与重复消费',
    title: '评论可靠链路',
    steps: ['限流校验', 'RocketMQ', 'MySQL 事务', '自动审核', '通知 Outbox'],
    result: 'RocketMQ 负责削峰，MySQL 是唯一事实源，source_event_id 唯一约束兜底重复投递。'
  },
  {
    index: '03',
    problem: '业务文章与知识索引不一致',
    title: '文章增量索引',
    steps: ['业务 Outbox', 'RocketMQ', '结构化 Chunk', 'Embedding', 'pgvector'],
    result: '索引按文章版本收敛；下线标记、重试、死信与对账共同阻止旧内容重新可见。'
  },
  {
    index: '04',
    problem: '检索不足与模型失控',
    title: '可信 RAG 与受控 Agent',
    steps: ['BM25 + Dense', 'RRF', 'Reranker', '引用校验', '只读 Agent'],
    result: '证据不足时拒答；Agent 受步数、检索次数、Token 与总超时硬预算约束。'
  }
]

const choices = [
  {
    title: '不拆十几个微服务',
    description: '只拆认证、消息和知识等边界清晰的能力，避免分布式复杂度超过业务收益。'
  },
  {
    title: '不用 Seata 包住所有事务',
    description: '跨服务副作用通过 Outbox 与最终一致性解决，主业务不等待远端事务。'
  },
  {
    title: '不做无边界多 Agent',
    description: '工具只读且白名单化，循环、越权、超时或模型失败都会终止并降级。'
  }
]

const loginDialogClicked = ref(false)
provide('loginDialogClicked', () => {
  loginDialogClicked.value = !loginDialogClicked.value
})
</script>

<style scoped>
.architecture-page {
  padding-bottom: var(--space-16);
}

.eyebrow {
  color: var(--color-brand-strong);
  font-size: 0.7rem;
  font-weight: 780;
  letter-spacing: 0.15em;
}

.architecture-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(18rem, 0.85fr);
  gap: clamp(2rem, 8vw, 7rem);
  align-items: end;
  padding-block: clamp(4.5rem, 10vw, 8rem);
}

h1,
h2,
h3 {
  font-family: var(--font-display);
}

h1 {
  margin-top: var(--space-5);
  font-size: clamp(2.6rem, 5vw, 4.9rem);
  font-weight: 780;
  letter-spacing: -0.06em;
  line-height: 1.04;
}

.architecture-hero__summary p {
  color: var(--color-text-secondary);
  line-height: 1.9;
}

.architecture-hero__summary a {
  display: inline-flex;
  gap: var(--space-6);
  margin-top: var(--space-6);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-text);
  color: var(--color-text);
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
}

.system-canvas {
  padding: clamp(1.25rem, 4vw, 2.5rem);
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 1.6rem;
  background:
    linear-gradient(rgb(255 255 255 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 3%) 1px, transparent 1px),
    #151a2a;
  background-size: 30px 30px;
  box-shadow: var(--shadow-lg);
}

.system-canvas__entry,
.system-canvas__gateway {
  width: min(100%, 34rem);
  margin-inline: auto;
  padding: var(--space-4);
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: var(--radius-md);
  background: rgb(255 255 255 / 5%);
  text-align: center;
}

.system-canvas__gateway {
  border-color: rgb(139 124 255 / 45%);
  background: rgb(99 91 255 / 12%);
}

.system-canvas__entry span,
.system-canvas__gateway span,
.service-grid article > span {
  display: block;
  color: #9289ff;
  font-size: 0.55rem;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.system-canvas strong,
.system-canvas small {
  display: block;
}

.system-canvas__entry strong,
.system-canvas__gateway strong {
  margin-top: var(--space-2);
  color: white;
  font-size: 0.96rem;
}

.system-canvas__entry small,
.system-canvas__gateway small {
  margin-top: var(--space-2);
  color: #939db7;
  font-size: 0.68rem;
}

.connector {
  display: flex;
  width: min(100%, 34rem);
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-4) auto;
}

.connector i {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, #6e77a1);
}

.connector i:last-child {
  background: linear-gradient(90deg, #6e77a1, transparent);
}

.connector span {
  color: #727d9a;
  font-family: monospace;
  font-size: 0.58rem;
}

.connector--branch {
  width: min(100%, 54rem);
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.service-grid article {
  padding: var(--space-5);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: var(--radius-md);
  background: rgb(39 46 68 / 88%);
}

.service-grid article.accent {
  border-color: rgb(0 168 143 / 35%);
}

.service-grid article.accent > span {
  color: #58d8c2;
}

.service-grid article > strong {
  margin-top: var(--space-3);
  color: #f7f8ff;
  font-size: 0.88rem;
}

.service-grid article > small {
  min-height: 2.5rem;
  margin-top: var(--space-2);
  color: #929cb6;
  font-size: 0.66rem;
  line-height: 1.6;
}

.service-grid ul {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-4);
}

.service-grid li {
  padding: 0.25rem 0.4rem;
  border-radius: 0.35rem;
  background: rgb(255 255 255 / 5%);
  color: #b1b8ca;
  font-size: 0.58rem;
}

.infra-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-6);
}

.infra-row > span {
  padding: var(--space-3);
  border-top: 1px solid rgb(255 255 255 / 9%);
}

.infra-row small {
  color: #68728e;
  font-size: 0.5rem;
  letter-spacing: 0.09em;
}

.infra-row strong {
  margin-top: var(--space-2);
  color: #d9ddec;
  font-size: 0.7rem;
}

.observability-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding: var(--space-4);
  border-radius: var(--radius-sm);
  background: rgb(0 168 143 / 8%);
  color: #9fa8be;
  font-size: 0.64rem;
}

.observability-row strong {
  margin-right: auto;
  color: #58d8c2;
  font-size: 0.58rem;
  letter-spacing: 0.12em;
}

.decision-section,
.choices-section {
  padding-top: clamp(5rem, 10vw, 8rem);
}

.section-heading {
  max-width: 47rem;
}

.section-heading h2,
.choices-section h2 {
  margin-top: var(--space-3);
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 760;
  letter-spacing: -0.045em;
}

.section-heading > span {
  display: block;
  margin-top: var(--space-4);
  color: var(--color-text-muted);
  font-size: 0.86rem;
  line-height: 1.8;
}

.flow-list {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-10);
}

.flow-list article {
  display: grid;
  grid-template-columns: minmax(13rem, 0.7fr) minmax(0, 1.5fr) minmax(14rem, 0.8fr);
  gap: var(--space-6);
  align-items: center;
  padding: var(--space-6);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.flow-list__head {
  display: flex;
  gap: var(--space-4);
  align-items: center;
}

.flow-list__head > span {
  color: var(--color-brand);
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 780;
}

.flow-list__head small {
  color: var(--color-text-muted);
  font-size: 0.64rem;
}

.flow-list h3 {
  margin-top: var(--space-1);
  font-size: 1rem;
}

.flow-list__path {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.flow-list__path span {
  padding: 0.38rem 0.55rem;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: 0.66rem;
  font-weight: 650;
}

.flow-list__path i {
  color: #a1a8b8;
  font-style: normal;
}

.flow-list article > p {
  color: var(--color-text-muted);
  font-size: 0.76rem;
  line-height: 1.7;
}

.choices-section {
  display: grid;
  grid-template-columns: minmax(14rem, 0.65fr) minmax(0, 1.35fr);
  gap: clamp(2rem, 8vw, 7rem);
}

.choice-grid {
  display: grid;
  gap: var(--space-3);
}

.choice-grid article {
  display: grid;
  grid-template-columns: 2rem 1fr;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.choice-grid article > span {
  display: grid;
  width: 1.7rem;
  height: 1.7rem;
  place-items: center;
  border-radius: 999px;
  background: #fff0f0;
  color: var(--color-danger);
  font-weight: 800;
}

.choice-grid h3 {
  font-size: 0.95rem;
}

.choice-grid p {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.78rem;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .architecture-hero,
  .choices-section {
    grid-template-columns: 1fr;
  }

  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .infra-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .flow-list article {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .architecture-hero {
    padding-block: 3.5rem;
  }

  .service-grid,
  .infra-row {
    grid-template-columns: 1fr;
  }

  .service-grid article > small {
    min-height: 0;
  }

  .observability-row strong {
    width: 100%;
  }

  .flow-list__path i {
    display: none;
  }
}
</style>
