<template>
  <AdminPageLayout title="用户管理" description="管理平台用户账号与角色">
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
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
      :scroll="{ x: 1000 }"
      row-key="id"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-avatar v-if="record.userAvatar" :src="getUserAvatarUrl(record.userAvatar)" :size="48">
            {{ record.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <span v-else class="no-avatar">暂无</span>
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
            {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" @click="openEditModal(record)">编辑</a-button>
            <a-popconfirm title="确定要删除该用户吗？" @confirm="doDelete(record.id)">
              <a-button danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="editModalOpen"
      title="编辑用户"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleEditSubmit"
    >
      <a-form layout="vertical" :model="editForm">
        <a-form-item label="用户名">
          <a-input v-model:value="editForm.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="头像">
          <div class="avatar-uploader">
            <a-upload
              name="file"
              list-type="picture-card"
              class="avatar-upload"
              :show-upload-list="false"
              :before-upload="beforeUpload"
              accept="image/jpeg,image/png,image/gif,image/webp"
            >
              <img
                v-if="editAvatarPreviewUrl"
                :src="editAvatarPreviewUrl"
                alt="avatar"
                class="avatar-preview"
              />
              <div v-else class="avatar-upload-placeholder">
                <LoadingOutlined v-if="uploading" />
                <PlusOutlined v-else />
                <div class="upload-text">上传头像</div>
              </div>
            </a-upload>
            <div class="avatar-tips">
              <p>支持 JPG、PNG、GIF、WebP，大小不超过 2MB</p>
              <a-button
                v-if="editForm.userAvatar"
                type="link"
                danger
                size="small"
                @click="removeAvatar"
              >
                移除头像
              </a-button>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="editForm.userProfile"
            placeholder="请输入简介"
            :auto-size="{ minRows: 3, maxRows: 5 }"
          />
        </a-form-item>
        <a-form-item label="用户角色">
          <a-select v-model:value="editForm.userRole" :options="userRoleOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </AdminPageLayout>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUser, listUserVoByPage, updateUser, uploadUserAvatar } from '@/api/userController'
import { message } from 'ant-design-vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import dayjs from 'dayjs'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import { usePaginatedTable } from '@/composables/usePaginatedTable'
import { getApiData, getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { getUserAvatarUrl } from '@/config/env'

const columns = [
  { title: 'id', dataIndex: 'id' },
  { title: '账号', dataIndex: 'userAccount' },
  { title: '用户名', dataIndex: 'userName' },
  { title: '头像', dataIndex: 'userAvatar', width: 100 },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '用户角色', dataIndex: 'userRole' },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '操作', key: 'action', fixed: 'right' as const, width: 160 },
]

const { data, loading, searchParams, pagination, fetchData, doTableChange, doSearch } =
  usePaginatedTable<API.UserVO, API.UserQueryRequest>(listUserVoByPage, {
    pageNum: 1,
    pageSize: 10,
  })

const saving = ref(false)
const uploading = ref(false)
const editModalOpen = ref(false)
const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: '',
})

const MAX_SIZE = 2 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

const editAvatarPreviewUrl = computed(() => getUserAvatarUrl(editForm.userAvatar))

const userRoleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

const doDelete = async (id: number) => {
  if (!id) return
  const res = await deleteUser({ id })
  if (isApiSuccess(res)) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败，' + getApiErrorMessage(res))
  }
}

const openEditModal = (record: API.UserVO) => {
  editForm.id = record.id
  editForm.userName = record.userName
  editForm.userAvatar = record.userAvatar || ''
  editForm.userProfile = record.userProfile
  editForm.userRole = record.userRole
  editModalOpen.value = true
}

const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
  if (!ALLOWED_TYPES.includes(file.type)) {
    message.error('仅支持 JPG、PNG、GIF、WebP 格式')
    return false
  }
  if (file.size > MAX_SIZE) {
    message.error('图片大小不能超过 2MB')
    return false
  }

  uploading.value = true
  try {
    const res = await uploadUserAvatar(file)
    const avatarUrl = getApiData(res)
    if (isApiSuccess(res) && avatarUrl) {
      editForm.userAvatar = avatarUrl
      message.success('头像上传成功，点击保存生效')
    } else {
      message.error('头像上传失败，' + getApiErrorMessage(res))
    }
  } catch (error) {
    console.error('上传头像失败：', error)
    message.error('头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
  return false
}

const removeAvatar = () => {
  editForm.userAvatar = ''
}

const handleEditSubmit = async () => {
  saving.value = true
  try {
    const res = await updateUser({ ...editForm })
    if (isApiSuccess(res)) {
      message.success('更新成功')
      editModalOpen.value = false
      await fetchData()
    } else {
      message.error('更新失败，' + getApiErrorMessage(res))
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.no-avatar {
  color: #999;
  font-size: 13px;
}

.avatar-uploader {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.avatar-upload :deep(.ant-upload) {
  width: 104px;
  height: 104px;
  margin: 0;
  overflow: hidden;
  border-radius: 8px;
}

.avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #999;
}

.upload-text {
  margin-top: 8px;
  font-size: 12px;
}

.avatar-tips {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding-top: 8px;
}

.avatar-tips p {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
</style>
