#!/usr/bin/env node

/**
 * Import an original DevNexus engineering article collection through public
 * application APIs. This deliberately does not write MySQL directly, so each
 * published article follows the normal transaction, Outbox and RAG index path.
 *
 * Required:
 *   DEVNEXUS_USERNAME=<admin username>
 *   DEVNEXUS_PASSWORD=<admin password>
 *
 * Optional:
 *   DEVNEXUS_BASE_URL=http://127.0.0.1:8081
 *   ARCHIVE_EXISTING=true CONFIRM_ARCHIVE=YES
 *   IMPORT_LIMIT=30
 */

import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const PROJECT_ROOT = path.resolve(SCRIPT_DIR, '..')
const ARTICLES_FILE = path.join(
  PROJECT_ROOT,
  'paicoding-web/src/test/resources/rag/expanded-hard-articles.tsv'
)
const QUERIES_FILE = path.join(
  PROJECT_ROOT,
  'paicoding-web/src/test/resources/rag/expanded-hard-queries.tsv'
)

const BASE_URL = (process.env.DEVNEXUS_BASE_URL || 'http://127.0.0.1:8081')
  .replace(/\/+$/, '')
const USERNAME = process.env.DEVNEXUS_USERNAME?.trim()
const PASSWORD = process.env.DEVNEXUS_PASSWORD
const ARCHIVE_EXISTING = process.env.ARCHIVE_EXISTING === 'true'
const CONFIRM_ARCHIVE = process.env.CONFIRM_ARCHIVE
const IMPORT_LIMIT = Number.parseInt(process.env.IMPORT_LIMIT || '30', 10)

if (!USERNAME || !PASSWORD) {
  fail('DEVNEXUS_USERNAME and DEVNEXUS_PASSWORD are required')
}
if (!Number.isInteger(IMPORT_LIMIT) || IMPORT_LIMIT < 1 || IMPORT_LIMIT > 30) {
  fail('IMPORT_LIMIT must be an integer between 1 and 30')
}
if (ARCHIVE_EXISTING && CONFIRM_ARCHIVE !== 'YES') {
  fail('ARCHIVE_EXISTING=true requires CONFIRM_ARCHIVE=YES')
}

function fail(message) {
  process.stderr.write(`[portfolio-import] ${message}\n`)
  process.exit(1)
}

function parseTsv(text) {
  const [headerLine, ...lines] = text.trim().split(/\r?\n/)
  const headers = headerLine.split('\t')
  return lines.map((line) => {
    const values = line.split('\t')
    return Object.fromEntries(headers.map((key, index) => [key, values[index] || '']))
  })
}

async function api(pathname, options = {}, token) {
  const headers = {
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: token } : {}),
    ...options.headers
  }
  const response = await fetch(`${BASE_URL}${pathname}`, {
    ...options,
    headers,
    signal: AbortSignal.timeout(15_000)
  })
  const text = await response.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`${pathname} returned non-JSON HTTP ${response.status}`)
  }
  if (!response.ok || body?.status?.code !== 0) {
    throw new Error(
      `${pathname} failed: HTTP ${response.status}, ${body?.status?.msg || 'unknown error'}`
    )
  }
  return body.result
}

function articleFamily(title) {
  if (/RAG|Embedding|检索|Reranker|Agent|模型|Prompt|向量|索引|Generation|SSE/.test(title)) {
    return 'ai'
  }
  if (/Gateway|Feign|Nacos|Sentinel|SkyWalking|Prometheus/.test(title)) {
    return 'cloud'
  }
  return 'backend'
}

function verificationFor(title) {
  if (/RAG|Embedding|检索|Reranker|向量/.test(title)) {
    return [
      '固定文章集合、问题集合和相关文档标注，避免每轮测试更换口径。',
      '分别记录关键词召回、向量召回、融合和精排结果，不用最终分数掩盖单通道退化。',
      '同时检查引用命中、拒答正确率、P95 延迟和模型调用成本。'
    ]
  }
  if (/Agent|Prompt|模型|SSE/.test(title)) {
    return [
      '用固定任务覆盖直接回答、一次检索、重复调用、越权、超时和模型失败。',
      '检查服务端硬预算，而不是只检查 Prompt 中是否写了限制。',
      '响应只保留最终答案、引用和低敏执行摘要，不暴露工具正文与内部推理。'
    ]
  }
  if (/Gateway|Feign|Nacos|Sentinel/.test(title)) {
    return [
      '分别注入连接超时、读取超时、服务下线和配置刷新。',
      '核对核心写入是否被非核心下游故障放大。',
      '记录降级结果、恢复时间和错误分类，而不是只看接口返回 200。'
    ]
  }
  if (/SkyWalking|Prometheus|线程池/.test(title)) {
    return [
      '使用低基数指标观察吞吐、延迟、队列、拒绝和依赖状态。',
      '通过 Trace ID 关联 HTTP、Feign 和 RocketMQ 异步段。',
      '指标用于发现趋势，单次业务键进入日志或 Trace，不进入 Prometheus Label。'
    ]
  }
  return [
    '验证正常提交、事务回滚、重复消息和旧版本迟到。',
    '停止并恢复依赖，记录消息停留位置、自动重试和最终恢复时间。',
    '核对事实表、Outbox、消费投影与死信状态，确认没有静默丢失。'
  ]
}

function referencesFor(title) {
  if (/RocketMQ|Outbox|DLQ|评论|消息/.test(title)) {
    return [
      ['Apache RocketMQ Documentation', 'https://rocketmq.apache.org/docs/']
    ]
  }
  if (/Redis|点赞/.test(title)) {
    return [['Redis Documentation', 'https://redis.io/docs/latest/']]
  }
  if (/Gateway|Feign|Nacos|Sentinel/.test(title)) {
    return [
      ['Spring Cloud Gateway Reference', 'https://docs.spring.io/spring-cloud-gateway/reference/'],
      ['Spring Cloud Alibaba Reference', 'https://sca.aliyun.com/en/docs/']
    ]
  }
  if (/SkyWalking/.test(title)) {
    return [['Apache SkyWalking Documentation', 'https://skywalking.apache.org/docs/']]
  }
  if (/Prometheus/.test(title)) {
    return [['Prometheus Documentation', 'https://prometheus.io/docs/']]
  }
  if (/向量|pgvector|Embedding|检索|RAG|Reranker|索引|Generation/.test(title)) {
    return [['pgvector', 'https://github.com/pgvector/pgvector']]
  }
  return [['Spring Boot Reference', 'https://docs.spring.io/spring-boot/index.html']]
}

function buildMarkdown(article, questions) {
  const checks = verificationFor(article.title)
  const references = referencesFor(article.title)
  return `# ${article.title}

> ${article.summary}

## 问题从哪里来

这篇文章记录 DevNexus AI 在真实改造过程中采用的工程边界。目标不是罗列组件，
而是说明状态由谁负责、故障发生后数据停在哪里，以及恢复以后怎样验证最终结果。

## 当前设计

${article.content}

设计时需要区分“同一事件被重复投递”和“两个不同版本的事件发生乱序”。
前者通常通过稳定事件 ID 与业务唯一约束处理，后者必须依靠业务版本、状态机或条件更新。
如果只完成其中一层，系统仍可能在重试或依赖抖动时产生错误副作用。

## 落地检查

${checks.map((item) => `- ${item}`).join('\n')}

## 常见追问

${questions.slice(0, 3).map((question) => `- ${question}`).join('\n')}

## 结论

架构设计的价值不在于使用了多少组件，而在于事实源唯一、异步边界清晰、
失败可以恢复，并且结论能够由测试、指标或真实依赖链路复核。

## 参考资料

${references.map(([name, url]) => `- [${name}](${url})`).join('\n')}

---

本文为 DevNexus AI 项目原创工程笔记，正文依据项目实现与验证结果重新撰写，
外部链接仅用于进一步阅读。`
}

function assignment(article, categoryIds, tagIds) {
  const family = articleFamily(article.title)
  if (family === 'ai') {
    return {
      categoryId: categoryIds.get('人工智能'),
      tagIds: [tagIds.get('RAG'), tagIds.get('AI 助手')].filter(Boolean)
    }
  }
  if (family === 'cloud') {
    return {
      categoryId: categoryIds.get('后端'),
      tagIds: [tagIds.get('Spring Boot'), tagIds.get('高并发')].filter(Boolean)
    }
  }
  return {
    categoryId: categoryIds.get('后端'),
    tagIds: [
      tagIds.get('Spring Boot'),
      tagIds.get(/Redis|点赞/.test(article.title) ? 'Redis' : '高并发')
    ].filter(Boolean)
  }
}

async function loadTaxonomy(token) {
  const categories = await api('/article/api/category/list', {}, token)
  const tagsPage = await api(
    '/article/api/tag/list?pageNumber=1&pageSize=100',
    {},
    token
  )
  return {
    categoryIds: new Map(categories.map((item) => [item.category, item.categoryId])),
    tagIds: new Map((tagsPage.list || []).map((item) => [item.tag, item.tagId]))
  }
}

async function loadPublicArticles(token) {
  const result = await api(
    '/article/api/articles/category?currentPage=1&pageSize=100',
    {},
    token
  )
  return result?.articles?.records || []
}

async function main() {
  process.stdout.write(`[portfolio-import] target=${BASE_URL}\n`)
  const login = await api('/new/login/username', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD })
  })
  const token = login?.token
  if (!token) {
    throw new Error('login succeeded without a token')
  }

  const [articleText, queryText] = await Promise.all([
    readFile(ARTICLES_FILE, 'utf8'),
    readFile(QUERIES_FILE, 'utf8')
  ])
  const allArticles = parseTsv(articleText)
  const queries = parseTsv(queryText)
  const articles = allArticles
    .filter((item) => Number(item.id) >= 3001 && Number(item.id) <= 3030)
    .slice(0, IMPORT_LIMIT)
  const questionsByArticle = new Map()
  for (const query of queries) {
    const articleId = query.expectedArticleIds
    if (!/^\d+$/.test(articleId)) continue
    const items = questionsByArticle.get(articleId) || []
    items.push(query.question)
    questionsByArticle.set(articleId, items)
  }

  const targetTitles = new Set(articles.map((item) => item.title))
  let publicArticles = await loadPublicArticles(token)

  if (ARCHIVE_EXISTING) {
    const legacy = publicArticles.filter((item) => !targetTitles.has(item.title))
    process.stdout.write(`[portfolio-import] archiving legacy articles=${legacy.length}\n`)
    for (const article of legacy) {
      await api(
        `/api/admin/article/delete?articleId=${encodeURIComponent(article.articleId)}`,
        { method: 'GET' },
        token
      )
    }
    publicArticles = publicArticles.filter((item) => targetTitles.has(item.title))
  }

  const { categoryIds, tagIds } = await loadTaxonomy(token)
  if (!categoryIds.get('后端') || !categoryIds.get('人工智能')) {
    throw new Error('required categories 后端/人工智能 do not exist')
  }

  const existingTitles = new Set(publicArticles.map((item) => item.title))
  let created = 0
  let skipped = 0
  for (const article of articles) {
    if (existingTitles.has(article.title)) {
      skipped += 1
      continue
    }
    const taxonomy = assignment(article, categoryIds, tagIds)
    const questions = questionsByArticle.get(article.id) || []
    await api(
      '/article/api/post',
      {
        method: 'POST',
        body: JSON.stringify({
          title: article.title,
          shortTitle: article.shortTitle,
          categoryId: taxonomy.categoryId,
          tagIds: taxonomy.tagIds,
          summary: article.summary,
          content: buildMarkdown(article, questions),
          articleType: 'BLOG',
          source: 2,
          actionType: 'post'
        })
      },
      token
    )
    created += 1
    process.stdout.write(`[portfolio-import] created ${created}/${articles.length}: ${article.title}\n`)
  }

  process.stdout.write(
    `[portfolio-import] complete created=${created} skipped=${skipped} target=${articles.length}\n`
  )
  process.stdout.write(
    '[portfolio-import] articles were published through the normal ArticleWriteService/Outbox path\n'
  )
}

main().catch((error) => {
  fail(error instanceof Error ? error.message : String(error))
})
