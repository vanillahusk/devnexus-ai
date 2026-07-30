import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { UserHomeTabTypeEnum } from '@/constants/UserHomeTabTypeConstants'
import { pinia } from '@/app/pinia'
import { useGlobalStore } from '@/stores/global'
import { fetchGlobalInfo } from '@/services/global'
import { messageTip } from '@/util/utils'
import { createAuthGuard } from './authGuard'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/login',
      name: 'login',
      component: HomeView,
    },
    {
      path: '/index',
      name: 'index',
      component: HomeView,
    },
    {
      path: '/article/detail/:articleId',
      name: "articleDetail",
      component: () => import('@/views/ArticleDetailView.vue')
    },
    {
      path: '/article/edit',
      name: "articleNew",
      component: () => import('@/views/ArticleEditView.vue')
    },
    {
      path: '/article/edit/:articleId',
      name: "articleEdit",
      component: () => import('@/views/ArticleEditView.vue')
    },
    {
      path: '/article/tag/:tagId',
      name: "tagArticles",
      component: () => import('@/views/TagArticlesView.vue')
    },
    // 作者个人信息页面
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('@/views/AboutView.vue')
    },
    {
      path: '/architecture',
      name: 'architecture',
      component: () => import('@/views/ArchitectureView.vue')
    },
    {
      path: '/evidence',
      name: 'evidence',
      component: () => import('@/views/EvidenceView.vue')
    },
    // 后续更新计划
    {
      path: '/plan',
      name: 'plan',
      component: () => import('@/views/PlanView.vue')
    },
    {
      path: '/column',
      name: 'column',
      component: () => import('@/views/ColumnView.vue')
    },
    // ai聊天页
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue')
    },
    // 工具页
    {
      path: '/tools',
      name: 'tools',
      component: () => import('@/views/ToolsView.vue'),
      redirect() {
        return { name: 'excel' }
      },
      children: [
        {
          path: 'excel',
          name: 'excel',
          component: () => import('@/views/tools/ToolsExcel.vue')
        }
      ]
    },
    {
      path: '/column/:columnId/:sectionId',
      name: 'columnDetail',
      component: () => import('@/views/ColumnDetailView.vue')
    },
    {
      path: '/user/:userId',
      redirect(to) {
        return { name: 'userHome', params: { userId: to.params.userId, typeName: UserHomeTabTypeEnum.ArticlesTab } }
      },
    },
    {
      path: '/user/:userId/:typeName',
      name: 'userHome',
      component: () => import('@/views/UserHomeView.vue'),
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/notice',
      redirect() {
        return { name: 'notice', params: { noticeType: 'comment' } }
      },
    },
    {
      path: '/notice/:noticeType',
      name: "notice",
      component: () => import('@/views/NoticeView.vue'),
    }
  ]
})

const globalStore = useGlobalStore(pinia)

router.beforeEach(
  createAuthGuard({
    loadGlobalInfo: fetchGlobalInfo,
    updateGlobal: (global) => globalStore.setGlobal(global),
    warn: (message) => messageTip(message, 'warning')
  })
)

export default router
