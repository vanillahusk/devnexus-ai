import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { LOCALSTORAGE_AUTHORIZATION } from '@/constants/LocalStorageConstants'
import { runtimeConfig } from '@/config/runtime'
import { clearStorage, getTokenName, messageConfirm, messageTip } from '@/util/utils'
import { ApiError } from './types'

interface ResponseStatusBody {
  code?: number
  msg?: string
  status?: {
    code?: number
    msg?: string
  }
}

interface HttpClientHooks {
  getToken: () => string | null
  onUnauthorized: (message: string) => void
}

interface HttpClientOptions {
  baseURL?: string
  timeout?: number
  withCredentials?: boolean
}

let loginPromptVisible = false

function readToken(): string | null {
  return (
    window.sessionStorage.getItem(getTokenName()) ??
    window.localStorage.getItem(getTokenName())
  )
}

function attachAuthorization(
  config: InternalAxiosRequestConfig,
  getToken: () => string | null
): InternalAxiosRequestConfig {
  const token = getToken()
  if (token) {
    config.headers.set(LOCALSTORAGE_AUTHORIZATION, token)
  }
  return config
}

function responseCode(data: unknown): { code?: number; message?: string } {
  if (!data || typeof data !== 'object') {
    return {}
  }

  const body = data as ResponseStatusBody
  return {
    code: body.status?.code ?? body.code,
    message: body.status?.msg ?? body.msg
  }
}

function promptForLogin(message: string): void {
  if (loginPromptVisible) {
    return
  }
  loginPromptVisible = true

  void messageConfirm(`${message}。是否重新登录？`)
    .then(() => {
      clearStorage()
      window.location.assign('/')
    })
    .catch(() => {
      messageTip('已取消跳转登录', 'warning')
    })
    .finally(() => {
      loginPromptVisible = false
    })
}

function normalizeAxiosError(error: AxiosError<ResponseStatusBody>): ApiError {
  const response = error.response
  const details = responseCode(response?.data)
  return new ApiError(details.message || error.message || '网络请求失败', {
    code: details.code,
    httpStatus: response?.status,
    cause: error
  })
}

function installInterceptors(
  client: AxiosInstance,
  hooks: HttpClientHooks
): AxiosInstance {
  client.interceptors.request.use((config) =>
    attachAuthorization(config, hooks.getToken)
  )

  client.interceptors.response.use(
    (response: AxiosResponse) => {
      const details = responseCode(response.data)
      if (details.code !== undefined && details.code > 900) {
        const message = details.message || '登录状态已失效'
        hooks.onUnauthorized(message)
        return Promise.reject(
          new ApiError(message, {
            code: details.code,
            httpStatus: response.status
          })
        )
      }
      return response
    },
    (error: AxiosError<ResponseStatusBody>) => {
      if (error.response?.status === 401) {
        hooks.onUnauthorized('登录状态已失效')
      }
      return Promise.reject(normalizeAxiosError(error))
    }
  )

  return client
}

export function createHttpClient(
  options: HttpClientOptions,
  hooks: HttpClientHooks
): AxiosInstance {
  return installInterceptors(
    axios.create({
      baseURL: options.baseURL,
      timeout: options.timeout,
      withCredentials: options.withCredentials ?? true,
      headers: {
        'Content-Type': 'application/json'
      }
    }),
    hooks
  )
}

export const httpClient = createHttpClient(
  {
    baseURL: runtimeConfig.apiBaseUrl || undefined,
    timeout: runtimeConfig.requestTimeoutMs,
    withCredentials: true
  },
  {
    getToken: readToken,
    onUnauthorized: promptForLogin
  }
)

export const externalHttpClient = axios.create({
  timeout: runtimeConfig.requestTimeoutMs,
  withCredentials: false,
  headers: {
    'Content-Type': 'application/json'
  }
})
