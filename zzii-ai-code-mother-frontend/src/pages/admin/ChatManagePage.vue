<template>
  <AdminPageLayout title="对话管理" description="查看与管理平台对话记录">
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="消息内容">
        <a-input v-model:value="searchParams.message" placeholder="输入消息内容" allow-clear />
      </a-form-item>
      <a-form-item label="消息类型">
        <a-select
          v-model:value="searchParams.messageType"
          placeholder="选择消息类型"
          style="width: 120px"
          allow-clear
        >
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="user">用户消息</a-select-option>
          <a-select-option value="assistant">AI消息</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="应用ID">
        <a-input v-model:value="searchParams.appId" placeholder="输入应用ID" allow-clear />
      </a-form-item>
      <a-form-item label="用户ID">
        <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <a-table
      :columns="columns"
      :data-source="data"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1400 }"
      row-key="id"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'message'">
          <a-tooltip :title="record.message">
            <div class="message-text">{{ record.message }}</div>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
            {{ record.messageType === 'user' ? '用户消息' : 'AI消息' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="viewAppChat(record.appId)">
              查看对话
            </a-button>
            <a-popconfirm title="确定要删除这条消息吗？" @confirm="deleteMessage">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </AdminPageLayout>
</template>

<script lang="ts" setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { formatTime } from '@/utils/time'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import { usePaginatedTable } from '@/composables/usePaginatedTable'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80, fixed: 'left' as const },
  { title: '消息内容', dataIndex: 'message', width: 300 },
  { title: '消息类型', dataIndex: 'messageType', width: 100 },
  { title: '应用ID', dataIndex: 'appId', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
]

const { data, loading, searchParams, pagination, fetchData, doTableChange, doSearch } =
  usePaginatedTable<API.ChatHistory, API.ChatHistoryQueryRequest>(
    listAllChatHistoryByPageForAdmin,
    {
      pageNum: 1,
      pageSize: 10,
    },
  )

const viewAppChat = (appId: number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}`)
  }
}

const deleteMessage = () => {
  message.info('当前版本暂未开放删除对话功能')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.message-text {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}
</style>
