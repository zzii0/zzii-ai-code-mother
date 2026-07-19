<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle">
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">{{ SITE_NAME }}</h1>
          </div>
        </RouterLink>
      </a-col>

      <a-col flex="auto" class="desktop-menu">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>

      <a-col class="mobile-menu-trigger">
        <a-button type="text" @click="drawerVisible = true">
          <MenuOutlined />
        </a-button>
      </a-col>

      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="getUserAvatarUrl(loginUserStore.loginUser.userAvatar)" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="router.push('/user/profile')">个人中心</a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" @click="router.push('/user/login')">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-drawer v-model:open="drawerVisible" title="导航菜单" placement="left" :width="280">
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        :items="menuItems"
        @click="handleDrawerMenuClick"
      />
    </a-drawer>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { LogoutOutlined, MenuOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'
import ACCESS_ENUM from '@/access/accessEnum'
import checkAccess from '@/access/checkAccess'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { SITE_NAME } from '@/config/site'
import { getUserAvatarUrl } from '@/config/env'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

const selectedKeys = ref<string[]>([route.path])
const drawerVisible = ref(false)

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

const menuItems = computed<MenuProps['items']>(() => {
  const menuRoutes = router
    .getRoutes()
    .filter((item) => item.meta?.title && !item.meta?.hideInMenu)
    .sort((a, b) => {
      const order = ['/', '/user/profile', '/admin/userManage', '/admin/appManage', '/admin/chatManage']
      return order.indexOf(a.path) - order.indexOf(b.path)
    })

  return menuRoutes
    .filter((item) => {
      const needAccess = (item.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN
      return checkAccess(loginUserStore.loginUser, needAccess)
    })
    .map((item) => ({
      key: item.path,
      label: item.meta?.title as string,
      title: item.meta?.title as string,
    }))
})

const navigateTo = (key: string) => {
  selectedKeys.value = [key]
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const handleMenuClick: MenuProps['onClick'] = (e) => {
  navigateTo(e.key as string)
}

const handleDrawerMenuClick: MenuProps['onClick'] = (e) => {
  navigateTo(e.key as string)
  drawerVisible.value = false
}

const doLogout = async () => {
  const res = await userLogout()
  if (isApiSuccess(res)) {
    loginUserStore.resetLoginUser()
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + getApiErrorMessage(res))
  }
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}

.mobile-menu-trigger {
  display: none;
}

@media (max-width: 992px) {
  .desktop-menu {
    display: none;
  }

  .mobile-menu-trigger {
    display: block;
  }
}
</style>
