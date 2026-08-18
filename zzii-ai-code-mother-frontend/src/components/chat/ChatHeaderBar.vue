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
      <a-tooltip v-if="isOwner" :title="exportTooltip">
        <span class="btn-tooltip-wrap">
          <a-dropdown :disabled="exporting || !canPreview">
            <a-button type="default" :loading="exporting" :disabled="!canPreview">
              <template #icon>
                <ExportOutlined />
              </template>
              导出对话
              <DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu @click="handleExportMenuClick">
                <a-menu-item key="full">完整版（.txt）</a-menu-item>
                <a-menu-item key="compact">精简版（.txt）</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </span>
      </a-tooltip>
      <a-tooltip v-if="isOwner" :title="deployTooltip">
        <span class="btn-tooltip-wrap">
          <a-button
            type="primary"
            :loading="deploying"
            :disabled="!canPreview"
            @click="$emit('deploy')"
          >
            <template #icon>
              <CloudUploadOutlined />
            </template>
            部署
          </a-button>
        </span>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DownOutlined,
  DownloadOutlined,
  ExportOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons-vue'
import { formatCodeGenType } from '@/utils/codeGenTypes'

const router = useRouter()

const props = defineProps<{
  appName?: string
  codeGenType?: string
  isOwner: boolean
  canDownload: boolean
  canPreview: boolean
  previewDisabledTooltip?: string
  downloading: boolean
  exporting: boolean
  deploying: boolean
}>()

const emit = defineEmits<{
  showDetail: []
  download: []
  export: [mode: 'full' | 'compact']
  deploy: []
}>()

const handleExportMenuClick: MenuProps['onClick'] = ({ key }) => {
  if (!props.canPreview) return
  if (key === 'full' || key === 'compact') {
    emit('export', key)
  }
}

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

const previewBlockedTooltip = computed(
  () => props.previewDisabledTooltip || '请等待网站预览就绪后再操作',
)

const exportTooltip = computed(() =>
  props.canPreview ? '导出对话记录为 .txt 文件' : previewBlockedTooltip.value,
)

const deployTooltip = computed(() =>
  props.canPreview ? '部署应用到云端' : previewBlockedTooltip.value,
)
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

/* 让 disabled 按钮也能触发 tooltip */
.btn-tooltip-wrap {
  display: inline-block;
}

@media (max-width: 768px) {
  .app-name {
    font-size: 16px;
  }
}
</style>
