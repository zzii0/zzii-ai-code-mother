import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import ACCESS_ENUM from '@/access/accessEnum'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/pages/HomePage.vue'),
    meta: {
      title: '主页',
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: '/user/login',
    name: 'userLogin',
    component: () => import('@/pages/user/UserLoginPage.vue'),
    meta: {
      title: '用户登录',
      hideInMenu: true,
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: '/user/register',
    name: 'userRegister',
    component: () => import('@/pages/user/UserRegisterPage.vue'),
    meta: {
      title: '用户注册',
      hideInMenu: true,
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: '/user/profile',
    name: 'userProfile',
    component: () => import('@/pages/user/UserProfilePage.vue'),
    meta: {
      title: '个人中心',
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: '/admin/userManage',
    name: 'userManage',
    component: () => import('@/pages/admin/UserManagePage.vue'),
    meta: {
      title: '用户管理',
      access: ACCESS_ENUM.ADMIN,
    },
  },
  {
    path: '/admin/appManage',
    name: 'appManage',
    component: () => import('@/pages/admin/AppManagePage.vue'),
    meta: {
      title: '应用管理',
      access: ACCESS_ENUM.ADMIN,
    },
  },
  {
    path: '/admin/chatManage',
    name: 'chatManage',
    component: () => import('@/pages/admin/ChatManagePage.vue'),
    meta: {
      title: '对话管理',
      access: ACCESS_ENUM.ADMIN,
    },
  },
  {
    path: '/app/chat/:id',
    name: 'appChat',
    component: () => import('@/pages/app/AppChatPage.vue'),
    meta: {
      title: '应用对话',
      hideInMenu: true,
      fullBleed: true,
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: '/app/edit/:id',
    name: 'appEdit',
    component: () => import('@/pages/app/AppEditPage.vue'),
    meta: {
      title: '编辑应用',
      hideInMenu: true,
      fullBleed: true,
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: '/noAuth',
    name: 'noAuth',
    component: () => import('@/pages/NoAuthPage.vue'),
    meta: {
      title: '无权限',
      hideInMenu: true,
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

export default router
