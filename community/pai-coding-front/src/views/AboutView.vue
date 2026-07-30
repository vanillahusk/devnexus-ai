<template>
  <HeaderBar />

  <main class="about-page">
    <section class="about-hero page-shell">
      <div>
        <p class="eyebrow">ABOUT DEVNEXUS AI</p>
        <h1>它不是组件陈列，<br />而是一套有故障边界的工程系统。</h1>
      </div>
      <p class="about-hero__summary">
        项目从技术社区出发，把点赞、评论、通知和文章知识索引做成可恢复、
        可观测、可验证的完整链路。每项能力都对应真实代码、自动化测试或实测证据。
      </p>
    </section>

    <section class="principles page-shell" aria-label="项目原则">
      <article v-for="principle in principles" :key="principle.index">
        <span>{{ principle.index }}</span>
        <h2>{{ principle.title }}</h2>
        <p>{{ principle.description }}</p>
      </article>
    </section>

    <section class="about-section page-shell">
      <div class="section-copy">
        <p class="eyebrow">SYSTEM MAP</p>
        <h2>社区业务与知识系统如何连接</h2>
        <p>
          MySQL 保存文章事实，Outbox 与 RocketMQ 传递变更，RAG
          服务负责分块、向量化和检索。Agent 只在服务端预算内调用只读工具。
        </p>
      </div>

      <div class="system-map" aria-label="DevNexus AI 系统结构">
        <div class="system-map__lane">
          <strong>公开入口</strong>
          <div><span>Vue 社区</span><i>→</i><span>Gateway</span></div>
        </div>
        <div class="system-map__lane">
          <strong>社区事实</strong>
          <div>
            <span>Article</span><i>→</i><span>MySQL</span><i>→</i><span>Outbox</span>
          </div>
        </div>
        <div class="system-map__lane system-map__lane--accent">
          <strong>知识收敛</strong>
          <div>
            <span>RocketMQ</span><i>→</i><span>Chunk</span><i>→</i><span>pgvector</span>
          </div>
        </div>
        <div class="system-map__lane">
          <strong>回答链路</strong>
          <div>
            <span>Hybrid</span><i>→</i><span>Reranker</span><i>→</i><span>Agent</span>
          </div>
        </div>
      </div>
    </section>

    <section class="status-section page-shell">
      <div class="section-copy">
        <p class="eyebrow">CAPABILITY STATUS</p>
        <h2>功能状态公开，而不是把规划写成成果</h2>
      </div>
      <div class="status-grid">
        <article v-for="item in statuses" :key="item.title">
          <div>
            <span :class="`status-dot status-dot--${item.level}`"></span>
            <small>{{ item.status }}</small>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
        </article>
      </div>
    </section>

    <section class="run-section page-shell">
      <div>
        <p class="eyebrow">RUN & CONTRIBUTE</p>
        <h2>先运行轻量主链路，再按需启用完整基础设施。</h2>
        <p>
          仓库提供环境变量示例和分层启动说明。前端可以独立浏览作品集；
          真实社区、检索和链路追踪需要启动对应后端依赖。
        </p>
      </div>
      <div class="run-section__actions">
        <a
          class="primary-link"
          href="https://github.com/vanillahusk/devnexus-ai"
          target="_blank"
          rel="noreferrer"
        >
          查看 GitHub
          <span aria-hidden="true">↗</span>
        </a>
        <RouterLink class="secondary-link" to="/chat">体验受控 Agent</RouterLink>
      </div>
    </section>

    <section class="contribute-section page-shell">
      <div class="section-copy">
        <p class="eyebrow">OPEN FOR COLLABORATION</p>
        <h2>这是一个持续演进的个人简历项目。</h2>
        <p>
          如果你对前端体验、自动化测试、低资源部署或检索评测感兴趣，
          欢迎提交 Issue 与 Pull Request；需要交流项目或获取简历，也可以通过 GitHub 联系我。
        </p>
      </div>
      <div class="contribute-grid">
        <article v-for="item in contributionAreas" :key="item.title">
          <span>{{ item.index }}</span>
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
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

const principles = [
  {
    index: '01',
    title: '事实源清晰',
    description: '状态与通知分离，业务版本防止迟到事件覆盖最新操作。'
  },
  {
    index: '02',
    title: '失败可以恢复',
    description: 'Outbox、重试、死信、人工重放和数据对账形成闭环。'
  },
  {
    index: '03',
    title: '结果必须验证',
    description: '用单测、压测、故障演练、指标和 Trace 支撑技术结论。'
  }
]

const statuses = [
  {
    level: 'done',
    status: '已验证',
    title: '社区可靠链路',
    description: '点赞与评论完成真实写入压测，消息失败具备补偿路径。'
  },
  {
    level: 'done',
    status: '已验证',
    title: '微服务与可观测性',
    description: 'Gateway、认证、消息服务及 HTTP、Feign、MQ Trace 已跑通。'
  },
  {
    level: 'done',
    status: '已验证',
    title: '现代 RAG 与 Agent',
    description: '混合检索完成困难集评测，受控 Agent 完成真实模型验证。'
  },
  {
    level: 'progress',
    status: '首版完成',
    title: '前端作品集',
    description: '作品集首页、系统架构、工程证据和社区主流程已经形成连续入口。'
  }
]

const contributionAreas = [
  {
    index: '01',
    title: '前端体验',
    description: '移动端适配、交互细节、无障碍与真实 SSE 展示。'
  },
  {
    index: '02',
    title: '自动化测试',
    description: '补充 Playwright 主流程与公开环境回归。'
  },
  {
    index: '03',
    title: '部署与文档',
    description: '优化低资源启动、环境预检和新机器复现。'
  }
]

const loginDialogClicked = ref(false)
provide('loginDialogClicked', () => {
  loginDialogClicked.value = !loginDialogClicked.value
})
</script>

<style scoped>
.about-page {
  padding-bottom: var(--space-16);
}

.about-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(17rem, 0.75fr);
  gap: clamp(2rem, 8vw, 7rem);
  align-items: end;
  padding-block: clamp(4.5rem, 10vw, 8.5rem);
}

.eyebrow {
  color: var(--color-brand-strong);
  font-size: 0.72rem;
  font-weight: 780;
  letter-spacing: 0.16em;
}

h1,
h2,
h3 {
  font-family: var(--font-display);
}

h1 {
  margin-top: var(--space-5);
  color: var(--color-text);
  font-size: clamp(2.5rem, 5vw, 4.8rem);
  font-weight: 780;
  letter-spacing: -0.055em;
  line-height: 1.05;
}

.about-hero__summary,
.section-copy > p:last-child,
.run-section > div > p:last-child {
  color: var(--color-text-secondary);
  font-size: 1rem;
  line-height: 1.9;
}

.principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.principles article {
  padding: clamp(1.5rem, 4vw, 2.5rem);
}

.principles article + article {
  border-left: 1px solid var(--color-border-subtle);
}

.principles span {
  color: var(--color-brand);
  font-size: 0.72rem;
  font-weight: 800;
}

.principles h2 {
  margin-top: var(--space-6);
  font-size: 1.25rem;
}

.principles p,
.status-grid p {
  margin-top: var(--space-3);
  color: var(--color-text-muted);
  font-size: 0.86rem;
  line-height: 1.7;
}

.about-section {
  display: grid;
  grid-template-columns: minmax(15rem, 0.7fr) minmax(0, 1.3fr);
  gap: clamp(2rem, 8vw, 7rem);
  align-items: center;
  padding-block: clamp(5rem, 9vw, 8rem);
}

.section-copy h2,
.run-section h2 {
  margin-block: var(--space-4);
  color: var(--color-text);
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 750;
  letter-spacing: -0.045em;
  line-height: 1.12;
}

.system-map {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: #171a29;
  box-shadow: var(--shadow-lg);
}

.system-map__lane {
  display: grid;
  grid-template-columns: 6rem 1fr;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4);
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: var(--radius-md);
}

.system-map__lane strong {
  color: #aeb5ca;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
}

.system-map__lane div {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.system-map__lane span {
  flex: 1;
  padding: 0.68rem;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: var(--radius-sm);
  background: rgb(255 255 255 / 5%);
  color: #f3f5ff;
  font-size: 0.76rem;
  text-align: center;
}

.system-map__lane i {
  color: #77809c;
  font-style: normal;
}

.system-map__lane--accent span {
  border-color: rgb(126 109 255 / 35%);
  background: rgb(99 91 255 / 15%);
}

.status-section {
  padding-bottom: clamp(5rem, 9vw, 8rem);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-10);
}

.status-grid article {
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.status-grid article > div {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.status-grid small {
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 650;
}

.status-grid h3 {
  margin-top: var(--space-5);
  font-size: 1rem;
}

.status-dot {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 999px;
}

.status-dot--done {
  background: var(--color-accent);
  box-shadow: 0 0 0 4px var(--color-accent-soft);
}

.status-dot--progress {
  background: var(--color-warning);
  box-shadow: 0 0 0 4px #fff3dc;
}

.run-section {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-10);
  padding: clamp(2rem, 5vw, 4rem);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, #eae7ff, #e7faf6);
}

.contribute-section {
  display: grid;
  grid-template-columns: minmax(16rem, 0.8fr) minmax(0, 1.2fr);
  gap: clamp(2rem, 8vw, 7rem);
  align-items: start;
  padding-top: clamp(5rem, 9vw, 8rem);
}

.contribute-grid {
  display: grid;
  gap: var(--space-3);
}

.contribute-grid article {
  display: grid;
  grid-template-columns: 2rem 1fr;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.contribute-grid article > span {
  color: var(--color-brand);
  font-family: var(--font-display);
  font-size: 0.72rem;
  font-weight: 800;
}

.contribute-grid h3 {
  font-size: 1rem;
}

.contribute-grid p {
  margin-top: var(--space-2);
}

.run-section > div:first-child {
  max-width: 44rem;
}

.run-section__actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: var(--space-3);
}

.primary-link,
.secondary-link {
  display: flex;
  min-width: 12rem;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
}

.primary-link {
  background: var(--color-text);
  color: white;
}

.secondary-link {
  border: 1px solid rgb(24 32 51 / 18%);
  color: var(--color-text);
}

@media (max-width: 900px) {
  .about-hero,
  .about-section,
  .contribute-section {
    grid-template-columns: 1fr;
  }

  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .run-section {
    align-items: stretch;
    flex-direction: column;
  }

  .run-section__actions {
    flex-direction: row;
  }
}

@media (max-width: 640px) {
  .about-hero {
    padding-block: 3.5rem;
  }

  .principles,
  .status-grid {
    grid-template-columns: 1fr;
  }

  .principles article + article {
    border-top: 1px solid var(--color-border-subtle);
    border-left: 0;
  }

  .system-map__lane {
    grid-template-columns: 1fr;
  }

  .system-map__lane div {
    align-items: stretch;
    flex-direction: column;
  }

  .system-map__lane i {
    transform: rotate(90deg);
    text-align: center;
  }

  .run-section__actions {
    flex-direction: column;
  }
}
</style>
