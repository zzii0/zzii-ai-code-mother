<template>
  <div class="no-auth-page">
    <a-result status="403" title="403" sub-title="你没有权限访问当前页面">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="router.push('/')">返回首页</a-button>
          <a-button @click="router.push('/user/login')">去登录</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.no-auth-page {
  min-height: calc(100vh - 200px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
</style>
