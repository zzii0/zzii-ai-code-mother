<template>
  <div id="userProfilePage" class="page-container">
    <PageHeader title="个人中心" description="管理你的账号信息与个人资料" />
    <a-card>
      <a-form layout="vertical" :model="formState" @finish="handleSubmit">
        <a-form-item label="账号">
          <a-input :value="loginUserStore.loginUser.userAccount" disabled />
        </a-form-item>
        <a-form-item label="头像">
          <div class="avatar-uploader">
            <a-upload
              v-model:file-list="fileList"
              name="file"
              list-type="picture-card"
              class="avatar-upload"
              :show-upload-list="false"
              :before-upload="beforeUpload"
              accept="image/jpeg,image/png,image/gif,image/webp"
            >
              <img
                v-if="avatarPreviewUrl"
                :src="avatarPreviewUrl"
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
              <a-button v-if="formState.userAvatar" type="link" danger size="small" @click="removeAvatar">
                移除头像
              </a-button>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="formState.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="formState.userProfile"
            placeholder="介绍一下自己"
            :auto-size="{ minRows: 4, maxRows: 6 }"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="saving">保存资料</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import { updateMyUser, uploadUserAvatar } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getApiData, getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'
import { getUserAvatarUrl } from '@/config/env'
import PageHeader from '@/components/common/PageHeader.vue'

const loginUserStore = useLoginUserStore()
const saving = ref(false)
const uploading = ref(false)
const fileList = ref<UploadProps['fileList']>([])

const formState = reactive<API.UserUpdateMyRequest>({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const MAX_SIZE = 2 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

const avatarPreviewUrl = computed(() => getUserAvatarUrl(formState.userAvatar))

const syncFormFromStore = () => {
  const user = loginUserStore.loginUser
  formState.userName = user.userName || ''
  formState.userAvatar = user.userAvatar || ''
  formState.userProfile = user.userProfile || ''
}

watch(
  () => loginUserStore.loginUser,
  () => syncFormFromStore(),
  { immediate: true, deep: true },
)

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
      formState.userAvatar = avatarUrl
      const saveRes = await updateMyUser({
        userName: formState.userName,
        userAvatar: avatarUrl,
        userProfile: formState.userProfile,
      })
      if (isApiSuccess(saveRes)) {
        await loginUserStore.fetchLoginUser()
        message.success('头像上传并保存成功')
      } else {
        message.error('头像已上传，但保存失败：' + getApiErrorMessage(saveRes))
      }
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
  formState.userAvatar = ''
  fileList.value = []
}

const handleSubmit = async () => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    return
  }

  saving.value = true
  try {
    const res = await updateMyUser({
      userName: formState.userName,
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile,
    })
    if (isApiSuccess(res)) {
      await loginUserStore.fetchLoginUser()
      message.success('保存成功')
    } else {
      message.error('保存失败，' + getApiErrorMessage(res))
    }
  } catch (error) {
    console.error('保存资料失败：', error)
    message.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
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
  color: var(--color-text-secondary);
  font-size: 13px;
}

:deep(.ant-card) {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
