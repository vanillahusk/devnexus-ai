import type { ArticleDetailResponse } from '@/http/ResponseTypes/ArticleDetailResponseType'
import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import { doGet, doPost } from '@/http/BackendRequests'
import { COMMENT_LIKE_URL, COMMENT_SUBMIT_URL } from '@/http/URL'

export interface SubmitCommentRequest {
  articleId: string
  commentContent: string
  parentCommentId?: number
  topCommentId?: number
}

export async function submitComment(
  request: SubmitCommentRequest
): Promise<CommonResponse<ArticleDetailResponse>> {
  const response = await doPost<CommonResponse<ArticleDetailResponse>>(
    COMMENT_SUBMIT_URL,
    request
  )
  return response.data
}

export async function toggleCommentLike(
  commentId: string,
  type: number
): Promise<CommonResponse> {
  const response = await doGet<CommonResponse>(COMMENT_LIKE_URL, {
    commentId,
    type
  })
  return response.data
}
