<template>
  <HeaderBar />

  <main class="article-page">
    <section v-if="loading" class="article-state page-shell" aria-busy="true">
      <el-skeleton animated :rows="12" />
    </section>

    <section v-else-if="errorMessage" class="article-state page-shell" role="alert">
      <span class="article-state__icon" aria-hidden="true">!</span>
      <h1>文章暂时无法加载</h1>
      <p>{{ errorMessage }}</p>
      <div>
        <button type="button" @click="loadArticleDetail">重新加载</button>
        <RouterLink to="/">返回首页</RouterLink>
      </div>
    </section>

    <div v-else-if="articleReady" class="article-detail">
      <div class="col-body pg-2-article" id="article-detail-body-div">
        <div class="com-3-layout">
          <div class="layout-main">
            <ArticleDetail :article-vo="articleVo" />

            <ArticleAiAssistant
              :article-id="Number(articleId)"
              :is-login="global.isLogin"
            />

            <CommentList
              :comments="articleVo.comments"
              :pending-comments="articleVo.pendingComments"
              :hot-comment="articleVo.hotComment"
              :article="articleVo.article"
            />

            <section class="correlation-article bg-color-white" id="relatedRecommend">
              <h4 class="correlation-article-title">相关推荐</h4>
              <div class="bg-color-white"><div id="articleList"></div></div>
            </section>
          </div>

          <aside class="layout-side hidden-when-screen-small flex-col flex">
            <UserCard :global="global" :user="articleVo.author" />
            <SideRecommendBar :sidebar-bar-items="articleVo.sideBarItems" />
            <div id="toc-container-position" class="hidden-when-screen-small"></div>
            <div class="sticky top-5 overflow-auto" id="content-menu">
              <el-scrollbar>
                <em>文章目录</em>
                <el-divider />
                <MdCatalog :editor-id="'id'" :scroll-element="scrollElement" />
              </el-scrollbar>
            </div>
          </aside>
        </div>
      </div>
    </div>
  </main>

  <Footer />
  <LoginDialog :clicked="loginDialogClicked" />
</template>

<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { MdCatalog } from 'md-editor-v3'
import Footer from '@/components/layout/Footer.vue'
import HeaderBar from '@/components/layout/HeaderBar.vue'
import ArticleDetail from '@/components/article/ArticleDetail.vue'
import ArticleAiAssistant from '@/components/article/ArticleAiAssistant.vue'
import UserCard from '@/components/user/UserCard.vue'
import SideRecommendBar from '@/views/article-detail/SideRecommendBar.vue'
import LoginDialog from '@/components/dialog/LoginDialog.vue'
import CommentList from '@/views/article-detail/CommentList.vue'
import {
  defaultArticleDetailResponse,
  type ArticleDetailResponse
} from '@/http/ResponseTypes/ArticleDetailResponseType'
import { fetchArticleDetail } from '@/services/article'
import { useGlobalStore } from '@/stores/global'
import { setTitle } from '@/util/utils'

const route = useRoute()
const router = useRouter()
const globalStore = useGlobalStore()
const global = globalStore.global
const articleId = String(route.params.articleId ?? '')
const scrollElement = document.documentElement
const articleVo = reactive<ArticleDetailResponse>({
  ...defaultArticleDetailResponse
})
const loading = ref(true)
const errorMessage = ref('')
const loginDialogClicked = ref(false)
const articleReady = computed(() => Boolean(articleVo.article.articleId))

provide('loginDialogClicked', () => {
  loginDialogClicked.value = !loginDialogClicked.value
})

async function loadArticleDetail(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await fetchArticleDetail(articleId)
    if (response.redirect) {
      await router.replace(
        `/column/${response.result.columnId}/${response.result.sectionId}`
      )
      return
    }
    globalStore.setGlobal(response.global)
    Object.assign(articleVo, response.result)
    setTitle(articleVo.article.title)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '请检查网络或稍后重试'
  } finally {
    loading.value = false
  }
}

provide('updateArticleComment', (response: ArticleDetailResponse) => {
  if (response.commentPending) {
    window.setTimeout(() => void loadArticleDetail(), 800)
    return
  }
  void loadArticleDetail()
})

onMounted(() => {
  void loadArticleDetail()
})
</script>

<style scoped>
.article-page {
  min-height: calc(100vh - var(--header-height) - var(--footer-height));
}

.article-state {
  min-height: 34rem;
  padding-block: clamp(3rem, 8vw, 6rem);
}

.article-state[role='alert'] {
  display: grid;
  max-width: 44rem;
  place-items: center;
  align-content: center;
  text-align: center;
}

.article-state__icon {
  display: grid;
  width: 3rem;
  height: 3rem;
  place-items: center;
  border-radius: 999px;
  background: #fff0f0;
  color: var(--color-danger);
  font-size: 1.25rem;
  font-weight: 800;
}

.article-state h1 {
  margin-top: var(--space-5);
  font-size: clamp(1.7rem, 4vw, 2.5rem);
}

.article-state p {
  margin-top: var(--space-3);
  color: var(--color-text-muted);
}

.article-state div {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-6);
}

.article-state button,
.article-state a {
  padding: 0.72rem 1rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
}

.article-state button {
  border-color: var(--color-text);
  background: var(--color-text);
  color: white;
}

div.layout-main {
  padding: 0 60px;
}

div#content-menu {
  height: calc(100vh - 70px);
}

@media (max-width: 768px) {
  div.layout-side {
    display: none;
  }

  div.layout-main {
    padding: 0;
  }
}
</style>
