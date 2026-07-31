<template>
  <div class="header-bar">
    <div class="header-left">
      <a-button type="text" class="back-btn" @click="router.push('/')">
        <template #icon>
          <ArrowLeftOutlined />
        </template>
      </a-button>
      <h1 class="app-name">{{ appName }}</h1>
      <a-tag v-if="codeGenType" color="blue" class="code-gen-type-tag">
        {{ formatCodeGenType(codeGenType) }}
      </a-tag>
    </div>
    <div class="header-right">
      <a-button type="default" @click="$emit('showDetail')">
        <template #icon>
          <InfoCircleOutlined />
        </template>
        应用详情
      </a-button>
      <a-tooltip :title="downloadTooltip">
        <a-button
          type="primary"
          ghost
          :loading="downloading"
          :disabled="downloadDisabled"
          @click="$emit('download')"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
      </a-tooltip>
      <a-button type="primary" :loading="deploying" @click="$emit('deploy')">
        <template #icon>
          <CloudUploadOutlined />
        </template>
        部署
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeftOutlined, CloudUploadOutlined, DownloadOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import { formatCodeGenType } from '@/utils/codeGenTypes'

const router = useRouter()

const props = defineProps<{
  appName?: string
  codeGenType?: string
  isOwner: boolean
  canDownload: boolean
  downloading: boolean
  deploying: boolean
}>()

defineEmits<{
  showDetail: []
  download: []
  deploy: []
}>()

const downloadDisabled = computed(() => !props.isOwner || !props.canDownload)

const downloadTooltip = computed(() => {
  if (!props.isOwner) {
    return '仅创建者可下载代码'
  }
  if (!props.canDownload) {
    return '请先部署应用后再下载代码'
  }
  return '下载项目代码'
})
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.back-btn {
  color: var(--color-text-secondary);
  margin-right: 4px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .app-name {
    font-size: 16px;
  }
}
</style>
