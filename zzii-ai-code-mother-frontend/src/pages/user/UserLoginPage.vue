<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="auth-title">{{ SITE_NAME }} - 用户登录</h2>
      <p class="auth-desc">{{ SITE_TAGLINE }}</p>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码长度不能小于 6 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
        </a-form-item>
        <div class="auth-tips">
          没有账号
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>
        <a-form-item>
          <a-button type="primary" html-type="submit" style="width: 100%" :loading="submitting">
            登录
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { SITE_NAME, SITE_TAGLINE } from '@/config/site'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)

const handleSubmit = async (values: API.UserLoginRequest) => {
  submitting.value = true
  try {
    const res = await userLogin(values)
    if (isApiSuccess(res) && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      router.push({ path: redirect, replace: true })
    } else {
      message.error('登录失败，' + getApiErrorMessage(res))
    }
  } finally {
    submitting.value = false
  }
}
</script>
