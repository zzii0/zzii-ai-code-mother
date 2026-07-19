import axios from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE_URL } from '@/config/env'

const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

myAxios.interceptors.request.use(
  (config) => {
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => Promise.reject(error),
)

myAxios.interceptors.response.use(
  (response) => {
    const { data } = response
    if (data.code === 40100) {
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.href = `/user/login?redirect=${encodeURIComponent(redirect)}`
      }
    }
    return response
  },
  (error) => {
    if (error.response) {
      message.error(`请求失败：${error.response.status}`)
    } else if (error.request) {
      message.error('网络异常，请检查网络连接')
    } else {
      message.error('请求失败，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default myAxios
