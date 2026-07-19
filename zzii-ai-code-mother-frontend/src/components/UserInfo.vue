<template>
  <div class="user-info">
    <a-avatar :src="avatarUrl" :size="size">
      {{ user?.userName?.charAt(0) || 'U' }}
    </a-avatar>
    <span v-if="showName" class="user-name">{{ user?.userName || '未知用户' }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getUserAvatarUrl } from '@/config/env'

interface Props {
  user?: API.UserVO
  size?: number | 'small' | 'default' | 'large'
  showName?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  showName: true,
})

const avatarUrl = computed(() => getUserAvatarUrl(props.user?.userAvatar))
</script>

<style scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: #1a1a1a;
}
</style>
