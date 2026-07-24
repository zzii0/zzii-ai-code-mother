<template>
  <div id="appChatPage">
    <ChatHeaderBar
      :app-name="appInfo?.appName || '网站生成器'"
      :code-gen-type="appInfo?.codeGenType"
      :is-owner="isOwner"
      :downloading="downloading"
      :deploying="deploying"
      @show-detail="showAppDetail"
      @download="downloadCode"
      @deploy="deployApp"
    />

    <div class="main-content">
      <div class="chat-section">
        <ChatMessageList
          ref="messageListRef"
          :messages="messages"
          :user-avatar="loginUserStore.loginUser.userAvatar"
          :ai-avatar="aiAvatar"
          :has-more-history="hasMoreHistory"
          :loading-history="loadingHistory"
          @load-more="loadMoreHistory"
        />

        <ChatInput
          v-model:model-value="userInput"
          :placeholder="getInputPlaceholder()"
          :is-owner="isOwner"
          :is-generating="isGenerating"
          :selected-element-info="selectedElementInfo"
          @send="sendMessage"
          @clear-selection="clearSelectedElement"
        />
      </div>

      <PreviewPanel
        :preview-url="previewUrl"
        :is-generating="isGenerating"
        :is-owner="isOwner"
        :is-edit-mode="isEditMode"
        @toggle-edit-mode="toggleEditMode"
        @open-in-new-tab="openInNewTab(previewUrl)"
        @iframe-load="onIframeLoad"
      />
    </div>

    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppVoById, deleteApp as deleteAppApi } from '@/api/appController'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { useChatHistory } from '@/composables/useChatHistory'
import { useChatStream } from '@/composables/useChatStream'
import { useAppPreview } from '@/composables/useAppPreview'
import { useAppDeploy } from '@/composables/useAppDeploy'
import ChatHeaderBar from '@/components/chat/ChatHeaderBar.vue'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import PreviewPanel from '@/components/chat/PreviewPanel.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appInfo = ref<API.AppVO>()
const appId = ref<string>()
const appDetailVisible = ref(false)
const messageListRef = ref<InstanceType<typeof ChatMessageList>>()

const isOwner = computed(() => appInfo.value?.userId === loginUserStore.loginUser.id)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const {
  messages,
  loadingHistory,
  hasMoreHistory,
  historyLoaded,
  loadChatHistory,
  loadMoreHistory,
} = useChatHistory(() => appId.value)

const {
  previewUrl,
  isEditMode,
  selectedElementInfo,
  updatePreview,
  onIframeLoad,
  toggleEditMode,
  clearSelectedElement,
  getInputPlaceholder,
  handleIframeMessage,
} = useAppPreview(
  () => appId.value,
  () => appInfo.value?.codeGenType,
)

const scrollToBottom = () => {
  messageListRef.value?.scrollToBottom()
}

const fetchAppInfo = async () => {
  if (!appId.value) return
  try {
    const res = await getAppVoById({ id: appId.value as unknown as number })
    if (isApiSuccess(res) && res.data.data) {
      appInfo.value = res.data.data
      await loadChatHistory()
      if (messages.value.length >= 2) {
        updatePreview()
      }
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

const onStreamComplete = async () => {
  updatePreview()
}

const { userInput, isGenerating, sendMessage, sendInitialMessage } = useChatStream({
  appId: () => appId.value,
  messages,
  scrollToBottom,
  onStreamComplete,
  getSelectedElement: () => selectedElementInfo.value,
  clearSelectedElement,
  exitEditModeIfNeeded: () => {
    if (isEditMode.value) {
      toggleEditMode()
    }
  },
})

const {
  deploying,
  deployModalVisible,
  deployUrl,
  downloading,
  downloadCode,
  deployApp,
  openInNewTab,
  openDeployedSite,
} = useAppDeploy(() => appId.value)

const showAppDetail = () => {
  appDetailVisible.value = true
}

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

const deleteApp = async () => {
  if (!appInfo.value?.id) return
  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (isApiSuccess(res)) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + getApiErrorMessage(res))
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

onMounted(async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }
  appId.value = id
  await fetchAppInfo()
  window.addEventListener('message', handleIframeMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', handleIframeMessage)
})
</script>

<style scoped>
#appChatPage {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: var(--color-bg-muted);
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

@media (max-width: 992px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  :deep(.preview-section) {
    flex: none;
    height: 50vh;
  }
}
</style>
