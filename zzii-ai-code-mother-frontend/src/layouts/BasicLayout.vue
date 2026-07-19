<template>
  <a-layout class="basic-layout">
    <GlobalHeader />
    <div v-if="routeLoading" class="route-progress" />
    <a-layout-content :class="['main-content', { 'main-content--full-bleed': isFullBleed }]">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </a-layout-content>
    <GlobalFooter v-if="!isFullBleed" />
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
const router = useRouter()
const routeLoading = ref(false)

const isFullBleed = computed(() => Boolean(route.meta.fullBleed))

router.beforeEach(() => {
  routeLoading.value = true
})

router.afterEach(() => {
  routeLoading.value = false
})
</script>

<style scoped>
.basic-layout {
  background: none;
  min-height: 100vh;
}

.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 1000;
  background: linear-gradient(90deg, var(--color-primary), #722ed1);
  animation: route-progress 0.8s ease-in-out infinite;
}

@keyframes route-progress {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.main-content {
  width: 100%;
  padding: 16px 24px 24px;
  background: none;
  margin: 0;
}

.main-content--full-bleed {
  padding: 0;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
