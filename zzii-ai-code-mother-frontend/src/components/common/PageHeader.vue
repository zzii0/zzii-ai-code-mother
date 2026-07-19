<template>
  <div class="page-header">
    <div class="page-header-main">
      <a-button v-if="showBack" type="text" class="back-btn" @click="handleBack">
        <template #icon>
          <ArrowLeftOutlined />
        </template>
      </a-button>
      <div>
        <h1 class="page-header-title">{{ title }}</h1>
        <p v-if="description" class="page-header-desc">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.extra" class="page-header-extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    showBack?: boolean
    backTo?: string
  }>(),
  {
    showBack: false,
  },
)

const router = useRouter()

const handleBack = () => {
  if (props.backTo) {
    router.push(props.backTo)
    return
  }
  router.back()
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.back-btn {
  margin-top: 2px;
  color: var(--color-text-secondary);
}

.page-header-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
}

.page-header-desc {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
}
</style>
