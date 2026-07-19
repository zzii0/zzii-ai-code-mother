import type { AxiosResponse } from 'axios'

type ApiResponse<T = unknown> = {
  code?: number
  data?: T
  message?: string
}

export function isApiSuccess<T>(res: AxiosResponse<ApiResponse<T>>): boolean {
  return res.data.code === 0
}

export function getApiData<T>(res: AxiosResponse<ApiResponse<T>>): T | undefined {
  return isApiSuccess(res) ? res.data.data : undefined
}

export function getApiErrorMessage(res: AxiosResponse<ApiResponse<unknown>>): string {
  return res.data.message || '请求失败'
}
