<template>
  <div class="admin-page-layout">
    <div class="admin-page-header">
      <h2 class="admin-page-title">{{ title }}</h2>
      <p v-if="description" class="admin-page-description">{{ description }}</p>
    </div>
    <div class="admin-page-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.admin-page-layout {
  padding: 24px;
  background: white;
  margin: 16px auto;
  max-width: var(--page-max-width, 1200px);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
}

.admin-page-header {
  margin-bottom: 24px;
}

.admin-page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
}

.admin-page-description {
  margin: 0;
  color: #666;
}

.admin-page-content :deep(.ant-form-inline) {
  row-gap: 12px;
}
</style>
