/**
 * 环境变量配置
 */
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

/** 解析用户头像完整 URL */
export const getUserAvatarUrl = (avatar?: string) => {
  if (!avatar) return undefined
  if (avatar.startsWith('http://') || avatar.startsWith('https://') || avatar.startsWith('data:')) {
    return avatar
  }
  return `${API_BASE_URL}${avatar.startsWith('/') ? avatar : `/${avatar}`}`
}

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
