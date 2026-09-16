<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal, Pagination, Skeleton } from 'ant-design-vue'
import {
  FolderOpenOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
import { deleteApp, listMyAppVoByPage } from '@/api/appController'
import { normalizeApp, toApiId, type AppView } from '@/utils/app'

const router = useRouter()
const apps = ref<AppView[]>([])
const total = ref(0)
const loading = ref(false)
const deletingAppId = ref('')
const query = reactive({
  pageNum: 1,
  pageSize: 9,
  appName: '',
})

/**
 * 查询参数中不传 userId，应用范围始终由服务端根据当前 Session 决定。
 * 即使客户端请求被人为修改，也无法通过这个接口读取其他用户的应用。
 */
async function loadApps() {
  if (loading.value) return
  loading.value = true
  try {
    const response = await listMyAppVoByPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      appName: query.appName.trim() || undefined,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    const page = response.data.data
    apps.value = (page?.records ?? []).map(normalizeApp)
    total.value = Number(page?.totalRow ?? 0)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取我的应用失败')
  } finally {
    loading.value = false
  }
}

async function searchApps() {
  query.pageNum = 1
  await loadApps()
}

async function changePage(page: number) {
  query.pageNum = page
  await loadApps()
}

/**
 * 删除成功后重新请求当前用户的列表。
 * 当本页最后一个应用被删除时先回到上一页，保证分页结果不会留下空白页。
 */
async function removeApp(app: AppView) {
  if (!app.id || deletingAppId.value) return
  deletingAppId.value = app.id
  try {
    const response = await deleteApp({ id: toApiId(app.id) })
    if (!response.data.data) return
    message.success('应用已删除')
    if (apps.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await loadApps()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试')
  } finally {
    deletingAppId.value = ''
  }
}

/** 提交删除前展示应用名称，减少误删。 */
function confirmDelete(app: AppView) {
  Modal.confirm({
    title: `删除“${app.appName || '未命名应用'}”？`,
    content: '删除后，该应用和相关对话将无法继续访问。',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => removeApp(app),
  })
}

onMounted(loadApps)
</script>

<template>
  <div class="my-apps-page page-width interior-page">
    <div class="page-heading">
      <div>
        <span class="section-kicker">MY APPLICATIONS</span>
        <h1>
          我的应用<span class="heading-count">{{ total }}</span>
        </h1>
        <p>这里仅展示当前账号创建的应用，你可以继续编辑或删除它们。</p>
      </div>
      <a-button type="primary" @click="router.push('/')"> <PlusOutlined /> 创建应用 </a-button>
    </div>

    <form class="app-toolbar" @submit.prevent="searchApps">
      <a-input
        v-model:value="query.appName"
        allow-clear
        placeholder="按名称搜索我的应用"
        @clear="searchApps"
      />
      <a-button html-type="submit" :loading="loading"> <SearchOutlined /> 搜索 </a-button>
      <a-button :disabled="loading" @click="loadApps"> <ReloadOutlined /> 刷新 </a-button>
    </form>

    <Skeleton v-if="loading && !apps.length" active :paragraph="{ rows: 8 }" />
    <div v-else-if="apps.length" class="application-grid">
      <AppCard
        v-for="app in apps"
        :key="app.id"
        :app="app"
        editable
        deletable
        :deleting="deletingAppId === app.id"
        @delete="confirmDelete"
      />
    </div>
    <div v-else class="empty-state">
      <div class="empty-symbol"><FolderOpenOutlined /></div>
      <h2>还没有属于你的应用</h2>
      <p>从一个想法开始，创建后会自动出现在这里。</p>
      <a-button type="primary" size="large" @click="router.push('/')">开始创建 ↗</a-button>
    </div>

    <Pagination
      v-if="total > query.pageSize"
      class="page-pagination"
      :current="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      :show-size-changer="false"
      @change="changePage"
    />
  </div>
</template>

<style scoped>
.my-apps-page {
  min-height: 70vh;
}

.app-toolbar {
  display: flex;
  max-width: 620px;
  margin: -10px 0 32px auto;
  align-items: center;
  gap: 8px;
}

.app-toolbar :deep(.ant-input-affix-wrapper) {
  min-width: 240px;
  flex: 1;
  background: #fbfaf6;
}

.application-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 32px 22px;
}

.page-pagination {
  margin-top: 38px;
  text-align: center;
}

@media (max-width: 900px) {
  .application-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .app-toolbar {
    width: 100%;
    margin: 0 0 28px;
  }

  .app-toolbar :deep(.ant-input-affix-wrapper) {
    min-width: 0;
  }

  .application-grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}
</style>
