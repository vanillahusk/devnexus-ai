<template>
  <HeaderBar />

  <main class="portfolio-page">
    <section class="hero page-shell">
      <div class="hero__copy">
        <div class="hero__status">
          <span></span>
          社区链路与 RAG Agent 已完成真实验证
        </div>
        <p class="hero__eyebrow">COMMUNITY SYSTEM × AI KNOWLEDGE</p>
        <h1>
          把社区业务做可靠，
          <em>让知识检索可验证。</em>
        </h1>
        <p class="hero__summary">
          DevNexus AI 将高并发社区、可靠消息、Spring Cloud Alibaba
          微服务治理和现代 RAG 连接成一套可以运行、测试与解释的工程系统。
        </p>
        <div class="hero__actions">
          <RouterLink class="primary-action" to="/chat">
            体验 AI 助手
            <span aria-hidden="true">→</span>
          </RouterLink>
          <a class="secondary-action" href="#articles">浏览社区文章</a>
        </div>
        <div class="hero__stack" aria-label="核心技术栈">
          <span>Spring Boot 3</span>
          <span>RocketMQ</span>
          <span>Redis</span>
          <span>pgvector</span>
          <span>SkyWalking</span>
        </div>
      </div>

      <div class="hero__visual" aria-label="系统链路概览">
        <div class="system-window">
          <div class="system-window__bar">
            <span></span><span></span><span></span>
            <small>devnexus / live architecture</small>
          </div>
          <div class="system-window__body">
            <div class="flow-node flow-node--gateway">
              <small>ENTRY</small>
              <strong>Gateway</strong>
              <span>Trace · Auth · Sentinel</span>
            </div>
            <div class="flow-line"></div>
            <div class="flow-grid">
              <div class="flow-node">
                <small>COMMUNITY</small>
                <strong>Article & Social</strong>
                <span>Redis · MySQL · Outbox</span>
              </div>
              <div class="flow-node flow-node--accent">
                <small>KNOWLEDGE</small>
                <strong>RAG & Agent</strong>
                <span>BM25 · Dense · Rerank</span>
              </div>
            </div>
            <div class="flow-events">
              <span>RocketMQ event stream</span>
              <i></i>
              <span>Trace correlated</span>
            </div>
          </div>
        </div>
        <div class="floating-proof floating-proof--top">
          <span>✓</span>
          <div><strong>52 tests</strong><small>fault regression</small></div>
        </div>
        <div class="floating-proof floating-proof--bottom">
          <span>↗</span>
          <div><strong>9.41s</strong><small>real Agent E2E</small></div>
        </div>
      </div>
    </section>

    <section class="metrics page-shell" aria-label="实测指标">
      <MetricCard
        value="102.95"
        label="点赞稳定档 RPS"
        note="三轮中位数 · P99 17.98ms"
      />
      <MetricCard
        value="52.42"
        label="评论稳定档 RPS"
        note="8664 / 8664 最终落库"
      />
      <MetricCard
        value="91.98%"
        label="Reranker Recall@5"
        note="150 篇 / 150 题困难集"
      />
      <MetricCard
        value="90.11%"
        label="Reranker MRR"
        note="真实 Qwen3 检索评测"
      />
    </section>

    <section id="articles" class="articles-section page-shell">
      <div class="section-heading section-heading--articles">
        <div>
          <p>COMMUNITY CONTENT</p>
          <h2>社区文章</h2>
        </div>
        <span>文章内容同时也是 RAG 知识索引的事实来源。</span>
      </div>

      <div v-if="articleError" class="load-state" role="alert">
        <div>
          <strong>社区数据暂时不可用</strong>
          <p>{{ articleError }}。作品集能力介绍仍可正常浏览。</p>
        </div>
        <button type="button" @click="loadArticles">重新加载</button>
      </div>

      <template v-else>
        <el-skeleton :loading="articlesLoading" animated :rows="6">
          <NavBar v-if="categories.length" :categories="categories" />
          <RecommendArticle
            v-if="topArticles.length"
            :top-articles="topArticles"
          />
          <div class="article-panel">
            <ArticleList :articles="articles.records" />
            <el-empty
              v-if="!articles.records.length"
              description="暂无已发布文章"
            />
            <el-pagination
              v-if="articles.pages > 1"
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20]"
              :page-count="articles.pages"
              layout="sizes, prev, pager, next"
              @update:page-size="loadArticles"
              @update:current-page="loadArticles"
            />
          </div>
        </el-skeleton>
      </template>
    </section>

    <section id="architecture" class="section page-shell">
      <div class="section-heading">
        <div>
          <p>ENGINEERING, NOT A COMPONENT LIST</p>
          <h2>两条主线，一套证据体系</h2>
        </div>
        <span>每项技术都对应具体故障边界和验证结果。</span>
      </div>
      <div class="capability-grid">
        <CapabilityCard
          v-for="capability in capabilities"
          :key="capability.title"
          v-bind="capability"
        />
      </div>
    </section>

    <section class="architecture-strip page-shell">
      <div class="architecture-strip__label">
        <span>01</span>
        <div>
          <small>COMMUNITY CORE</small>
          <strong>可靠业务链路</strong>
        </div>
      </div>
      <div class="architecture-strip__flow">
        <span>Redis 削峰</span><i>→</i>
        <span>MySQL 事实源</span><i>→</i>
        <span>Outbox</span><i>→</i>
        <span>RocketMQ</span>
      </div>
      <div class="architecture-strip__label">
        <span>02</span>
        <div>
          <small>KNOWLEDGE CORE</small>
          <strong>可验证检索</strong>
        </div>
      </div>
      <div class="architecture-strip__flow">
        <span>结构化 Chunk</span><i>→</i>
        <span>Hybrid</span><i>→</i>
        <span>Reranker</span><i>→</i>
        <span>受控 Agent</span>
      </div>
    </section>

    <section class="portfolio-bridge page-shell">
      <div>
        <p>EXPLORE THE ENGINEERING</p>
        <h2>从架构决策，一直看到验证结果。</h2>
        <span>
          先理解为什么这样设计，再查看压测、故障回归和真实模型评测证据。
        </span>
      </div>
      <div class="portfolio-bridge__actions">
        <RouterLink to="/architecture">
          查看完整架构
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink to="/evidence">
          查看工程证据
          <span aria-hidden="true">↗</span>
        </RouterLink>
      </div>
    </section>

  </main>

  <Footer />
  <LoginDialog :clicked="loginDialogClicked" />
</template>

<script setup lang="ts">
import { onMounted, provide, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import HeaderBar from '@/components/layout/HeaderBar.vue'
import Footer from '@/components/layout/Footer.vue'
import LoginDialog from '@/components/dialog/LoginDialog.vue'
import ArticleList from '@/views/home/article/ArticleList.vue'
import NavBar from '@/views/home/navbar/NavBar.vue'
import RecommendArticle from '@/views/home/recommend/RecommendArticle.vue'
import MetricCard from '@/shared/ui/MetricCard.vue'
import CapabilityCard from '@/shared/ui/CapabilityCard.vue'
import { fetchHomeArticles } from '@/services/article'
import { useGlobalStore } from '@/stores/global'
import { defaultBasicPage, type BasicPageType } from '@/http/ResponseTypes/PageType/BasicPageType'
import type { ArticleType } from '@/http/ResponseTypes/ArticleType/ArticleType'
import type { ArticleCategoryType } from '@/http/ResponseTypes/CategoryType/ArticleCategoryType'

const capabilities = [
  {
    icon: '⚡',
    eyebrow: 'Reliable Community',
    title: '高并发社区链路',
    description: '围绕点赞、评论和通知建立削峰、幂等、乱序保护与失败恢复。',
    items: ['Redis 可靠队列', 'Outbox', 'RocketMQ', '最终一致']
  },
  {
    icon: '◇',
    eyebrow: 'Service Governance',
    title: '适度微服务治理',
    description: '只拆认证和消息等清晰边界，通过统一入口治理同步与异步调用。',
    items: ['Gateway', 'Nacos', 'OpenFeign', 'Sentinel']
  },
  {
    icon: '◎',
    eyebrow: 'Observable System',
    title: '端到端可观测',
    description: '指标、Trace 和业务状态共同定位 HTTP、Feign 与消息异步链路。',
    items: ['Micrometer', 'Prometheus', 'Grafana', 'SkyWalking']
  },
  {
    icon: '✦',
    eyebrow: 'Modern Retrieval',
    title: '现代 RAG 与 Agent',
    description: '混合召回、精排、引用和拒答形成可评测检索，Agent 仅调用只读工具。',
    items: ['BM25 + Dense', 'RRF', 'Reranker', '硬预算']
  }
]

const route = useRoute()
const globalStore = useGlobalStore()
const articles = ref<BasicPageType<ArticleType>>({
  ...defaultBasicPage,
  records: []
})
const categories = ref<ArticleCategoryType[]>([])
const topArticles = ref<ArticleType[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const articlesLoading = ref(true)
const articleError = ref('')
const loginDialogClicked = ref(false)

provide('loginDialogClicked', () => {
  loginDialogClicked.value = !loginDialogClicked.value
})

function selectedCategory(): string | null {
  const category = route.query.category
  return typeof category === 'string' ? category : null
}

async function loadArticles(): Promise<void> {
  articlesLoading.value = true
  articleError.value = ''
  try {
    const response = await fetchHomeArticles({
      category: selectedCategory(),
      currentPage: currentPage.value,
      pageSize: pageSize.value
    })
    globalStore.setGlobal(response.global)
    categories.value = response.result.categories ?? []
    topArticles.value = response.result.topArticles ?? []
    articles.value = response.result.articles
    currentPage.value = Number(response.result.articles.current)
  } catch (error) {
    articleError.value =
      error instanceof Error ? error.message : '无法连接社区服务'
  } finally {
    articlesLoading.value = false
  }
}

onMounted(loadArticles)
</script>

<style scoped>
.portfolio-page {
  overflow: hidden;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(28rem, 0.98fr);
  gap: clamp(3rem, 7vw, 7rem);
  align-items: center;
  min-height: 690px;
  padding-block: clamp(4rem, 9vw, 7.5rem);
}

.hero__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0.42rem 0.75rem;
  border: 1px solid #cfece5;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: #087463;
  font-size: 0.72rem;
  font-weight: 680;
}

.hero__status span {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 999px;
  background: var(--color-accent);
  box-shadow: 0 0 0 4px rgb(0 168 143 / 12%);
}

.hero__eyebrow,
.section-heading p {
  margin-top: var(--space-6);
  color: var(--color-brand-strong);
  font-size: 0.72rem;
  font-weight: 760;
  letter-spacing: 0.14em;
}

.hero h1 {
  max-width: 11ch;
  margin-top: var(--space-4);
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: clamp(3rem, 5.6vw, 5.4rem);
  font-weight: 790;
  letter-spacing: -0.065em;
  line-height: 0.98;
}

.hero h1 em {
  display: block;
  background: linear-gradient(110deg, var(--color-brand), #8b5cf6 55%, var(--color-accent));
  background-clip: text;
  color: transparent;
  font-style: normal;
  font-weight: inherit;
}

.hero__summary {
  max-width: 36rem;
  margin-top: var(--space-6);
  color: var(--color-text-secondary);
  font-size: clamp(0.95rem, 1.4vw, 1.08rem);
  line-height: 1.85;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-8);
}

.primary-action,
.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  min-height: 3rem;
  padding: 0.75rem 1.15rem;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.primary-action {
  background: var(--color-text);
  box-shadow: 0 12px 25px rgb(24 32 51 / 18%);
  color: white;
}

.secondary-action {
  border: 1px solid var(--color-border-subtle);
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.primary-action:hover,
.secondary-action:hover {
  transform: translateY(-2px);
}

.primary-action:hover {
  color: white;
}

.hero__stack {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-8);
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 650;
}

.hero__stack span:not(:last-child)::after {
  margin-left: var(--space-4);
  color: #c3c8d3;
  content: '·';
}

.hero__visual {
  position: relative;
}

.system-window {
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 65%);
  border-radius: 1.6rem;
  background: #171c2c;
  box-shadow: var(--shadow-lg);
  transform: perspective(1000px) rotateY(-3deg) rotateX(1deg);
}

.system-window__bar {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.9rem 1rem;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
  background: #202638;
}

.system-window__bar > span {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 99px;
  background: #576078;
}

.system-window__bar > span:first-child {
  background: #ff7a78;
}

.system-window__bar > span:nth-child(2) {
  background: #ffd36e;
}

.system-window__bar > span:nth-child(3) {
  background: #61d6a1;
}

.system-window__bar small {
  margin-left: auto;
  color: #7f89a4;
  font-family: monospace;
  font-size: 0.62rem;
}

.system-window__body {
  padding: clamp(1.5rem, 4vw, 2.5rem);
  background:
    linear-gradient(rgb(255 255 255 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 3%) 1px, transparent 1px);
  background-size: 28px 28px;
}

.flow-node {
  padding: 1rem;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: var(--radius-md);
  background: rgb(39 46 68 / 90%);
}

.flow-node--gateway {
  width: 62%;
  margin-inline: auto;
  border-color: rgb(139 124 255 / 50%);
  box-shadow: 0 0 30px rgb(99 91 255 / 12%);
}

.flow-node--accent {
  border-color: rgb(0 168 143 / 45%);
}

.flow-node small,
.flow-node strong,
.flow-node span {
  display: block;
}

.flow-node small {
  color: #8d83ff;
  font-size: 0.55rem;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.flow-node strong {
  margin-top: 0.35rem;
  color: #f7f8ff;
  font-size: 0.92rem;
  font-weight: 680;
}

.flow-node span {
  margin-top: 0.4rem;
  color: #8f9ab5;
  font-family: monospace;
  font-size: 0.6rem;
}

.flow-line {
  width: 1px;
  height: 2.5rem;
  margin-inline: auto;
  background: linear-gradient(#7067dd, #414963);
}

.flow-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
}

.flow-events {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-6);
  color: #8893ac;
  font-family: monospace;
  font-size: 0.58rem;
}

.flow-events i {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, #635bff, #00a88f);
}

.floating-proof {
  position: absolute;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0.75rem 0.9rem;
  border: 1px solid rgb(229 232 240 / 85%);
  border-radius: var(--radius-md);
  background: rgb(255 255 255 / 92%);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(12px);
}

.floating-proof > span {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-weight: 800;
}

.floating-proof strong,
.floating-proof small {
  display: block;
}

.floating-proof strong {
  color: var(--color-text);
  font-size: 0.78rem;
  font-weight: 750;
}

.floating-proof small {
  margin-top: 0.1rem;
  color: var(--color-text-muted);
  font-size: 0.58rem;
}

.floating-proof--top {
  top: -2rem;
  right: -1.5rem;
}

.floating-proof--bottom {
  bottom: -2rem;
  left: -2rem;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  transform: translateY(-1rem);
}

.section {
  padding-top: clamp(5rem, 10vw, 8rem);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-8);
  margin-bottom: var(--space-8);
}

.section-heading p {
  margin-top: 0;
}

.section-heading h2 {
  margin-top: var(--space-3);
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 760;
  letter-spacing: -0.045em;
}

.section-heading > span {
  max-width: 26rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  line-height: 1.7;
  text-align: right;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-5);
}

.architecture-strip {
  display: grid;
  grid-template-columns: 0.8fr 1.7fr;
  gap: 1px;
  overflow: hidden;
  margin-top: var(--space-16);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-border-subtle);
}

.architecture-strip__label,
.architecture-strip__flow {
  display: flex;
  align-items: center;
  min-height: 6.5rem;
  padding: var(--space-5);
  background: var(--color-surface);
}

.architecture-strip__label {
  gap: var(--space-4);
}

.architecture-strip__label > span {
  color: var(--color-brand);
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 780;
}

.architecture-strip__label small,
.architecture-strip__label strong {
  display: block;
}

.architecture-strip__label small {
  color: var(--color-text-muted);
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.architecture-strip__label strong {
  margin-top: var(--space-1);
  color: var(--color-text);
  font-size: 0.9rem;
  font-weight: 700;
}

.architecture-strip__flow {
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-secondary);
  font-size: 0.76rem;
  font-weight: 650;
}

.architecture-strip__flow span {
  padding: 0.45rem 0.65rem;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}

.architecture-strip__flow i {
  color: var(--color-brand);
  font-style: normal;
}

.articles-section {
  padding-top: clamp(5rem, 10vw, 8rem);
}

.portfolio-bridge {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-10);
  margin-top: var(--space-16);
  padding: clamp(2rem, 5vw, 4rem);
  border: 1px solid rgb(99 91 255 / 12%);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 90% 10%, rgb(0 168 143 / 14%), transparent 16rem),
    linear-gradient(135deg, #eeecff, #f7f8ff);
}

.portfolio-bridge > div:first-child {
  max-width: 43rem;
}

.portfolio-bridge p {
  color: var(--color-brand-strong);
  font-size: 0.68rem;
  font-weight: 780;
  letter-spacing: 0.14em;
}

.portfolio-bridge h2 {
  margin-top: var(--space-3);
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 4vw, 3rem);
  font-weight: 760;
  letter-spacing: -0.045em;
}

.portfolio-bridge > div:first-child > span {
  display: block;
  margin-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  line-height: 1.8;
}

.portfolio-bridge__actions {
  display: grid;
  flex: 0 0 auto;
  gap: var(--space-3);
}

.portfolio-bridge__actions a {
  display: flex;
  min-width: 12rem;
  justify-content: space-between;
  padding: 0.8rem 1rem;
  border: 1px solid rgb(24 32 51 / 12%);
  border-radius: var(--radius-sm);
  color: var(--color-text);
  font-size: 0.8rem;
  font-weight: 700;
  text-decoration: none;
}

.portfolio-bridge__actions a:first-child {
  border-color: var(--color-text);
  background: var(--color-text);
  color: white;
}

.section-heading--articles {
  margin-bottom: var(--space-6);
}

.article-panel {
  padding: 0 var(--space-5) var(--space-6);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.article-panel :deep(.el-pagination) {
  justify-content: center;
  margin-top: var(--space-6);
}

.load-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-6);
  border: 1px solid #f1d7b7;
  border-radius: var(--radius-lg);
  background: #fff9f0;
}

.load-state strong {
  color: var(--color-text);
  font-weight: 700;
}

.load-state p {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.load-state button {
  flex: none;
  padding: 0.65rem 0.9rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-text);
  color: white;
  cursor: pointer;
  font-size: 0.78rem;
  font-weight: 680;
}

@media (max-width: 980px) {
  .hero {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .hero__copy {
    text-align: center;
  }

  .hero h1,
  .hero__summary {
    margin-inline: auto;
  }

  .hero__actions,
  .hero__stack {
    justify-content: center;
  }

  .hero__visual {
    width: min(100%, 38rem);
    margin-inline: auto;
  }

  .metrics {
    grid-template-columns: repeat(2, 1fr);
    transform: none;
  }

  .architecture-strip {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .hero {
    gap: var(--space-16);
    padding-top: var(--space-12);
  }

  .hero h1 {
    font-size: clamp(2.65rem, 14vw, 4rem);
  }

  .hero__status {
    font-size: 0.64rem;
  }

  .hero__visual {
    width: calc(100% - 1rem);
  }

  .system-window {
    transform: none;
  }

  .floating-proof--top {
    top: -2.4rem;
    right: -0.5rem;
  }

  .floating-proof--bottom {
    bottom: -2.4rem;
    left: -0.5rem;
  }

  .metrics,
  .capability-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    display: block;
  }

  .section-heading > span {
    display: block;
    margin-top: var(--space-4);
    text-align: left;
  }

  .architecture-strip__flow {
    align-items: stretch;
    flex-direction: column;
  }

  .architecture-strip__flow i {
    transform: rotate(90deg);
    text-align: center;
  }

  .load-state {
    align-items: flex-start;
    flex-direction: column;
  }

  .portfolio-bridge {
    align-items: stretch;
    flex-direction: column;
  }

  .portfolio-bridge__actions a {
    min-width: 0;
  }
}
</style>
