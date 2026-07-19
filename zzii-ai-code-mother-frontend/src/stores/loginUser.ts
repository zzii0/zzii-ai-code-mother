import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController'
import ACCESS_ENUM from '@/access/accessEnum'
import { isApiSuccess } from '@/utils/apiHelper'

const defaultLoginUser = (): API.LoginUserVO => ({
  userName: '未登录',
  userRole: ACCESS_ENUM.NOT_LOGIN,
})

/**
 * 登录用户信息
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>(defaultLoginUser())

  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      if (isApiSuccess(res) && res.data.data) {
        loginUser.value = res.data.data
        return
      }
    } catch {
      // ignore
    }
    loginUser.value = defaultLoginUser()
  }

  function setLoginUser(newLoginUser: Partial<API.LoginUserVO>) {
    loginUser.value = {
      ...defaultLoginUser(),
      ...newLoginUser,
    }
  }

  function resetLoginUser() {
    loginUser.value = defaultLoginUser()
  }

  return { loginUser, fetchLoginUser, setLoginUser, resetLoginUser }
})
