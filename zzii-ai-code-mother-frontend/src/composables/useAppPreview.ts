import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import { getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

export function useAppPreview(
  appId: () => string | number | undefined,
  codeGenType: () => string | undefined,
) {
  const previewUrl = ref('')
  const previewReady = ref(false)
  const isEditMode = ref(false)
  const selectedElementInfo = ref<ElementInfo | null>(null)

  const visualEditor = new VisualEditor({
    onElementSelected: (elementInfo: ElementInfo) => {
      selectedElementInfo.value = elementInfo
    },
  })

  const updatePreview = () => {
    const id = appId()
    if (id) {
      const type = codeGenType() || CodeGenTypeEnum.HTML
      previewUrl.value = getStaticPreviewUrl(type, String(id))
      previewReady.value = true
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
    onIframeLoad,
    toggleEditMode,
    clearSelectedElement,
    getInputPlaceholder,
    handleIframeMessage,
  }
}
