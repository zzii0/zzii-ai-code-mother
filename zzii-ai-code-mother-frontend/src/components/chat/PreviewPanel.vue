<template>
  <div class="preview-section">
    <div class="preview-header">
      <h3>生成后的网页展示</h3>
      <div class="preview-actions">
        <a-button
          v-if="isOwner && previewUrl && !showBusyState && !buildError"
          type="link"
          :danger="isEditMode"
          :class="{ 'edit-mode-active': isEditMode }"
          style="padding: 0; height: auto; margin-right: 12px"
          @click="$emit('toggleEditMode')"
        >
          <template #icon>
            <EditOutlined />
          </template>
          {{ isEditMode ? '退出编辑' : '编辑模式' }}
        </a-button>
        <span
          v-if="isEditMode && supportsInlineEdit && previewUrl && !showBusyState && !buildError"
          class="inline-edit-hint"
        >
          双击文本可直接修改
        </span>
        <a-button v-if="previewUrl && !showBusyState && !buildError" type="link" @click="$emit('openInNewTab')">
          <template #icon>
            <ExportOutlined />
          </template>
          新窗口打开
        </a-button>
      </div>
    </div>
    <div class="preview-content">
      <div v-if="buildError && !showBusyState" class="preview-error">
        <div class="placeholder-icon">⚠️</div>
        <p class="error-title">预览未就绪</p>
        <p class="error-detail">{{ buildError }}</p>
        <p class="error-hint">请查看左侧对话中的错误详情，或描述需要如何修复。</p>
      </div>
      <div v-else-if="!previewUrl && !showBusyState" class="preview-placeholder">
        <div class="placeholder-icon">🌐</div>
        <p>网站文件生成完成后将在这里展示</p>
      </div>
      <div v-else-if="showBusyState" class="preview-loading">
        <a-skeleton active :paragraph="{ rows: 8 }" />
        <p>{{ statusText }}</p>
      </div>
      <iframe
        v-else
        :src="previewUrl"
        class="preview-iframe"
        frameborder="0"
        @load="$emit('iframeLoad')"
      ></iframe>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EditOutlined, ExportOutlined } from '@ant-design/icons-vue'
import type { BuildPhase } from '@/composables/useChatStream'

const props = withDefaults(
  defineProps<{
    previewUrl: string
    isGenerating: boolean
    isOwner: boolean
    isEditMode: boolean
    supportsInlineEdit?: boolean
    buildPhase?: BuildPhase
    buildStatusText?: string
    buildError?: string
  }>(),
  {
    supportsInlineEdit: false,
    buildPhase: 'idle',
    buildStatusText: '',
    buildError: '',
  },
)

defineEmits<{
  toggleEditMode: []
  openInNewTab: []
  iframeLoad: []
}>()

/**
 * 生成过程中（含校验通过、等待 iframe 刷新）都应显示 loading，
 * 避免 save_success 后、previewUrl 尚未写入时落到「占位空白」态。
 */
const showBusyState = computed(() => {
  if (props.buildError && props.buildPhase === 'failed') return false
  if (props.isGenerating) return true
  // 已成功但预览 URL 尚未挂上时，继续显示加载
  return props.buildPhase === 'success' && !props.previewUrl
})

const WAIT_HINT = '，请耐心等待...'

/** 为预览区 loading 文案统一追加等待提示 */
const withWaitHint = (text: string) => {
  if (!text || text.includes('请耐心等待')) return text
  const base = text.replace(/\.{3}$/, '').trim()
  return `${base}${WAIT_HINT}`
}

const statusText = computed(() => {
  let text: string
  if (props.buildStatusText) {
    text = props.buildStatusText
  } else if (props.buildPhase === 'building') {
    text = '正在构建预览...'
  } else if (props.buildPhase === 'validating') {
    text = '正在校验生成的代码...'
  } else if (props.buildPhase === 'fixing') {
    text = '正在自动补生成问题文件...'
  } else if (props.buildPhase === 'success') {
    text = '校验通过，正在刷新预览...'
  } else {
    text = '正在生成网站...'
  }
  return withWaitHint(text)
})
</script>

<style scoped>
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder,
.preview-loading,
.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  padding: 24px;
  text-align: center;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-error .error-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #a8071a;
}

.preview-error .error-detail {
  margin: 0 0 12px;
  max-width: 480px;
  color: #cf1322;
  word-break: break-word;
}

.preview-error .error-hint {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.edit-mode-active {
  background-color: #52c41a !important;
  border-color: #52c41a !important;
  color: white !important;
}

.inline-edit-hint {
  margin-right: 12px;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
