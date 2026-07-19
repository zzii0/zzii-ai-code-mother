<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <ConfigProvider :locale="zhCN">
    <BasicLayout />
  </ConfigProvider>
</template>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell',
    'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow-x: hidden;
  background: var(--color-bg-muted);
}

html {
  overflow-x: hidden;
}

a {
  color: var(--color-primary);
}

a:hover {
  color: #40a9ff;
}
</style>
