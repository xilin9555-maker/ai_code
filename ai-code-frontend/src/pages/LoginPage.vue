<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

/**
 * 提交账号密码，登录成功后同步全局用户状态并进入目标页面。
 */
async function handleSubmit(values: API.UserLoginRequest) {
  if (submitting.value) return
  submitting.value = true
  try {
    const response = await userLogin(values)
    if (response.data.code === 0 && response.data.data) {
      loginUserStore.setLoginUser(response.data.data)
      message.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      await router.replace(redirect.startsWith('/') ? redirect : '/')
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page page-width">
    <section class="login-intro" aria-labelledby="login-title">
      <span class="section-kicker">WELCOME BACK</span>
      <h1 id="login-title">继续构建你的下一个想法。</h1>
      <p>登录后，创作记录和个人空间会与当前账号关联，让每一次灵感都能顺畅延续。</p>
      <div class="login-assurance">
        <SafetyCertificateOutlined />
        <span>登录状态由服务端会话安全维护</span>
      </div>
    </section>

    <section class="login-panel" aria-label="账号登录表单">
      <div class="panel-heading">
        <span>账号登录</span>
        <small>LINGBUILD ACCOUNT</small>
      </div>

      <a-form
        :model="formState"
        layout="vertical"
        name="user-login"
        autocomplete="on"
        @finish="handleSubmit"
      >
        <a-form-item
          label="账号"
          name="userAccount"
          :rules="[
            { required: true, message: '请输入账号' },
            { min: 4, message: '账号不能少于 4 位' },
          ]"
        >
          <a-input
            v-model:value="formState.userAccount"
            autocomplete="username"
            placeholder="请输入账号"
            size="large"
          >
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码不能少于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            autocomplete="current-password"
            placeholder="请输入密码"
            size="large"
          >
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-button type="primary" html-type="submit" size="large" block :loading="submitting">
          登录并继续
        </a-button>
      </a-form>

      <p class="panel-note">
        还没有账号？
        <RouterLink to="/user/register">立即注册</RouterLink>
      </p>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100dvh - 150px);
  padding: 74px 0 88px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 430px);
  align-items: center;
  gap: clamp(56px, 9vw, 130px);
}

.login-intro h1 {
  max-width: 620px;
  margin: 18px 0 20px;
  font-size: clamp(38px, 5vw, 64px);
  line-height: 1.22;
  letter-spacing: -2.5px;
  font-weight: 680;
}

.login-intro > p {
  max-width: 560px;
  margin: 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 2;
}

.login-assurance {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-top: 32px;
  color: #7d806f;
  font-size: 12px;
}

.login-panel {
  padding: 34px;
  background: #fffefb;
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: 0 18px 50px #4d463112;
}

.panel-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 30px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
  font-size: 20px;
  font-weight: 650;
}

.panel-heading small {
  color: #a2a397;
  font-size: 8px;
  letter-spacing: 1.4px;
  font-weight: 600;
}

.login-panel :deep(.ant-form-item-label > label) {
  color: #66675f;
  font-size: 12px;
}

.login-panel :deep(.ant-input-affix-wrapper) {
  border-color: #dedcd2;
  background: #fbfaf6;
  box-shadow: none;
}

.login-panel :deep(.ant-input-affix-wrapper-focused) {
  border-color: #cf8065;
  box-shadow: 0 0 0 2px #d65a3614;
}

.login-panel :deep(.ant-input-prefix) {
  margin-right: 10px;
  color: #9a9b90;
}

.login-panel .ant-btn-primary {
  height: 48px;
  margin-top: 5px;
  box-shadow: none;
}

.panel-note {
  margin: 22px 0 0;
  color: #999a90;
  font-size: 11px;
  text-align: center;
}

.panel-note a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}

.panel-note a:hover {
  text-decoration: underline;
}

@media (max-width: 820px) {
  .login-page {
    grid-template-columns: 1fr;
    gap: 42px;
    padding: 52px 0 64px;
  }

  .login-intro {
    text-align: center;
  }

  .login-intro h1,
  .login-intro > p {
    margin-right: auto;
    margin-left: auto;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding-top: 34px;
  }

  .login-intro h1 {
    font-size: 34px;
    letter-spacing: -1.5px;
  }

  .login-intro > p {
    font-size: 13px;
  }

  .login-panel {
    padding: 25px 21px;
  }
}
</style>
