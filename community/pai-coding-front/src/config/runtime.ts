function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

function currentOrigin(): string {
  return typeof window === 'undefined' ? '' : window.location.origin
}

function toWebSocketUrl(httpUrl: string): string {
  return httpUrl.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
}

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? ''
const configuredWsBaseUrl = import.meta.env.VITE_WS_BASE_URL?.trim() ?? ''

export const runtimeConfig = Object.freeze({
  apiBaseUrl: trimTrailingSlash(configuredApiBaseUrl),
  wsBaseUrl: trimTrailingSlash(
    configuredWsBaseUrl || toWebSocketUrl(configuredApiBaseUrl || currentOrigin())
  ),
  excelServiceBaseUrl: trimTrailingSlash(
    import.meta.env.VITE_EXCEL_SERVICE_BASE_URL?.trim() ?? ''
  ),
  requestTimeoutMs: Number(import.meta.env.VITE_REQUEST_TIMEOUT_MS ?? 15_000)
})
