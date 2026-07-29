import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import { doGet } from '@/http/BackendRequests'
import { LOGOUT_URL } from '@/http/URL'

export async function logout(): Promise<void> {
  const response = await doGet<CommonResponse>(LOGOUT_URL, {})
  if (response.data.status.code !== 0) {
    throw new Error(response.data.status.msg || '退出登录失败')
  }
}
