<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { useAppList } from '@/composables/useAppList'
import PromptCreateSection from '@/components/home/PromptCreateSection.vue'
import AppListSection from '@/components/home/AppListSection.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const creating = ref(false)

const {
  myApps,
  myAppsLoading,
  myAppsPage,
  featuredApps,
  featuredAppsLoading,
  featuredAppsPage,
  loadMyApps,
  loadFeaturedApps,
} = useAppList()

const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({ initPrompt: userPrompt.value.trim() })
    if (isApiSuccess(res) && res.data.data) {
      message.success('应用创建成功')
      await router.push(`/app/chat/${String(res.data.data)}`)
    } else {
      message.error('创建失败：' + getApiErrorMessage(res))
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    window.open(getDeployUrl(app.deployKey), '_blank')
  }
}

const handleMyAppsPageChange = async (page: number) => {
  myAppsPage.current = page
  await loadMyApps(loginUserStore.loginUser.id)
}

const handleFeaturedAppsPageChange = async (page: number) => {
  featuredAppsPage.current = page
  await loadFeaturedApps()
}

const handleMouseMove = (e: MouseEvent) => {
  const { clientX, clientY } = e
  const { innerWidth, innerHeight } = window
  document.documentElement.style.setProperty('--mouse-x', `${(clientX / innerWidth) * 100}%`)
  document.documentElement.style.setProperty('--mouse-y', `${(clientY / innerHeight) * 100}%`)
}

onMounted(async () => {
  await loadMyApps(loginUserStore.loginUser.id)
  await loadFeaturedApps()
  document.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
})
</script>

<template>
  <div id="homePage">
    <div class="container page-container">
      <PromptCreateSection v-model="userPrompt" :creating="creating" @create="createApp" />

      <AppListSection
        title="我的作品"
        :apps="myApps"
        :page="myAppsPage"
        :loading="myAppsLoading"
        empty-description="登录后创建你的第一个 AI 应用"
        @view-chat="viewChat"
        @view-work="viewWork"
        @page-change="handleMyAppsPageChange"
      />

      <AppListSection
        title="精选案例"
        :apps="featuredApps"
        :page="featuredAppsPage"
        :loading="featuredAppsLoading"
        :featured="true"
        unit="个案例"
        grid-class="featured-grid"
        empty-description="暂无精选案例"
        @view-chat="viewChat"
        @view-work="viewWork"
        @page-change="handleFeaturedAppsPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 8%, #e2e8f0 20%, #cbd5e1 100%),
    radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 50%);
  position: relative;
  overflow: hidden;
}

#homePage::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.05) 1px, transparent 1px);
  background-size: 100px 100px;
  pointer-events: none;
}

#homePage::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
    600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
    rgba(59, 130, 246, 0.08) 0%,
    transparent 80%
  );
  pointer-events: none;
}

.container {
  position: relative;
  z-index: 2;
}
</style>
