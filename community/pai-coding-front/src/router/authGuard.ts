import type {
  NavigationGuardReturn,
  RouteLocationNormalized
} from 'vue-router'
import type { GlobalResponse } from '@/http/ResponseTypes/CommonResponseType'

interface AuthGuardDependencies {
  loadGlobalInfo: () => Promise<GlobalResponse>
  updateGlobal: (global: GlobalResponse) => void
  warn: (message: string) => void
}

export function createAuthGuard(dependencies: AuthGuardDependencies) {
  return async (
    to: RouteLocationNormalized
  ): Promise<NavigationGuardReturn> => {
    if (!to.meta.requiresAuth) {
      return true
    }

    try {
      const global = await dependencies.loadGlobalInfo()
      dependencies.updateGlobal(global)
      if (global.isLogin) {
        return true
      }
      dependencies.warn('请先登录')
    } catch {
      dependencies.warn('登录状态检查失败，请稍后重试')
    }

    return {
      name: 'home',
      query: {
        redirect: to.fullPath
      }
    }
  }
}
