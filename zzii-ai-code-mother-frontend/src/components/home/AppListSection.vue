<template>
  <div class="section">
    <h2 ref="titleRef" class="section-title">{{ title }}</h2>
    <div v-if="loading" class="app-grid-skeleton">
      <a-card v-for="i in 3" :key="i" class="skeleton-card">
        <a-skeleton active :paragraph="{ rows: 3 }" />
      </a-card>
    </div>
    <a-empty v-else-if="apps.length === 0" class="empty-state" :description="emptyDescription" />
    <div v-else :class="gridClass">
      <AppCard
        v-for="app in apps"
        :key="app.id"
        :app="app"
        :featured="featured"
        @view-chat="$emit('viewChat', $event)"
        @view-work="$emit('viewWork', $event)"
      />
    </div>
    <div v-if="total > 0" class="pagination-wrapper">
      <a-pagination
        :current="page.current"
        :page-size="page.pageSize"
        :total="total"
        :show-size-changer="false"
        :show-total="(count: number) => `共 ${count} ${unit}`"
        @change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AppCard from '@/components/AppCard.vue'

const titleRef = ref<HTMLElement>()

defineExpose({
  titleRef,
})

const props = withDefaults(
  defineProps<{
    title: string
    apps: API.AppVO[]
    page: { current: number; pageSize: number; total: number }
    loading?: boolean
    featured?: boolean
    emptyDescription?: string
    unit?: string
    gridClass?: string
  }>(),
  {
    loading: false,
    featured: false,
    emptyDescription: '暂无数据',
    unit: '个应用',
    gridClass: 'app-grid',
  },
)

const emit = defineEmits<{
  viewChat: [app: API.AppVO]
  viewWork: [app: API.AppVO]
  pageChange: [page: number]
}>()

const total = computed(() => props.page.total)

const handlePageChange = (page: number) => {
  emit('pageChange', page)
}
</script>

<style scoped>
.section {
  margin-bottom: 60px;
}

.section-title {
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 32px;
  color: #1e293b;
}

.app-grid,
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.app-grid-skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.skeleton-card {
  border-radius: 16px;
  overflow: hidden;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

@media (max-width: 768px) {
  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }
}
</style>
