import type { AxiosResponse, ResponseType } from 'axios'
import { MOCK_LOGIN_URL } from '@/http/URL'
import {
  externalHttpClient,
  httpClient
} from '@/services/http/client'
import type { RequestParams } from '@/services/http/types'

type RequestData = unknown

export function doGet<T>(
  url: string,
  params: RequestParams = {},
  type: Extract<ResponseType, 'json' | 'text'> = 'json'
): Promise<AxiosResponse<T>> {
  return httpClient.get<T>(url, {
    params,
    responseType: type
  })
}

export function doPost<T>(
  url: string,
  data: RequestData
): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(url, data)
}

export function doFilePost<T>(
  url: string,
  data: FormData
): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(url, data, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function doLoginPost<T>(
  url: string,
  data: RequestData
): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(url, data)
}

export function doPut<T>(
  url: string,
  data: RequestData
): Promise<AxiosResponse<T>> {
  return httpClient.put<T>(url, data)
}

export function doDelete<T>(
  url: string,
  params: RequestParams = {}
): Promise<AxiosResponse<T>> {
  return httpClient.delete<T>(url, { params })
}

// 用于调用社区主站之外的独立工具服务。
export function extraFilePostAndDownload(
  baseUrl: string,
  url: string,
  data: FormData,
  params: RequestParams = {}
): Promise<AxiosResponse<Blob>> {
  if (!baseUrl) {
    return Promise.reject(new Error('未配置 VITE_EXCEL_SERVICE_BASE_URL'))
  }

  return externalHttpClient.post<Blob>(url, data, {
    baseURL: baseUrl,
    params,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    responseType: 'blob'
  })
}

function mockLoginXml(code: string, fromUserName: string): string {
  return `<xml><URL><![CDATA[https://hhui.top]]></URL><ToUserName><![CDATA[一灰灰blog]]></ToUserName><FromUserName><![CDATA[${fromUserName}]]></FromUserName><CreateTime>1655700579</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[${code}]]></Content><MsgId>11111111</MsgId></xml>`
}

export function mockLoginXML<T>(code: string): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(
    MOCK_LOGIN_URL,
    mockLoginXml(code, 'demoUser1234'),
    {
      headers: {
        'Content-Type': 'application/xml'
      }
    }
  )
}

export function mockLogin2XML<T>(code: string): Promise<AxiosResponse<T>> {
  const randomUser = `demoUser_${Math.round(Math.random() * 100)}`
  return httpClient.post<T>(
    MOCK_LOGIN_URL,
    mockLoginXml(code, randomUser),
    {
      headers: {
        'Content-Type': 'application/xml'
      }
    }
  )
}
