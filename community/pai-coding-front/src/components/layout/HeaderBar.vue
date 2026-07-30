<template>
  <header class="app-header">
    <div class="app-header__inner page-shell">
      <RouterLink class="brand" to="/" aria-label="DevNexus AI 首页">
        <span class="brand__mark" aria-hidden="true">D</span>
        <span>
          <strong>DevNexus</strong>
          <small>Community × Intelligence</small>
        </span>
      </RouterLink>

      <nav class="desktop-nav" aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="header-actions">
        <RouterLink
          v-if="global.isLogin && !route.path.includes('/article/edit')"
          class="header-actions__write"
          to="/article/edit"
        >
          写文章
        </RouterLink>

        <button
          v-if="!global.isLogin"
          class="header-actions__login"
          type="button"
          @click="openLogin"
        >
          登录
        </button>

        <template v-else>
          <RouterLink class="notice-link" to="/notice/" aria-label="消息通知">
            <span aria-hidden="true">↗</span>
            <strong v-if="global.msgNum">{{ global.msgNum }}</strong>
          </RouterLink>

          <el-dropdown trigger="click">
            <button class="user-menu" type="button" aria-label="打开用户菜单">
              <img
                :src="global.user.photo || fallbackAvatar"
                :alt="global.user.userName || '用户头像'"
              />
              <span>{{ global.user.userName || '用户' }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openProfile">个人主页</el-dropdown-item>
                <el-dropdown-item @click="router.push('/tools/')">工具</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <button
          class="mobile-toggle"
          type="button"
          :aria-expanded="mobileMenuOpen"
          aria-label="切换导航菜单"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>

    <nav v-if="mobileMenuOpen" class="mobile-nav" aria-label="移动端导航">
      <RouterLink
        v-for="item in navigation"
        :key="item.to"
        :to="item.to"
        @click="mobileMenuOpen = false"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { inject, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { clearStorage, messageTip } from '@/util/utils'
import { logout } from '@/services/auth'
import { useGlobalStore } from '@/stores/global'

defineOptions({
  name: 'AppHeader'
})

const navigation = [
  { label: '首页', to: '/' },
  { label: '文章', to: '/#articles' },
  { label: '系统架构', to: '/architecture' },
  { label: '工程证据', to: '/evidence' },
  { label: 'AI 助手', to: '/chat' },
  { label: '项目介绍', to: '/about' }
]

const fallbackAvatar =
  'https://static.developers.pub/static/img/logo.b2ff606.jpeg'
const router = useRouter()
const route = useRoute()
const globalStore = useGlobalStore()
const global = globalStore.global
const mobileMenuOpen = ref(false)
const showLoginDialog = inject<() => void>('loginDialogClicked')

watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
  }
)

function openLogin(): void {
  if (showLoginDialog) {
    showLoginDialog()
    return
  }
  messageTip('登录组件尚未就绪', 'warning')
}

function openProfile(): void {
  void router.push(
    global.user.userId ? `/user/${global.user.userId}` : '/login'
  )
}

async function handleLogout(): Promise<void> {
  try {
    await logout()
    clearStorage()
    messageTip('退出登录成功', 'success')
    await router.replace('/')
    window.location.reload()
  } catch (error) {
    const message = error instanceof Error ? error.message : '退出登录失败'
    messageTip(message, 'error')
  }
}
</script>

<style scoped>
.app-header {
  position: sticky;
  z-index: 100;
  top: 0;
  border-bottom: 1px solid rgb(229 232 240 / 85%);
  background: rgb(255 255 255 / 88%);
  backdrop-filter: blur(18px);
}

.app-header__inner {
  display: flex;
  min-height: var(--header-height);
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text);
  text-decoration: none;
}

.brand:hover {
  color: var(--color-text);
}

.brand__mark {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 0.72rem;
  background: linear-gradient(145deg, var(--color-brand), #897cff);
  box-shadow: 0 8px 20px rgb(99 91 255 / 28%);
  color: white;
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 800;
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  font-family: var(--font-display);
  font-size: 0.98rem;
  font-weight: 760;
  letter-spacing: -0.02em;
}

.brand small {
  margin-top: 0.08rem;
  color: var(--color-text-muted);
  font-size: 0.62rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.desktop-nav a {
  padding: 0.55rem 0.8rem;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 620;
  text-decoration: none;
  transition: 160ms ease;
}

.desktop-nav a:hover,
.desktop-nav a.router-link-active {
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-actions__write,
.header-actions__login {
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 680;
  text-decoration: none;
}

.header-actions__write {
  padding: 0.55rem 0.8rem;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
}

.header-actions__login {
  padding: 0.62rem 1rem;
  background: var(--color-text);
  color: white;
}

.notice-link {
  position: relative;
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border: 1px solid var(--color-border-subtle);
  border-radius: 999px;
  color: var(--color-text-secondary);
  text-decoration: none;
}

.notice-link strong {
  position: absolute;
  top: -0.25rem;
  right: -0.25rem;
  min-width: 1.05rem;
  padding: 0.1rem 0.25rem;
  border-radius: 999px;
  background: var(--color-danger);
  color: white;
  font-size: 0.58rem;
  text-align: center;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0.2rem 0.55rem 0.2rem 0.2rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: 999px;
  background: white;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 0.78rem;
  font-weight: 650;
}

.user-menu img {
  width: 1.9rem;
  height: 1.9rem;
  border-radius: 999px;
  object-fit: cover;
}

.mobile-toggle {
  display: none;
  width: 2.4rem;
  height: 2.4rem;
  padding: 0.55rem;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-sm);
  background: white;
}

.mobile-toggle span {
  display: block;
  height: 2px;
  margin: 3px 0;
  border-radius: 2px;
  background: var(--color-text);
}

.mobile-nav {
  display: none;
}

@media (max-width: 900px) {
  .desktop-nav,
  .header-actions__write,
  .user-menu span {
    display: none;
  }

  .mobile-toggle {
    display: block;
  }

  .mobile-nav {
    display: grid;
    width: min(calc(100% - 1.25rem), var(--content-width));
    margin: 0 auto;
    padding: var(--space-2) 0 var(--space-4);
    border-top: 1px solid var(--color-border-subtle);
  }

  .mobile-nav a {
    padding: var(--space-3);
    border-radius: var(--radius-sm);
    color: var(--color-text-secondary);
    font-size: 0.9rem;
    font-weight: 650;
    text-decoration: none;
  }

  .mobile-nav a.router-link-active {
    background: var(--color-brand-soft);
    color: var(--color-brand-strong);
  }
}
</style>
