import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const article = {
  articleId: '42',
  articleType: 1,
  author: '7',
  authorName: 'DevNexus 编辑部',
  authorAvatar: '',
  title: 'Outbox 为什么仍然可能重复投递',
  shortTitle: 'Outbox 重复投递',
  summary: '从本地事务、Broker 确认和消费幂等解释至少一次投递。',
  cover: '',
  content:
    '# Outbox 为什么仍然可能重复投递\n\n发送成功后，应用可能在更新本地消息状态前宕机。',
  sourceType: 'ORIGINAL',
  sourceUrl: '',
  status: 1,
  officalStat: 0,
  toppingStat: 1,
  creamStat: 1,
  createTime: '1785427200000',
  lastUpdateTime: '1785427200000',
  category: {
    categoryId: 1,
    category: '后端架构',
    rank: 1,
    status: 1
  },
  tags: [
    {
      tagId: '1',
      tag: 'RocketMQ',
      status: 1,
      selected: false
    }
  ],
  praised: false,
  commented: false,
  collected: false,
  count: {
    praiseCount: 12,
    readCount: 256,
    collectionCount: 8,
    commentCount: 3
  },
  praisedUsers: []
}

const global = {
  siteInfo: {
    cdnImgStyle: '',
    websiteRecord: '',
    pageSize: 10,
    websiteName: 'DevNexus AI',
    websiteLogoUrl: '',
    websiteFaviconIconUrl: '',
    contactMeWxQrCode: '',
    contactMeStarQrCode: '',
    contactMeTitle: '',
    wxLoginUrl: '',
    host: 'http://127.0.0.1:5174',
    welcomeInfo: '',
    starInfo: '',
    oss: '',
    needLoginArticleReadCount: ''
  },
  siteStatisticInfo: { day: null, path: null, pv: 0, uv: 0 },
  todaySiteStatisticInfo: { day: null, path: null, pv: 0, uv: 0 },
  env: 'e2e',
  isLogin: true,
  user: {
    company: 'DevNexus AI',
    createTime: '',
    deleted: 0,
    extend: '',
    id: '7',
    photo: '',
    position: '项目维护者',
    profile: '',
    region: '',
    role: 'ADMIN',
    starStatus: '',
    updateTime: '',
    userId: '7',
    userName: 'portfolio_e2e'
  },
  msgNum: 0,
  onlineCnt: 1,
  currentDomain: '127.0.0.1',
  ogp: [],
  jsonLd: ''
}

function response(result: unknown) {
  return {
    global,
    result,
    status: { code: 0, msg: 'OK' },
    redirect: false
  }
}

async function json(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

async function installApiMocks(page: Page) {
  await page.route('**/api/global/info', (route) =>
    json(route, response({}))
  )
  await page.route('**/article/api/articles/category**', (route) =>
    json(
      route,
      response({
        articles: {
          records: [article],
          total: 1,
          size: 10,
          current: 1,
          pages: 1
        },
        categories: [article.category],
        topArticles: [article]
      })
    )
  )
  await page.route('**/article/api/data/detail/42', (route) =>
    json(
      route,
      response({
        article,
        comments: [],
        pendingComments: [],
        hotComment: null,
        author: {
          followCount: 0,
          fansCount: 0,
          joinDayCount: 30,
          articleCount: 30,
          praiseCount: 128,
          readCount: 4096,
          collectionCount: 64,
          followed: false,
          infoPercent: 100,
          yearArticleList: []
        },
        other: { readType: 0 },
        sideBarItems: [],
        columnId: 0,
        sectionId: 0,
        commentPending: false
      })
    )
  )
  await page.route('**/ai/agent/api/stream', async (route) => {
    const result = {
      traceId: 'trace-e2e-portfolio',
      mode: 'AGENT',
      answer: 'Outbox 采用至少一次投递，因此消费者仍需幂等。[ref:c42]',
      fallback: false,
      failureCode: '',
      toolCalls: [
        {
          toolName: 'searchKnowledge',
          status: 'SUCCESS',
          citationCount: 1
        }
      ],
      citations: [
        {
          chunkId: 'c42',
          articleId: '42',
          articleVersion: '3',
          title: article.title,
          headingPath: '重复投递窗口',
          snippet: '发送成功后，本地状态更新前宕机会触发再次投递。'
        }
      ],
      usage: {
        steps: 2,
        toolCalls: 1,
        retrievalCalls: 1,
        rerankCalls: 1,
        modelCalls: 2,
        estimatedTokens: 620,
        modelName: 'e2e-fixture',
        remainingMillis: 18_000
      }
    }
    const events = [
      ['accepted', { requestId: 'req-e2e', traceId: result.traceId }],
      ['status', { requestId: 'req-e2e', phase: 'PLANNING' }],
      ['status', { requestId: 'req-e2e', phase: 'RETRIEVING' }],
      ['status', { requestId: 'req-e2e', phase: 'GENERATING' }],
      ['delta', { requestId: 'req-e2e', text: 'Outbox 采用至少一次投递，' }],
      ['result', { requestId: 'req-e2e', result }],
      ['done', { requestId: 'req-e2e', finishReason: 'COMPLETED' }]
    ]
      .map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
      .join('')
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      headers: {
        'Cache-Control': 'no-cache'
      },
      body: events
    })
  })
}

async function installScreenshotFont(page: Page) {
  if (process.env.PORTFOLIO_SCREENSHOTS !== 'true') {
    return
  }
  const fontPath = process.env.PORTFOLIO_CJK_FONT_PATH
  if (!fontPath) {
    throw new Error(
      '生成中文截图时必须通过 PORTFOLIO_CJK_FONT_PATH 指定本机字体'
    )
  }
  await page.route('**/__e2e-font.ttf', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'font/ttf',
      path: fontPath
    })
  )
  await page.addInitScript(() => {
    window.addEventListener('DOMContentLoaded', () => {
      const style = document.createElement('style')
      style.textContent = `
        @font-face {
          font-family: "DevNexus Screenshot CJK";
          src: url("/__e2e-font.ttf") format("truetype");
          font-style: normal;
          font-weight: 100 900;
          font-display: block;
        }
        html, body, body *, button, input, textarea {
          font-family: "DevNexus Screenshot CJK", sans-serif !important;
        }
      `
      document.head.appendChild(style)
    })
  })
}

async function capture(page: Page, name: string) {
  if (process.env.PORTFOLIO_SCREENSHOTS !== 'true') {
    return
  }
  await page.evaluate(() => window.scrollTo({ top: 0, left: 0 }))
  await page.evaluate(() => document.fonts.ready)
  const targetDir = resolve(process.cwd(), '../docs/images')
  await mkdir(targetDir, { recursive: true })
  await page.screenshot({
    path: resolve(targetDir, `${name}.jpg`),
    type: 'jpeg',
    quality: 86,
    fullPage: false
  })
}

test.beforeEach(async ({ page }) => {
  await installScreenshotFont(page)
  await installApiMocks(page)
})

test('首页 → 社区文章 → Agent 引用 → 原文形成连续公开主流程', async ({
  page
}, testInfo) => {
  const desktop = testInfo.project.name === 'chromium-desktop'
  await page.goto('/')
  await expect(
    page.getByRole('heading', { name: /把社区业务做可靠/ })
  ).toBeVisible()
  await expect(
    page.locator('.cdc-article-panel__title').filter({ hasText: article.title })
  ).toBeVisible()
  if (desktop) {
    await capture(page, 'portfolio-home')
  }

  await page.locator('.cdc-article-panel__link').first().click()
  await expect(page).toHaveURL(/\/article\/detail\/42$/)
  await expect(page.locator('#postsTitle')).toHaveText(article.title)
  if (desktop) {
    await capture(page, 'portfolio-article')
  }

  await page.goto('/chat')
  await page
    .getByPlaceholder('例如：为什么 Outbox 不能保证绝对只投递一次？')
    .fill('为什么 Outbox 可能重复投递？')
  await page.getByRole('button', { name: /发送问题/ }).click()

  await expect(
    page.getByText('Outbox 采用至少一次投递，因此消费者仍需幂等。')
  ).toBeVisible()
  const citation = page.getByTestId('agent-citation')
  await expect(citation).toContainText(article.title)
  if (desktop) {
    await capture(page, 'portfolio-agent')
  }
  await citation.click()

  await expect(page).toHaveURL(/\/article\/detail\/42$/)
  await expect(page.locator('#postsTitle')).toHaveText(article.title)
})

test('作品集核心页面在当前视口没有横向溢出', async (
  { page },
  testInfo
) => {
  for (const path of ['/', '/architecture', '/evidence', '/chat', '/about']) {
    await page.goto(path)
    await expect(page.locator('main')).toBeVisible()
    if (
      process.env.PORTFOLIO_SCREENSHOTS === 'true' &&
      testInfo.project.name === 'chromium-desktop' &&
      path === '/architecture'
    ) {
      await capture(page, 'portfolio-architecture')
    }
    const dimensions = await page.evaluate(() => ({
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: document.documentElement.clientWidth
    }))
    expect(
      dimensions.documentWidth,
      `${path} 不应产生横向滚动`
    ).toBeLessThanOrEqual(dimensions.viewportWidth + 1)
  }
})
