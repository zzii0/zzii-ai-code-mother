<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, type CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ExclamationCircleFilled } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import ACCESS_ENUM from '@/access/accessEnum'
import { useAppList } from '@/composables/useAppList'
import PromptCreateSection from '@/components/home/PromptCreateSection.vue'
import AppListSection from '@/components/home/AppListSection.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const creating = ref(false)
const authTipVisible = ref(false)
const authTipStyle = ref<CSSProperties>({})
const featuredSectionRef = ref<InstanceType<typeof AppListSection>>()
let authTipTimer: ReturnType<typeof setTimeout> | null = null

const updateAuthTipPosition = () => {
  const titleEl = featuredSectionRef.value?.titleRef
  if (!titleEl) {
    authTipStyle.value = {
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
    }
    return
  }
  const rect = titleEl.getBoundingClientRect()
  authTipStyle.value = {
    top: `${rect.top + rect.height / 2}px`,
    left: '50%',
    transform: 'translate(-50%, -50%)',
  }
}

const showAuthTip = async () => {
  updateAuthTipPosition()
  authTipVisible.value = true
  await nextTick()
  updateAuthTipPosition()
  if (authTipTimer) {
    clearTimeout(authTipTimer)
  }
  authTipTimer = setTimeout(() => {
    authTipVisible.value = false
    authTipTimer = null
  }, 3000)
}

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

const viewChat = (app: API.AppVO) => {
  if (!app?.id) {
    return
  }
  const loginUser = loginUserStore.loginUser
  if (!loginUser.id) {
    message.warning('请先登录')
    return
  }
  const isAdmin = loginUser.userRole === ACCESS_ENUM.ADMIN
  const isCreator = app.userId != null && app.userId === loginUser.id
  if (!isAdmin && !isCreator) {
    showAuthTip()
    return
  }
  router.push(`/app/chat/${app.id}?view=1`)
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
  window.addEventListener('resize', updateAuthTipPosition)
  window.addEventListener('scroll', updateAuthTipPosition, true)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('resize', updateAuthTipPosition)
  window.removeEventListener('scroll', updateAuthTipPosition, true)
  if (authTipTimer) {
    clearTimeout(authTipTimer)
  }
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
        ref="featuredSectionRef"
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

    <Teleport to="body">
      <Transition name="home-auth-tip">
        <div
          v-if="authTipVisible"
          class="home-auth-tip-overlay"
          :style="authTipStyle"
          role="alert"
          @click="authTipVisible = false"
        >
          <div class="home-auth-tip-box" @click.stop>
            <ExclamationCircleFilled class="home-auth-tip-icon" />
            <span>无权查看该应用的对话历史</span>
          </div>
        </div>
      </Transition>
    </Teleport>
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

.home-auth-tip-overlay {
  position: fixed;
  z-index: 2000;
  pointer-events: none;
}

.home-auth-tip-box {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: min(calc(100vw - 48px), 420px);
  padding: 14px 22px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fff7e6 0%, #fffbe6 100%);
  border: 1px solid #ffe58f;
  box-shadow: 0 12px 32px rgba(250, 173, 20, 0.22);
  color: #874d00;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  pointer-events: auto;
  white-space: nowrap;
}

.home-auth-tip-icon {
  color: #faad14;
  font-size: 18px;
  flex-shrink: 0;
}

.home-auth-tip-enter-active,
.home-auth-tip-leave-active {
  transition: opacity 0.25s ease;
}

.home-auth-tip-enter-from,
.home-auth-tip-leave-to {
  opacity: 0;
}
</style>
