import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { createHttpClient } from '../client'

function response(
  config: InternalAxiosRequestConfig,
  data: unknown,
  status = 200
): Promise<AxiosResponse> {
  return Promise.resolve({
    config,
    data,
    headers: {},
    status,
    statusText: status === 200 ? 'OK' : 'ERROR'
  })
}

describe('createHttpClient', () => {
  it('使用独立实例附加登录令牌', async () => {
    const client = createHttpClient(
      {},
      {
        getToken: () => 'token-value',
        onUnauthorized: vi.fn()
      }
    )
    client.defaults.adapter = (config) =>
      response(config, { status: { code: 200, msg: 'ok' } })

    const result = await client.get('/profile')

    expect(result.config.headers.Authorization).toBe('token-value')
  })

  it('业务鉴权失败时拒绝请求而不是返回 undefined', async () => {
    const onUnauthorized = vi.fn()
    const client = createHttpClient(
      {},
      {
        getToken: () => null,
        onUnauthorized
      }
    )
    client.defaults.adapter = (config) =>
      response(config, {
        status: {
          code: 901,
          msg: 'token expired'
        }
      })

    await expect(client.get('/profile')).rejects.toMatchObject({
      name: 'ApiError',
      code: 901,
      message: 'token expired'
    })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('正常业务响应保持 AxiosResponse 契约', async () => {
    const client = createHttpClient(
      {},
      {
        getToken: () => null,
        onUnauthorized: vi.fn()
      }
    )
    const body = {
      status: { code: 200, msg: 'ok' },
      result: { articleId: 1 }
    }
    client.defaults.adapter = (config) => response(config, body)

    const result = await client.get('/article/1')

    expect(result.data).toEqual(body)
    expect(result.status).toBe(200)
  })
})
