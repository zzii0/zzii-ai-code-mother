<template>
  <div class="input-container">
    <a-alert
      v-if="selectedElementInfo"
      class="selected-element-alert"
      type="info"
      closable
      @close="$emit('clearSelection')"
    >
      <template #message>
        <div class="selected-element-info">
          <div class="element-header">
            <span class="element-tag">
              选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
            </span>
            <span v-if="selectedElementInfo.id" class="element-id">#{{ selectedElementInfo.id }}</span>
            <span v-if="selectedElementInfo.className" class="element-class">
              .{{ selectedElementInfo.className.split(' ').join('.') }}
            </span>
          </div>
          <div class="element-details">
            <div v-if="selectedElementInfo.textContent" class="element-item">
              内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
              {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
            </div>
            <div v-if="selectedElementInfo.pagePath" class="element-item">
              页面路径: {{ selectedElementInfo.pagePath }}
            </div>
            <div class="element-item">
              选择器:
              <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
            </div>
          </div>
        </div>
      </template>
    </a-alert>

    <div class="input-wrapper">
      <div class="input-area">
        <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
          <a-textarea
            :value="modelValue"
            :placeholder="placeholder"
            :rows="4"
            :maxlength="1000"
            :disabled="isGenerating || !isOwner"
            @update:value="$emit('update:modelValue', $event)"
            @keydown="handleKeydown"
          />
        </a-tooltip>
        <a-textarea
          v-else
          :value="modelValue"
          :placeholder="placeholder"
          :rows="4"
          :maxlength="1000"
          :disabled="isGenerating"
          @update:value="$emit('update:modelValue', $event)"
          @keydown="handleKeydown"
        />
        <div class="input-actions">
          <a-button type="primary" :loading="isGenerating" :disabled="!isOwner" @click="$emit('send')">
            <template #icon>
              <SendOutlined />
            </template>
          </a-button>
        </div>
      </div>
      <p v-if="isOwner" class="input-hint">Ctrl + Enter 发送</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SendOutlined } from '@ant-design/icons-vue'
import type { ElementInfo } from '@/utils/visualEditor'

defineProps<{
  modelValue: string
  placeholder: string
  isOwner: boolean
  isGenerating: boolean
  selectedElementInfo: ElementInfo | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  clearSelection: []
}>()

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    emit('send')
  }
}
</script>

<style scoped>
.input-container {
  padding: 16px;
  background: white;
}

.input-area {
  position: relative;
}

.input-area :deep(.ant-input) {
  padding-right: 52px;
  padding-bottom: 44px;
}

.input-actions {
  position: absolute;
  bottom: 10px;
  right: 10px;
}

.selected-element-alert {
  margin-bottom: 12px;
}

.element-tag {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #007bff;
}

.element-id {
  color: #28a745;
  margin-left: 4px;
}

.element-class {
  color: #ffc107;
  margin-left: 4px;
}

.element-selector-code {
  font-family: 'Monaco', 'Menlo', monospace;
  background: #f6f8fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 12px;
  color: #d73a49;
  border: 1px solid #e1e4e8;
}

.input-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
  text-align: right;
}
</style>
