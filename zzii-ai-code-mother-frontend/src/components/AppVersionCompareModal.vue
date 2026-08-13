<template>
  <a-modal
    v-model:open="visible"
    title="版本对比"
    :footer="null"
    width="92vw"
    :destroy-on-close="true"
    class="version-compare-modal"
  >
    <a-spin :spinning="loading">
      <div v-if="!hasHistoryVersion" class="empty-tip">
        暂无历史版本。重新生成代码后，系统会自动归档上一版代码。
      </div>
      <template v-else>
        <div class="compare-toolbar">
          <a-select
            v-model:value="oldVersionKey"
            placeholder="选择旧版本"
            style="min-width: 220px"
            :options="versionOptions"
            @change="loadCompare"
          />
          <SwapOutlined class="swap-icon" />
          <a-select
            v-model:value="newVersionKey"
            placeholder="选择新版本"
            style="min-width: 220px"
            :options="versionOptions"
            @change="loadCompare"
          />
          <a-select
            v-model:value="selectedFile"
            placeholder="选择文件"
            style="min-width: 200px"
            :options="fileOptions"
            @change="loadCompare"
          />
          <span class="removal-count">{{ compareResult?.removals || 0 }} removals</span>
          <span class="addition-count">{{ compareResult?.additions || 0 }} additions</span>
          <a-popconfirm
            v-if="canRollback"
            title="回退后会先归档当前代码，确认回退到该历史版本？"
            ok-text="确认回退"
            cancel-text="取消"
            @confirm="handleRollback"
          >
            <a-button danger size="small">回退到旧版本</a-button>
          </a-popconfirm>
        </div>

        <div class="diff-board">
          <div class="diff-pane">
            <div class="pane-header">
              <span>{{ oldLines.length }} lines</span>
              <a-button type="link" size="small" @click="copyContent(oldContent)">Copy</a-button>
            </div>
            <div class="code-list">
              <div
                v-for="(line, index) in oldRows"
                :key="'old-' + index"
                class="code-line"
                :class="line.type"
              >
                <span class="line-no">{{ line.no || '' }}</span>
                <pre>{{ line.text }}</pre>
              </div>
            </div>
          </div>
          <div class="diff-pane">
            <div class="pane-header">
              <span>{{ newLines.length }} lines</span>
              <a-button type="link" size="small" @click="copyContent(newContent)">Copy</a-button>
            </div>
            <div class="code-list">
              <div
                v-for="(line, index) in newRows"
                :key="'new-' + index"
                class="code-line"
                :class="line.type"
              >
                <span class="line-no">{{ line.no || '' }}</span>
                <pre>{{ line.text }}</pre>
              </div>
            </div>
          </div>
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { SwapOutlined } from '@ant-design/icons-vue'
import {
  compareAppVersion,
  listAppVersions,
  rollbackAppVersion,
} from '@/api/appController'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'

interface Props {
  open: boolean
  appId?: number
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'rollback-success'): void
}

interface DiffRow {
  type: 'same' | 'removed' | 'added' | 'empty'
  no: number | ''
  text: string
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)
const versions = ref<API.AppVersionVO[]>([])
const oldVersionKey = ref<string>()
const newVersionKey = ref<string>()
const selectedFile = ref<string>()
const compareResult = ref<API.AppVersionCompareVO>()

const visible = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})

const versionOptions = computed(() =>
  versions.value.map((item) => ({
    label: item.versionName || item.versionKey,
    value: item.versionKey,
  })),
)

const fileOptions = computed(() =>
  (compareResult.value?.fileList || []).map((file) => ({
    label: file,
    value: file,
  })),
)

const hasHistoryVersion = computed(
  () => versions.value.some((item) => !item.current) && versions.value.length > 0,
)

const canRollback = computed(() => {
  return !!oldVersionKey.value && oldVersionKey.value !== 'current'
})

const oldContent = computed(() => compareResult.value?.oldContent || '')
const newContent = computed(() => compareResult.value?.newContent || '')
const oldLines = computed(() => splitLines(oldContent.value))
const newLines = computed(() => splitLines(newContent.value))

const diffRows = computed(() => buildDiffRows(oldLines.value, newLines.value))
const oldRows = computed(() => diffRows.value.oldRows)
const newRows = computed(() => diffRows.value.newRows)

const resetState = () => {
  versions.value = []
  oldVersionKey.value = undefined
  newVersionKey.value = undefined
  selectedFile.value = undefined
  compareResult.value = undefined
}

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      resetState()
      return
    }
    await loadVersions()
  },
)

const loadVersions = async () => {
  if (!props.appId) {
    return
  }
  loading.value = true
  try {
    const res = await listAppVersions({ appId: props.appId })
    if (!isApiSuccess(res)) {
      message.error(getApiErrorMessage(res) || '加载版本列表失败')
      return
    }
    versions.value = res.data.data || []
    if (!hasHistoryVersion.value) {
      return
    }
    const history = versions.value.filter((item) => !item.current)
    const current = versions.value.find((item) => item.current)
    oldVersionKey.value = history[0]?.versionKey
    newVersionKey.value = current?.versionKey || history[1]?.versionKey
    selectedFile.value = undefined
    await loadCompare()
  } catch (error) {
    console.error(error)
    message.error('加载版本列表失败')
  } finally {
    loading.value = false
  }
}

const loadCompare = async () => {
  if (!props.appId || !oldVersionKey.value || !newVersionKey.value) {
    return
  }
  loading.value = true
  try {
    const res = await compareAppVersion({
      appId: props.appId,
      oldVersionKey: oldVersionKey.value,
      newVersionKey: newVersionKey.value,
      filePath: selectedFile.value,
    })
    if (!isApiSuccess(res)) {
      message.error(getApiErrorMessage(res) || '版本对比失败')
      return
    }
    compareResult.value = res.data.data
    selectedFile.value = res.data.data?.filePath
  } catch (error) {
    console.error(error)
    message.error('版本对比失败')
  } finally {
    loading.value = false
  }
}

const handleRollback = async () => {
  if (!props.appId || !oldVersionKey.value || oldVersionKey.value === 'current') {
    return
  }
  loading.value = true
  try {
    const res = await rollbackAppVersion({
      appId: props.appId,
      versionKey: oldVersionKey.value,
    })
    if (isApiSuccess(res) && res.data.data) {
      message.success('已回退到选中的历史版本')
      emit('rollback-success')
      await loadVersions()
    } else {
      message.error(getApiErrorMessage(res) || '回退失败')
    }
  } catch (error) {
    console.error(error)
    message.error('回退失败')
  } finally {
    loading.value = false
  }
}

const copyContent = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content || '')
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

const splitLines = (content: string): string[] => {
  if (!content) {
    return []
  }
  return content.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
}

const buildDiffRows = (oldLineList: string[], newLineList: string[]) => {
  const oldLength = oldLineList.length
  const newLength = newLineList.length
  const dp = Array.from({ length: oldLength + 1 }, () => Array(newLength + 1).fill(0))

  for (let i = oldLength - 1; i >= 0; i--) {
    for (let j = newLength - 1; j >= 0; j--) {
      dp[i][j] =
        oldLineList[i] === newLineList[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  const oldRows: DiffRow[] = []
  const newRows: DiffRow[] = []
  let i = 0
  let j = 0
  let oldNo = 1
  let newNo = 1

  while (i < oldLength && j < newLength) {
    if (oldLineList[i] === newLineList[j]) {
      oldRows.push({ type: 'same', no: oldNo++, text: oldLineList[i] })
      newRows.push({ type: 'same', no: newNo++, text: newLineList[j] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      oldRows.push({ type: 'removed', no: oldNo++, text: oldLineList[i] })
      newRows.push({ type: 'empty', no: '', text: '' })
      i++
    } else {
      oldRows.push({ type: 'empty', no: '', text: '' })
      newRows.push({ type: 'added', no: newNo++, text: newLineList[j] })
      j++
    }
  }

  while (i < oldLength) {
    oldRows.push({ type: 'removed', no: oldNo++, text: oldLineList[i] })
    newRows.push({ type: 'empty', no: '', text: '' })
    i++
  }
  while (j < newLength) {
    oldRows.push({ type: 'empty', no: '', text: '' })
    newRows.push({ type: 'added', no: newNo++, text: newLineList[j] })
    j++
  }

  return { oldRows, newRows }
}
</script>

<style scoped>
.empty-tip {
  padding: 48px 0;
  text-align: center;
  color: #8c8c8c;
}

.compare-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.swap-icon {
  color: #8c8c8c;
}

.removal-count {
  color: #cf1322;
  font-weight: 500;
}

.addition-count {
  color: #389e0d;
  font-weight: 500;
}

.diff-board {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  height: 70vh;
}

.diff-pane {
  display: flex;
  flex-direction: column;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.pane-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  color: #595959;
  font-size: 13px;
}

.code-list {
  flex: 1;
  overflow: auto;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.code-line {
  display: flex;
  min-height: 22px;
}

.code-line.removed {
  background: #fff1f0;
}

.code-line.added {
  background: #f6ffed;
}

.code-line.empty {
  background: #fafafa;
}

.line-no {
  width: 48px;
  flex-shrink: 0;
  text-align: right;
  padding: 0 8px;
  color: #bfbfbf;
  user-select: none;
  border-right: 1px solid #f0f0f0;
}

.code-line pre {
  margin: 0;
  padding: 0 12px;
  white-space: pre-wrap;
  word-break: break-all;
  flex: 1;
}

@media (max-width: 900px) {
  .diff-board {
    grid-template-columns: 1fr;
    height: auto;
  }

  .diff-pane {
    height: 40vh;
  }
}
</style>
