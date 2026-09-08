<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { SaveOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message, type FormInstance } from 'ant-design-vue'
import { updateMyUser } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()
const formRef = ref<FormInstance>()
const saving = ref(false)

/**
 * 个人设置只包含允许用户自行维护的公开资料。
 * 账号、角色和密码不放进表单，避免普通资料更新意外影响登录凭据或系统权限。
 */
const formState = reactive<API.UserUpdateMyRequest>({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

/**
 * 把全局登录状态中的最新资料填入表单，让用户进入页面时看到当前保存值。
 */
function fillCurrentProfile() {
  Object.assign(formState, {
    userName: loginUserStore.loginUser.userName ?? '',
    userAvatar: loginUserStore.loginUser.userAvatar ?? '',
    userProfile: loginUserStore.loginUser.userProfile ?? '',
  })
}

/**
 * 校验并保存当前用户的公开资料。
 * 服务端根据 Session 确认目标用户，保存成功后重新获取登录信息，使页头头像和昵称
 * 不需要刷新浏览器就能同步变化。
 */
async function handleSave() {
  if (saving.value) return

  try {
    await formRef.value?.validate()
    saving.value = true
    const response = await updateMyUser({ ...formState })
    if (response.data.code === 0 && response.data.data) {
      await loginUserStore.fetchLoginUser()
      fillCurrentProfile()
      message.success('个人资料已保存')
    }
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'errorFields' in error) return
    message.error(error instanceof Error ? error.message : '保存个人资料失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(fillCurrentProfile)
</script>

<template>
  <main class="settings-page page-width interior-page">
    <header class="settings-heading">
      <span class="section-kicker">PERSONAL SETTINGS</span>
      <h1>个人设置</h1>
      <p>维护公开展示的昵称、头像和个人简介。登录账号和角色由系统单独管理。</p>
    </header>

    <section class="settings-panel" aria-label="个人资料编辑表单">
      <aside class="profile-preview">
        <a-avatar :src="formState.userAvatar" :size="82">
          <template #icon><UserOutlined /></template>
        </a-avatar>
        <strong>{{ formState.userName || '未设置昵称' }}</strong>
        <span>{{ loginUserStore.loginUser.userAccount }}</span>
      </aside>

      <a-form ref="formRef" :model="formState" layout="vertical" @finish="handleSave">
        <a-form-item
          label="用户昵称"
          name="userName"
          :rules="[
            { required: true, whitespace: true, message: '请输入用户昵称' },
            { max: 256, message: '用户昵称不能超过 256 个字符' },
          ]"
        >
          <a-input v-model:value="formState.userName" allow-clear placeholder="请输入用户昵称" />
        </a-form-item>

        <a-form-item
          label="头像地址"
          name="userAvatar"
          :rules="[{ max: 1024, message: '头像地址不能超过 1024 个字符' }]"
        >
          <a-input
            v-model:value="formState.userAvatar"
            allow-clear
            placeholder="请输入可公开访问的图片地址"
          />
        </a-form-item>

        <a-form-item
          label="个人简介"
          name="userProfile"
          :rules="[{ max: 512, message: '个人简介不能超过 512 个字符' }]"
        >
          <a-textarea
            v-model:value="formState.userProfile"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="512"
            show-count
            placeholder="写一段简短的自我介绍"
          />
        </a-form-item>

        <a-button type="primary" html-type="submit" :loading="saving">
          <template #icon><SaveOutlined /></template>
          保存修改
        </a-button>
      </a-form>
    </section>
  </main>
</template>

<style scoped>
.settings-page {
  padding-top: 54px;
}

.settings-heading {
  margin-bottom: 30px;
}

.settings-heading h1 {
  margin: 12px 0 10px;
  color: var(--ink);
  font-size: clamp(32px, 4vw, 46px);
}

.settings-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.settings-panel {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 42px;
  max-width: 820px;
  padding: 34px;
  background: #fffefb;
  border: 1px solid var(--line);
  border-radius: 15px;
  box-shadow: 0 14px 40px #4d46310a;
}

.profile-preview {
  display: flex;
  min-width: 0;
  padding: 10px 20px 24px;
  align-items: center;
  align-self: start;
  flex-direction: column;
  border-right: 1px solid var(--line);
  text-align: center;
}

.profile-preview strong {
  max-width: 150px;
  margin-top: 15px;
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-preview span {
  max-width: 150px;
  margin-top: 5px;
  overflow: hidden;
  color: #929388;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 680px) {
  .settings-panel {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 25px 20px;
  }

  .profile-preview {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
}
</style>
