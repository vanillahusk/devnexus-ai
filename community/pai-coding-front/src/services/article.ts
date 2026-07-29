import type { ArticleType } from '@/http/ResponseTypes/ArticleType/ArticleType'
import type { ArticleCategoryType } from '@/http/ResponseTypes/CategoryType/ArticleCategoryType'
import type { CommonResponse } from '@/http/ResponseTypes/CommonResponseType'
import type { ArticleDetailResponse } from '@/http/ResponseTypes/ArticleDetailResponseType'
import type { BasicPageType } from '@/http/ResponseTypes/PageType/BasicPageType'
import { doGet } from '@/http/BackendRequests'
import { ARTICLE_DETAIL_URL, CATEGORY_ARTICLE_LIST_URL } from '@/http/URL'

export interface HomeArticleResult {
  articles: BasicPageType<ArticleType>
  categories: ArticleCategoryType[]
  topArticles: ArticleType[]
}

export interface ArticlePageQuery {
  category?: string | null
  currentPage?: number
  pageSize?: number
}

export async function fetchHomeArticles(
  query: ArticlePageQuery
): Promise<CommonResponse<HomeArticleResult>> {
  const response = await doGet<CommonResponse<HomeArticleResult>>(
    CATEGORY_ARTICLE_LIST_URL,
    {
      category: query.category,
      currentPage: query.currentPage,
      pageSize: query.pageSize
    }
  )
  return response.data
}

export async function fetchArticleDetail(
  articleId: string
): Promise<CommonResponse<ArticleDetailResponse>> {
  const response = await doGet<CommonResponse<ArticleDetailResponse>>(
    `${ARTICLE_DETAIL_URL}/${encodeURIComponent(articleId)}`,
    {}
  )
  return response.data
}
