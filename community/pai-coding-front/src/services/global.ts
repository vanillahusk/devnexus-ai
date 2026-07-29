import type { GlobalResponse } from '@/http/ResponseTypes/CommonResponseType'
import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import { doGet } from '@/http/BackendRequests'
import { GLOBAL_INFO_URL } from '@/http/URL'

export async function fetchGlobalInfo(): Promise<GlobalResponse> {
  const response = await doGet<CommonResponse>(GLOBAL_INFO_URL, {})
  return response.data.global
}
