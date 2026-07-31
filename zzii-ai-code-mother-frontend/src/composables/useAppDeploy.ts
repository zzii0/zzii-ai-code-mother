import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { deployApp as deployAppApi } from '@/api/appController'
import request from '@/request'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'

export function useAppDeploy(
  appId: () => string | number | undefined,
  canDownload: () => boolean = () => true,
) {
  const deploying = ref(false)
  const deployModalVisible = ref(false)
  const deployUrl = ref('')
  const downloading = ref(false)

  const downloadCode = async () => {
    const id = appId()
    if (!id) {
      message.error('应用ID不存在')
      return
    }
    if (!canDownload()) {
      message.warning('请先部署应用后再下载代码')
      return
    }

    downloading.value = true
    try {
      const baseURL = request.defaults.baseURL || ''
      const url = `${baseURL}/app/download/${id}`
      const response = await fetch(url, {
        method: 'GET',
        credentials: 'include',
      })
      if (!response.ok) {
        const contentType = response.headers.get('Content-Type') || ''
        if (contentType.includes('application/json')) {
          const data = await response.json()
          throw new Error(data.message || '下载失败')
        }
        throw new Error(`下载失败: ${response.status}`)
      }

      const contentDisposition = response.headers.get('Content-Disposition')
      const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${id}.zip`
      const blob = await response.blob()
      const downloadUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = fileName
      link.click()
      URL.revokeObjectURL(downloadUrl)
      message.success('代码下载成功')
    } catch (error) {
      console.error('下载失败：', error)
      const errorMessage = error instanceof Error ? error.message : '下载失败，请重试'
      message.error(errorMessage)
    } finally {
      downloading.value = false
    }
  }

  const deployApp = async (): Promise<boolean> => {
    const id = appId()
    if (!id) {
      message.error('应用ID不存在')
      return false
    }

    deploying.value = true
    try {
      const res = await deployAppApi({ appId: id as number })
      if (isApiSuccess(res) && res.data.data) {
        deployUrl.value = res.data.data
        deployModalVisible.value = true
        message.success('部署成功')
        return true
      }
      message.error('部署失败：' + getApiErrorMessage(res))
      return false
    } catch (error) {
      console.error('部署失败：', error)
      message.error('部署失败，请重试')
      return false
    } finally {
      deploying.value = false
    }
  }

  const openInNewTab = (previewUrl: string) => {
    if (previewUrl) {
      window.open(previewUrl, '_blank')
    }
  }

  const openDeployedSite = () => {
    if (deployUrl.value) {
      window.open(deployUrl.value, '_blank')
    }
  }

  return {
    deploying,
    deployModalVisible,
    deployUrl,
    downloading,
    downloadCode,
    deployApp,
    openInNewTab,
    openDeployedSite,
  }
}
