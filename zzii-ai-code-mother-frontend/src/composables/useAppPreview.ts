import { nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import { getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export function useAppPreview(
  appId: () => string | number | undefined,
  codeGenType: () => string | undefined,
) {
  const previewUrl = ref('')
  const previewReady = ref(false)
  const isEditMode = ref(false)
  const selectedElementInfo = ref<ElementInfo | null>(null)
  let previewRefreshToken = 0

  const visualEditor = new VisualEditor({
    onElementSelected: (elementInfo: ElementInfo) => {
      selectedElementInfo.value = elementInfo
    },
  })

  const getPreviewBaseUrl = () => {
    const id = appId()
    if (!id) return ''
    const type = codeGenType() || CodeGenTypeEnum.HTML
    return getStaticPreviewUrl(type, String(id))
  }

  /** 强制刷新预览 iframe（带缓存破坏参数） */
  const updatePreview = async () => {
    const baseUrl = getPreviewBaseUrl()
    if (!baseUrl) return

    // 先清空再赋值，确保即使路径不变也会触发 iframe 重新加载
    previewUrl.value = ''
    previewReady.value = false
    await nextTick()
    previewUrl.value = `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}t=${Date.now()}`
  }

  const fetchResourceLastModified = async (url: string): Promise<string | null> => {
    try {
      const response = await fetch(url, {
        method: 'HEAD',
        cache: 'no-store',
        credentials: 'include',
      })
      if (!response.ok) return null
      return response.headers.get('Last-Modified')
    } catch {
      return null
    }
  }

  /**
   * 生成/修改完成后刷新预览。
   * 后端保存已改为异步执行，需等待静态资源更新后再刷新 iframe。
   */
  const refreshPreviewAfterGeneration = async () => {
    const baseUrl = getPreviewBaseUrl()
    if (!baseUrl) return

    const token = ++previewRefreshToken
    const previousModified = await fetchResourceLastModified(baseUrl)
    const type = codeGenType() || CodeGenTypeEnum.HTML
    const maxWaitMs = type === CodeGenTypeEnum.VUE_PROJECT ? 120000 : 90000
    const intervalMs = type === CodeGenTypeEnum.VUE_PROJECT ? 2000 : 1000
    const startedAt = Date.now()

    while (Date.now() - startedAt < maxWaitMs) {
      if (token !== previewRefreshToken) return
      await sleep(intervalMs)
      if (token !== previewRefreshToken) return

      const currentModified = await fetchResourceLastModified(baseUrl)
      if (currentModified && currentModified !== previousModified) {
        await updatePreview()
        return
      }
      if (!previousModified && currentModified) {
        await updatePreview()
        return
      }
    }

    if (token === previewRefreshToken) {
      await updatePreview()
    }
  }

  const onIframeLoad = () => {
    previewReady.value = true
    const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
    if (iframe) {
      visualEditor.init(iframe)
      visualEditor.onIframeLoad()
    }
  }

  const toggleEditMode = () => {
    const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
    if (!iframe || !previewReady.value) {
      message.warning('请等待页面加载完成')
      return
    }
    isEditMode.value = visualEditor.toggleEditMode()
  }

  const clearSelectedElement = () => {
    selectedElementInfo.value = null
    visualEditor.clearSelection()
  }

  const getInputPlaceholder = () => {
    if (selectedElementInfo.value) {
      return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
    }
    return '请描述你想生成的网站，越详细效果越好哦'
  }

  const handleIframeMessage = (event: MessageEvent) => {
    visualEditor.handleIframeMessage(event)
  }

  return {
    previewUrl,
    previewReady,
    isEditMode,
    selectedElementInfo,
    updatePreview,
    refreshPreviewAfterGeneration,
    onIframeLoad,
    toggleEditMode,
    clearSelectedElement,
    getInputPlaceholder,
    handleIframeMessage,
  }
}
