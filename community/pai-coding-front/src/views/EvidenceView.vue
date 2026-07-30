<template>
  <HeaderBar />

  <main class="evidence-page">
    <section class="evidence-hero page-shell">
      <div>
        <p class="eyebrow">ENGINEERING EVIDENCE</p>
        <h1>不只展示功能，<br />还展示它如何被证明。</h1>
      </div>
      <p>
        下列结果来自固定数据集、真实依赖链路或有边界压力测试。
        Mock、静态检查和真实端到端证据在项目中被明确区分。
      </p>
    </section>

    <section class="metric-grid page-shell" aria-label="项目核心验证指标">
      <MetricCard
        v-for="metric in metrics"
        :key="metric.label"
        v-bind="metric"
      />
    </section>

    <section class="evidence-section page-shell">
      <div class="section-heading">
        <p class="eyebrow">RESULTS BY CAPABILITY</p>
        <h2>结论、测试条件与能力边界放在一起</h2>
      </div>
      <div class="evidence-table" role="table" aria-label="工程验证结果">
        <div class="evidence-table__head" role="row">
          <span role="columnheader">能力</span>
          <span role="columnheader">验证方式</span>
          <span role="columnheader">实际结果</span>
          <span role="columnheader">结论边界</span>
        </div>
        <article v-for="item in evidence" :key="item.name" role="row">
          <div role="cell">
            <span :class="`evidence-badge evidence-badge--${item.level.toLowerCase()}`">
              {{ item.level }}
            </span>
            <strong>{{ item.name }}</strong>
          </div>
          <p role="cell">{{ item.method }}</p>
          <p class="evidence-table__result" role="cell">{{ item.result }}</p>
          <p role="cell">{{ item.boundary }}</p>
        </article>
      </div>
    </section>

    <section class="quality-section page-shell">
      <div class="section-heading">
        <p class="eyebrow">EVIDENCE LADDER</p>
        <h2>绿色输出不等于真实链路通过</h2>
      </div>
      <ol class="quality-ladder">
        <li v-for="level in levels" :key="level.name">
          <span>{{ level.name }}</span>
          <div>
            <strong>{{ level.title }}</strong>
            <p>{{ level.description }}</p>
          </div>
        </li>
      </ol>
    </section>

    <section class="boundary-section page-shell">
      <div>
        <p class="eyebrow">KNOWN BOUNDARIES</p>
        <h2>仍在继续验证的部分</h2>
      </div>
      <ul>
        <li>Agent 当前为同步 HTTP 门面，真实 SSE 与服务端协作取消仍在规划中。</li>
        <li>RAG 双 Generation 已完成代码与零服务回归，真实 PostgreSQL 并发切换仍待验证。</li>
        <li>本机结果用于证明设计与当前环境表现，不外推为生产集群容量。</li>
      </ul>
      <RouterLink to="/chat">体验受控 Agent <span>→</span></RouterLink>
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
import MetricCard from '@/shared/ui/MetricCard.vue'

const metrics = [
  {
    value: '102.95',
    label: '点赞稳定档 RPS',
    note: 'P99 17.98ms · 三轮中位数'
  },
  {
    value: '52.42',
    label: '评论稳定档 RPS',
    note: 'P99 42.73ms · 8664/8664 落库'
  },
  {
    value: '91.98%',
    label: 'Reranker Recall@5',
    note: '150 篇文章 · 150 题困难集'
  },
  {
    value: '90.11%',
    label: 'Reranker MRR',
    note: '真实 Qwen3 Embedding 与 Reranker'
  }
]

const evidence = [
  {
    level: 'L4',
    name: '点赞可靠链路',
    method: '100 RPS 稳定档三轮；300 RPS 边界轮；同时核对 Redis 队列、MySQL 与 Outbox。',
    result: '稳定档 102.95 RPS，P99 17.98ms；边界积压约 21 秒归零。',
    boundary: '代表当前 WSL 单机环境，不是分布式集群容量上限。'
  },
  {
    level: 'L4',
    name: '评论可靠链路',
    method: '当前 RocketMQ → MySQL 直写链路，20/50/100 RPS 阶梯与三轮稳定档。',
    result: '稳定档 52.42 RPS，P99 42.73ms；8664/8664 条最终落库，重复事件为 0。',
    boundary: '100 RPS 暴露客户端超时后的请求级幂等改进点。'
  },
  {
    level: 'L3',
    name: '微服务与 Trace',
    method: '真实启动 Gateway、认证、消息与主站，查询 SkyWalking Trace V2 和服务拓扑。',
    result: 'HTTP、OpenFeign 与 RocketMQ 异步段可通过同一 Trace 关联。',
    boundary: '证明链路可追踪，不代表持续高负载下的 OAP 容量。'
  },
  {
    level: 'L4',
    name: 'RAG 检索质量',
    method: '150 篇文章、150 道困难问题，比较 BM25、Dense、Hybrid 与 Reranker。',
    result: 'Reranker Recall@5 0.9198，MRR 0.9011，引用命中率 0.9556。',
    boundary: '困难集为冻结合成评测集，拒答阈值仍存在继续校准空间。'
  },
  {
    level: 'L3',
    name: '受控 Agent',
    method: '真实 Planner、Qwen3 Embedding、pgvector、Reranker 与 HY3 生成链路。',
    result: '端到端 1/1 通过，耗时 9.41 秒，完成检索、精排和合法引用。',
    boundary: '证明单次完整链路成立，不外推为长期模型可用性或生产 P95。'
  },
  {
    level: 'L2',
    name: '故障回归',
    method: '零服务回归覆盖降级、舱壁、重复乱序、索引屏障、Agent 越权与循环。',
    result: 'Ragent、社区与 AIGC 共 52 个用例，34 秒完成，0 失败。',
    boundary: '属于组件与进程内验证，真实依赖停止恢复单独按 L3/L4 记录。'
  }
]

const levels = [
  {
    name: 'L0',
    title: '静态门禁',
    description: '类型、语法、配置、构建产物和敏感信息扫描。'
  },
  {
    name: 'L1',
    title: '单元测试',
    description: '验证分支、状态机和接口契约，不冒充真实中间件。'
  },
  {
    name: 'L2',
    title: '组件集成',
    description: '验证 Spring 装配、事务协作与进程内边界。'
  },
  {
    name: 'L3',
    title: '真实依赖',
    description: '连接 MySQL、Redis、RocketMQ、pgvector 或真实模型。'
  },
  {
    name: 'L4',
    title: '端到端与压测',
    description: '记录请求量、延迟、错误、积压、恢复和最终一致性。'
  }
]

const loginDialogClicked = ref(false)
provide('loginDialogClicked', () => {
  loginDialogClicked.value = !loginDialogClicked.value
})
</script>

<style scoped>
.evidence-page {
  padding-bottom: var(--space-16);
}

.eyebrow {
  color: var(--color-brand-strong);
  font-size: 0.7rem;
  font-weight: 780;
  letter-spacing: 0.15em;
}

.evidence-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(17rem, 0.8fr);
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

.evidence-hero > p {
  color: var(--color-text-secondary);
  line-height: 1.9;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.evidence-section,
.quality-section {
  padding-top: clamp(5rem, 10vw, 8rem);
}

.section-heading {
  max-width: 48rem;
}

.section-heading h2,
.boundary-section h2 {
  margin-top: var(--space-3);
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 760;
  letter-spacing: -0.045em;
}

.evidence-table {
  overflow: hidden;
  margin-top: var(--space-10);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
}

.evidence-table__head,
.evidence-table article {
  display: grid;
  grid-template-columns: 0.75fr 1.35fr 1.25fr 1fr;
  gap: var(--space-5);
  padding: var(--space-5);
}

.evidence-table__head {
  background: var(--color-text);
  color: #aeb6ca;
  font-size: 0.62rem;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.evidence-table article + article {
  border-top: 1px solid var(--color-border-subtle);
}

.evidence-table article > div {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.evidence-table article strong {
  font-size: 0.82rem;
}

.evidence-table article p {
  color: var(--color-text-muted);
  font-size: 0.72rem;
  line-height: 1.65;
}

.evidence-table .evidence-table__result {
  color: var(--color-text-secondary);
  font-weight: 650;
}

.evidence-badge {
  flex: none;
  padding: 0.22rem 0.38rem;
  border-radius: 0.35rem;
  font-size: 0.58rem;
  font-weight: 800;
}

.evidence-badge--l4 {
  background: var(--color-accent-soft);
  color: #087463;
}

.evidence-badge--l3 {
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.evidence-badge--l2 {
  background: #fff3dc;
  color: var(--color-warning);
}

.quality-ladder {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  margin-top: var(--space-10);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-border-subtle);
}

.quality-ladder li {
  padding: var(--space-5);
  background: var(--color-surface);
}

.quality-ladder li > span {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-size: 0.72rem;
  font-weight: 800;
}

.quality-ladder strong {
  display: block;
  margin-top: var(--space-5);
  font-size: 0.84rem;
}

.quality-ladder p {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.72rem;
  line-height: 1.65;
}

.boundary-section {
  display: grid;
  grid-template-columns: 0.8fr 1.25fr auto;
  gap: clamp(2rem, 6vw, 5rem);
  align-items: center;
  margin-top: var(--space-16);
  padding: clamp(2rem, 5vw, 4rem);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, #eae7ff, #e7faf6);
}

.boundary-section ul {
  display: grid;
  gap: var(--space-3);
}

.boundary-section li {
  position: relative;
  padding-left: var(--space-5);
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  line-height: 1.7;
}

.boundary-section li::before {
  position: absolute;
  top: 0.55rem;
  left: 0;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 999px;
  background: var(--color-brand);
  content: '';
}

.boundary-section > a {
  display: flex;
  min-width: 10rem;
  justify-content: space-between;
  padding: 0.8rem 1rem;
  border-radius: var(--radius-sm);
  background: var(--color-text);
  color: white;
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 980px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .evidence-table__head {
    display: none;
  }

  .evidence-table article {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quality-ladder {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .boundary-section {
    grid-template-columns: 1fr;
  }

  .boundary-section > a {
    width: fit-content;
  }
}

@media (max-width: 640px) {
  .evidence-hero {
    grid-template-columns: 1fr;
    padding-block: 3.5rem;
  }

  .metric-grid,
  .evidence-table article,
  .quality-ladder {
    grid-template-columns: 1fr;
  }

  .evidence-table article {
    gap: var(--space-3);
  }
}
</style>
