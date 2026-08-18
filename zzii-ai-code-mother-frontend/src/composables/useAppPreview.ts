import { nextTick, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { inlineEditApp } from '@/api/appController'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import { getStaticPreviewUrl } from '@/config/env'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import {
  VisualEditor,
  type ElementInfo,
  type InlineEditPayload,
} from '@/utils/visualEditor'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const supportsInlineEditByType = (codeGenType?: string) =>
  codeGenType === CodeGenTypeEnum.HTML || codeGenType === CodeGenTypeEnum.MULTI_FILE

export function useAppPreview(
  appId: () => string | number | undefined,
  codeGenType: () => string | undefined,
) {
  const previewUrl = ref('')
  const previewReady = ref(false)
  const isEditMode = ref(false)
  const selectedElementInfo = ref<ElementInfo | null>(null)
  const isInlineEditing = ref(false)
  let previewRefreshToken = 0
  let inlineEditQueue: Promise<void> = Promise.resolve()

  const visualEditor = new VisualEditor({
    onElementSelected: (elementInfo: ElementInfo) => {
      selectedElementInfo.value = elementInfo
    },
    onInlineEditCommit: (payload: InlineEditPayload) => {
      enqueueInlineEdit(payload)
    },
  })

  watch(
    () => codeGenType(),
    (type) => {
      visualEditor.setSupportsInlineEdit(supportsInlineEditByType(type))
    },
    { immediate: true },
  )

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

    previewUrl.value = ''
    previewReady.value = false
    await nextTick()
    previewUrl.value = `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}t=${Date.now()}`
  }

  const fetchResourceExists = async (url: string): Promise<boolean> => {
    try {
      const response = await fetch(url, {
        method: 'HEAD',
        cache: 'no-store',
        credentials: 'include',
      })
      return response.ok
    } catch {
      return false
    }
  }

  /**
   * 生成/修改完成后刷新预览。
   * save_success / build_success 时会提前调用；done 时再调用一次作为兜底。
   */
  const refreshPreviewAfterGeneration = async () => {
    const baseUrl = getPreviewBaseUrl()
    if (!baseUrl) return

    const token = ++previewRefreshToken
    await updatePreview()

    const maxAttempts = 15
    const intervalMs = 400
    for (let i = 0; i < maxAttempts; i++) {
      if (token !== previewRefreshToken) return
      const exists = await fetchResourceExists(baseUrl)
      if (exists) {
        if (token === previewRefreshToken) {
          await updatePreview()
        }
        return
      }
      await sleep(intervalMs)
    }
  }

  const enqueueInlineEdit = (payload: InlineEditPayload) => {
    inlineEditQueue = inlineEditQueue
      .then(() => persistInlineEdit(payload))
      .catch((error) => {
        console.error('行内编辑队列异常：', error)
      })
  }

  const persistInlineEdit = async (payload: InlineEditPayload) => {
    const id = appId()
    if (!id || !supportsInlineEditByType(codeGenType())) {
      return
    }

    isInlineEditing.value = true
    try {
      const res = await inlineEditApp({
        appId: String(id),
        file: payload.file || 'index.html',
        selector: payload.selector,
        oldContent: payload.oldContent,
        newContent: payload.newContent,
        innerHtml: payload.innerHtml,
      })

      if (isApiSuccess(res)) {
        message.success('文本已保存')
        selectedElementInfo.value = null
      } else {
        message.error(getApiErrorMessage(res))
      }
    } catch (error) {
      console.error('行内编辑失败：', error)
      message.error('行内编辑保存失败')
    } finally {
      isInlineEditing.value = false
    }
  }

  const onIframeLoad = () => {
    previewReady.value = true
    const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
    if (iframe) {
      visualEditor.init(iframe)
      visualEditor.setSupportsInlineEdit(supportsInlineEditByType(codeGenType()))
      visualEditor.onIframeLoad()
    }
  }

  const toggleEditMode = () => {
    const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
    if (!iframe || !previewReady.value) {
      message.warning('请等待页面加载完成')
      return
    }
    visualEditor.setSupportsInlineEdit(supportsInlineEditByType(codeGenType()))
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
    if (supportsInlineEditByType(codeGenType()) && isEditMode.value) {
      return '单击选中元素发给 AI 修改；双击文本可直接改字'
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
    isInlineEditing,
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
