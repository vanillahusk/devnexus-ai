import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import type { NoticeMsgResponseType } from '@/http/ResponseTypes/NoticeMsgResponseType'
import { doGet } from '@/http/BackendRequests'
import { UNREAD_NOTICE_URL } from '@/http/URL'

export interface NoticePageQuery {
  currentPage: number
  pageSize: number
}

export async function fetchNotices(
  noticeType: string,
  query: NoticePageQuery
): Promise<CommonResponse<NoticeMsgResponseType>> {
  const response = await doGet<CommonResponse<NoticeMsgResponseType>>(
    `${UNREAD_NOTICE_URL}/${encodeURIComponent(noticeType)}`,
    {
      currentPage: query.currentPage,
      pageSize: query.pageSize
    }
  )
  return response.data
}
