export type RequestParams = Record<string, unknown>

export interface ApiStatus {
  code: number
  msg: string
}

export interface ApiEnvelope<T> {
  result: T
  status: ApiStatus
  redirect?: boolean
}

export class ApiError extends Error {
  readonly code?: number
  readonly httpStatus?: number

  constructor(
    message: string,
    options: {
      code?: number
      httpStatus?: number
      cause?: unknown
    } = {}
  ) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.code = options.code
    this.httpStatus = options.httpStatus
  }
}
