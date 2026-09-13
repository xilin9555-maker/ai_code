<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { InputNumber, message, Skeleton, type FormInstance } from 'ant-design-vue'
import { getAppVoById, updateApp, updateAppByAdmin } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getCodeGenTypeLabel } from '@/constants/codeGenType'
import { normalizeApp, toApiId, type AppView } from '@/utils/app'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const appId = computed(() => String(route.params.id ?? ''))
const app = ref<AppView>()
const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance>()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const form = reactive({
  appName: '',
  cover: '',
  priority: 0,
})

/**
 * 读取应用后同时检查页面操作权限。
 * 普通用户只能编辑自己的应用，管理员可以维护任意应用的展示信息和精选优先级。
 */
async function loadApp() {
  loading.value = true
  try {
    const response = await getAppVoById({ id: toApiId(appId.value) })
    if (!response.data.data) return
    const currentApp = normalizeApp(response.data.data)
    const currentUserId = String(loginUserStore.loginUser.id ?? '')
    if (!isAdmin.value && currentApp.userId !== currentUserId) {
      await router.replace('/no-auth')
      return
    }

    app.value = currentApp
    Object.assign(form, {
      appName: currentApp.appName ?? '',
      cover: currentApp.cover ?? '',
      priority: currentApp.priority ?? 0,
    })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取应用信息失败')
    await router.replace('/')
  } finally {
    loading.value = false
  }
}

/**
 * 根据当前身份选择对应接口。
 * 用户接口只提交应用名称；管理员接口额外提交封面和优先级，后端仍会再次校验权限。
 */
async function saveApp() {
  if (saving.value || !app.value) return
  try {
    await formRef.value?.validate()
    saving.value = true
    const id = toApiId(appId.value)
    const response = isAdmin.value
      ? await updateAppByAdmin({
          id,
          appName: form.appName.trim(),
          cover: form.cover.trim(),
          priority: form.priority,
        })
      : await updateApp({ id, appName: form.appName.trim() })

    if (response.data.data) {
      message.success('应用信息已保存')
      await router.replace(`/app/chat/${appId.value}`)
    }
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'errorFields' in error) return
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadApp)
</script>

<template>
  <div class="app-edit-page page-width interior-page">
    <header class="edit-heading">
      <a-button type="text" aria-label="返回应用" @click="router.back()">
        <ArrowLeftOutlined />
      </a-button>
      <div>
        <span class="section-kicker">APPLICATION SETTINGS</span>
        <h1>编辑应用</h1>
        <p>调整作品在列表中的名称和展示信息。</p>
      </div>
    </header>

    <Skeleton v-if="loading" active :paragraph="{ rows: 8 }" />
    <section v-else-if="app" class="edit-card">
      <div
        class="cover-preview"
        :style="form.cover ? { backgroundImage: `url(${form.cover})` } : undefined"
      >
        <div v-if="!form.cover">
          <span>✦</span>
          <strong>{{ form.appName || '未命名应用' }}</strong>
          <small>{{ getCodeGenTypeLabel(app.codeGenType) }}</small>
        </div>
      </div>

      <a-form ref="formRef" :model="form" layout="vertical" @finish="saveApp">
        <a-form-item label="应用 ID">
          <a-input :value="app.id" disabled />
          <span class="field-hint">应用 ID 用于关联生成目录，保存时不会改变。</span>
        </a-form-item>

        <a-form-item
          label="应用名称"
          name="appName"
          :rules="[
            { required: true, whitespace: true, message: '请输入应用名称' },
            { max: 256, message: '应用名称不能超过 256 个字符' },
          ]"
        >
          <a-input
            v-model:value="form.appName"
            :maxlength="256"
            show-count
            placeholder="给作品取一个清楚的名字"
          />
        </a-form-item>

        <template v-if="isAdmin">
          <a-form-item
            label="封面地址"
            name="cover"
            :rules="[{ max: 512, message: '封面地址不能超过 512 个字符' }]"
          >
            <a-input
              v-model:value="form.cover"
              allow-clear
              placeholder="输入可公开访问的图片地址"
            />
            <span class="field-hint">留空时使用系统生成的默认封面。</span>
          </a-form-item>

          <a-form-item
            label="展示优先级"
            name="priority"
            :rules="[{ required: true, message: '请输入展示优先级' }]"
          >
            <InputNumber v-model:value="form.priority" :min="0" :max="9999" />
            <span class="field-hint">设置为 99 后会进入首页精选应用，普通应用使用 0。</span>
          </a-form-item>
        </template>

        <a-form-item label="初始化需求">
          <a-textarea :value="app.initPrompt" :auto-size="{ minRows: 4, maxRows: 8 }" disabled />
          <span class="field-hint">它记录应用最初的创建目标，可在生成工作台继续提出修改。</span>
        </a-form-item>

        <div class="form-actions">
          <a-button @click="router.back()">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="saving">
            <SaveOutlined /> 保存修改
          </a-button>
        </div>
      </a-form>
    </section>
  </div>
</template>

<style scoped>
.app-edit-page {
  max-width: 940px;
}

.edit-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 32px;
}

.edit-heading h1 {
  margin: 10px 0 7px;
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 670;
  letter-spacing: -1.4px;
}

.edit-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.edit-card {
  display: grid;
  grid-template-columns: minmax(260px, 0.75fr) minmax(380px, 1.25fr);
  gap: 38px;
  padding: 32px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fffefb;
  box-shadow: 0 16px 44px #4d46310b;
}

.cover-preview {
  height: 310px;
  overflow: hidden;
  border: 1px solid #e0ddd2;
  border-radius: 12px;
  background-color: #e8e5d9;
  background-position: center;
  background-size: cover;
}

.cover-preview > div {
  display: flex;
  height: 100%;
  padding: 28px;
  flex-direction: column;
  justify-content: flex-end;
  background:
    radial-gradient(circle at 75% 22%, #dca88d 0 7%, transparent 7.5%),
    linear-gradient(145deg, #ece9dc 0 53%, #d9dfd0 53% 76%, #dcb49d 76%);
}

.cover-preview span {
  margin-bottom: auto;
  color: #a36e54;
  font-size: 28px;
}

.cover-preview strong {
  overflow: hidden;
  font-family: Georgia, 'Songti SC', serif;
  font-size: 26px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-preview small {
  margin-top: 8px;
  color: #73756b;
  font-size: 8px;
  letter-spacing: 1.4px;
  text-transform: uppercase;
}

.edit-card :deep(.ant-form-item-label > label) {
  color: #63645d;
  font-size: 12px;
}

.edit-card :deep(.ant-input),
.edit-card :deep(.ant-input-number) {
  background: #fbfaf6;
}

.edit-card :deep(.ant-input-number) {
  width: 160px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: #97988e;
  font-size: 10px;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

@media (max-width: 760px) {
  .edit-card {
    grid-template-columns: 1fr;
    padding: 20px;
  }

  .cover-preview {
    height: 230px;
  }
}
</style>
