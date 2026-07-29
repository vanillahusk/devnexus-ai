import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { defaultGlobalResponse } from '@/http/ResponseTypes/CommonResponseType'
import { createAuthGuard } from '../authGuard'

function route(
  requiresAuth: boolean,
  fullPath = '/notice/comment'
): RouteLocationNormalized {
  return {
    fullPath,
    meta: { requiresAuth }
  } as RouteLocationNormalized
}

describe('createAuthGuard', () => {
  it('公开路由不请求登录状态', async () => {
    const loadGlobalInfo = vi.fn()
    const guard = createAuthGuard({
      loadGlobalInfo,
      updateGlobal: vi.fn(),
      warn: vi.fn()
    })

    await expect(guard(route(false))).resolves.toBe(true)
    expect(loadGlobalInfo).not.toHaveBeenCalled()
  })

  it('已登录用户只刷新一次状态并放行', async () => {
    const global = {
      ...defaultGlobalResponse,
      isLogin: true
    }
    const updateGlobal = vi.fn()
    const guard = createAuthGuard({
      loadGlobalInfo: vi.fn().mockResolvedValue(global),
      updateGlobal,
      warn: vi.fn()
    })

    await expect(guard(route(true))).resolves.toBe(true)
    expect(updateGlobal).toHaveBeenCalledOnce()
    expect(updateGlobal).toHaveBeenCalledWith(global)
  })

  it('未登录时返回一次确定的首页重定向', async () => {
    const warn = vi.fn()
    const guard = createAuthGuard({
      loadGlobalInfo: vi.fn().mockResolvedValue(defaultGlobalResponse),
      updateGlobal: vi.fn(),
      warn
    })

    await expect(guard(route(true))).resolves.toEqual({
      name: 'home',
      query: { redirect: '/notice/comment' }
    })
    expect(warn).toHaveBeenCalledWith('请先登录')
  })

  it('登录状态接口失败时关闭失败并返回首页', async () => {
    const warn = vi.fn()
    const guard = createAuthGuard({
      loadGlobalInfo: vi.fn().mockRejectedValue(new Error('network down')),
      updateGlobal: vi.fn(),
      warn
    })

    await expect(guard(route(true))).resolves.toEqual({
      name: 'home',
      query: { redirect: '/notice/comment' }
    })
    expect(warn).toHaveBeenCalledWith('登录状态检查失败，请稍后重试')
  })
})
