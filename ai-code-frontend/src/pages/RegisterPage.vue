<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  LockOutlined,
  SafetyCertificateOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/userController'

const router = useRouter()

/**
 * 记录注册请求是否正在处理，用来锁定提交按钮并避免用户连续创建重复请求。
 */
const submitting = ref(false)

/**
 * 保存注册表单中的三个字段，字段名称与后端 UserRegisterRequest 保持一致。
 * Ant Design Vue 会根据这个对象完成输入框双向绑定，并在提交时生成请求参数。
 */
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 检查确认密码是否与第一次输入的密码一致。
 * 空值交给表单的 required 规则处理，这里只负责两个密码之间的关联校验。
 *
 * @param _rule Ant Design Vue 传入的当前校验规则，本次校验不需要读取它
 * @param value 确认密码输入框中的当前值
 * @returns 校验通过时返回成功的 Promise，不一致时返回包含提示信息的失败 Promise
 */
function validateCheckPassword(_rule: unknown, value: string): Promise<void> {
  if (value && value !== formState.userPassword) {
    return Promise.reject(new Error('两次输入的密码不一致'))
  }
  return Promise.resolve()
}

/**
 * 在全部表单规则通过后向服务端提交注册请求。
 * 注册成功后不直接设置登录状态，而是跳转到登录页，让用户使用新账号完成身份验证。
 * 无论请求成功还是失败，处理结束时都会解除按钮的加载状态，方便用户继续操作。
 *
 * @param values Ant Design Vue 校验通过后整理出的账号、密码和确认密码
 */
async function handleSubmit(values: API.UserRegisterRequest) {
  if (submitting.value) return
  submitting.value = true

  try {
    const response = await userRegister(values)
    if (response.data.code === 0) {
      message.success('注册成功，请使用新账号登录')
      await router.replace('/user/login')
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="register-page page-width">
    <section class="register-intro" aria-labelledby="register-title">
      <span class="section-kicker">CREATE YOUR ACCOUNT</span>
      <h1 id="register-title">从一个账号开始，保存每一次创造。</h1>
      <p>创建账号后，你的应用、草稿和创作记录都能集中保存，下一次回来可以继续完善。</p>
      <div class="register-assurance">
        <SafetyCertificateOutlined />
        <span>密码只用于身份校验，服务端仅保存加密后的结果</span>
      </div>
    </section>

    <section class="register-panel" aria-label="账号注册表单">
      <div class="panel-heading">
        <span>创建账号</span>
        <small>LINGBUILD ACCOUNT</small>
      </div>

      <a-form
        :model="formState"
        layout="vertical"
        name="user-register"
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
            placeholder="请输入至少 4 位账号"
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
            autocomplete="new-password"
            placeholder="请输入至少 8 位密码"
            size="large"
          >
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          label="确认密码"
          name="checkPassword"
          :rules="[
            { required: true, message: '请再次输入密码' },
            { min: 8, message: '密码不能少于 8 位' },
            { validator: validateCheckPassword, trigger: ['change', 'blur'] },
          ]"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            size="large"
          >
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-button type="primary" html-type="submit" size="large" block :loading="submitting">
          <template #icon><UserAddOutlined /></template>
          注册并创建账号
        </a-button>
      </a-form>

      <p class="panel-note">
        已有账号？
        <RouterLink to="/user/login">返回登录</RouterLink>
      </p>
    </section>
  </div>
</template>

<style scoped>
.register-page {
  min-height: calc(100dvh - 150px);
  padding: 62px 0 76px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 450px);
  align-items: center;
  gap: clamp(56px, 9vw, 130px);
}

.register-intro h1 {
  max-width: 640px;
  margin: 18px 0 20px;
  font-size: clamp(38px, 5vw, 64px);
  line-height: 1.22;
  letter-spacing: -2.5px;
  font-weight: 680;
}

.register-intro > p {
  max-width: 560px;
  margin: 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 2;
}

.register-assurance {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-top: 32px;
  color: #7d806f;
  font-size: 12px;
}

.register-panel {
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

.register-panel :deep(.ant-form-item-label > label) {
  color: #66675f;
  font-size: 12px;
}

.register-panel :deep(.ant-input-affix-wrapper) {
  border-color: #dedcd2;
  background: #fbfaf6;
  box-shadow: none;
}

.register-panel :deep(.ant-input-affix-wrapper-focused) {
  border-color: #cf8065;
  box-shadow: 0 0 0 2px #d65a3614;
}

.register-panel :deep(.ant-input-prefix) {
  margin-right: 10px;
  color: #9a9b90;
}

.register-panel .ant-btn-primary {
  height: 48px;
  margin-top: 5px;
  box-shadow: none;
}

.panel-note {
  margin: 22px 0 0;
  color: #999a90;
  font-size: 12px;
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
  .register-page {
    grid-template-columns: 1fr;
    gap: 42px;
    padding: 52px 0 64px;
  }

  .register-intro {
    text-align: center;
  }

  .register-intro h1,
  .register-intro > p {
    margin-right: auto;
    margin-left: auto;
  }
}

@media (max-width: 520px) {
  .register-page {
    padding-top: 34px;
  }

  .register-intro h1 {
    font-size: 34px;
    letter-spacing: -1.5px;
  }

  .register-intro > p {
    font-size: 13px;
  }

  .register-panel {
    padding: 25px 21px;
  }
}
</style>
