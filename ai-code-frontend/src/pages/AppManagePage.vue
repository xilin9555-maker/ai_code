<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  EditOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons-vue'
import {
  Divider,
  InputNumber,
  message,
  Select,
  Table,
  type TableColumnsType,
  type TablePaginationConfig,
} from 'ant-design-vue'
import { deleteAppByAdmin, listAppVoByPageByAdmin, updateAppByAdmin } from '@/api/appController'
import { CODE_GEN_TYPE_OPTIONS, getCodeGenTypeLabel } from '@/constants/codeGenType'
import { formatAppDate, normalizeApp, toApiId, type AppView } from '@/utils/app'

const GOOD_APP_PRIORITY = 99
const router = useRouter()
const apps = ref<AppView[]>([])
const total = ref(0)
const loading = ref(false)
const deletingId = ref<string>()
const updatingPriorityId = ref<string>()

const columns: TableColumnsType<AppView> = [
  { title: 'ID', dataIndex: 'id', width: 190, fixed: 'left' },
  { title: '应用', dataIndex: 'appName', width: 220 },
  { title: '创建者', dataIndex: 'user', width: 150 },
  { title: '生成方式', dataIndex: 'codeGenType', width: 120 },
  { title: '状态', dataIndex: 'deployKey', width: 100 },
  { title: '优先级', dataIndex: 'priority', width: 90 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 285, fixed: 'right' },
]

/** 搜索表单单独保留字符串 ID，防止输入雪花 ID 时被 JavaScript 转换并损失精度。 */
const searchForm = reactive({
  id: '',
  appName: '',
  cover: '',
  initPrompt: '',
  codeGenType: undefined as string | undefined,
  deployKey: '',
  priority: undefined as number | undefined,
  userId: '',
})

const pageQuery = reactive({ pageNum: 1, pageSize: 10 })
const pagination = computed<TablePaginationConfig>(() => ({
  current: pageQuery.pageNum,
  pageSize: pageQuery.pageSize,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (count) => `共 ${count} 个应用`,
}))

/**
 * 按当前分页和搜索条件读取全部应用。
 * 查询结果先做 ID 归一化，后续跳转、删除和编辑都使用未损失精度的字符串值。
 */
async function loadApps() {
  if (loading.value) return
  loading.value = true
  try {
    const query: API.AppQueryRequest = {
      ...pageQuery,
      appName: searchForm.appName.trim() || undefined,
      cover: searchForm.cover.trim() || undefined,
      initPrompt: searchForm.initPrompt.trim() || undefined,
      codeGenType: searchForm.codeGenType,
      deployKey: searchForm.deployKey.trim() || undefined,
      priority: searchForm.priority,
      sortField: 'createTime',
      sortOrder: 'descend',
    }
    if (searchForm.id.trim()) query.id = toApiId(searchForm.id.trim())
    if (searchForm.userId.trim()) query.userId = toApiId(searchForm.userId.trim())

    const response = await listAppVoByPageByAdmin(query)
    const page = response.data.data
    apps.value = (page?.records ?? []).map(normalizeApp)
    total.value = Number(page?.totalRow ?? 0)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取应用列表失败')
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  pageQuery.pageNum = 1
  await loadApps()
}

async function handleReset() {
  Object.assign(searchForm, {
    id: '',
    appName: '',
    cover: '',
    initPrompt: '',
    codeGenType: undefined,
    deployKey: '',
    priority: undefined,
    userId: '',
  })
  pageQuery.pageNum = 1
  await loadApps()
}

async function handleTableChange(page: TablePaginationConfig) {
  pageQuery.pageNum = page.current ?? 1
  pageQuery.pageSize = page.pageSize ?? 10
  await loadApps()
}

/** 删除后修正可能已经为空的末页，再重新读取服务端数据。 */
async function handleDelete(id?: string) {
  if (!id || deletingId.value) return
  deletingId.value = id
  try {
    const response = await deleteAppByAdmin({ id: toApiId(id) })
    if (response.data.data) {
      message.success('应用已删除')
      if (apps.value.length === 1 && pageQuery.pageNum > 1) pageQuery.pageNum -= 1
      await loadApps()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除应用失败')
  } finally {
    deletingId.value = undefined
  }
}

/**
 * 在普通优先级与精选优先级之间切换。
 * 精选判断最终由后端固定的 99 条件完成，前端只负责提交管理员的明确操作。
 */
async function toggleFeatured(app: AppView) {
  if (!app.id || updatingPriorityId.value) return
  updatingPriorityId.value = app.id
  const isFeatured = app.priority === GOOD_APP_PRIORITY
  try {
    const response = await updateAppByAdmin({
      id: toApiId(app.id),
      priority: isFeatured ? 0 : GOOD_APP_PRIORITY,
    })
    if (response.data.data) {
      message.success(isFeatured ? '已取消精选' : '已设为精选')
      await loadApps()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新精选状态失败')
  } finally {
    updatingPriorityId.value = undefined
  }
}

onMounted(loadApps)
</script>

<template>
  <div class="app-manage-page page-width interior-page">
    <header class="manage-heading">
      <div>
        <span class="section-kicker">ADMIN CONSOLE</span>
        <h1>应用管理</h1>
        <p>检索平台应用，维护展示信息，并决定哪些作品进入首页精选区域。</p>
      </div>
      <div class="result-count">
        <strong>{{ total }}</strong>
        <span>当前结果</span>
      </div>
    </header>

    <section class="manage-panel" aria-label="应用查询和管理列表">
      <a-form class="search-form" layout="inline" :model="searchForm" @finish="handleSearch">
        <a-form-item label="应用 ID">
          <a-input v-model:value="searchForm.id" allow-clear placeholder="输入完整 ID" />
        </a-form-item>
        <a-form-item label="应用名称">
          <a-input v-model:value="searchForm.appName" allow-clear placeholder="输入名称关键词" />
        </a-form-item>
        <a-form-item label="生成方式">
          <Select
            v-model:value="searchForm.codeGenType"
            allow-clear
            :options="CODE_GEN_TYPE_OPTIONS"
            placeholder="全部方式"
          />
        </a-form-item>
        <a-form-item label="创建者 ID">
          <a-input v-model:value="searchForm.userId" allow-clear placeholder="输入完整用户 ID" />
        </a-form-item>
        <a-form-item label="初始需求">
          <a-input v-model:value="searchForm.initPrompt" allow-clear placeholder="输入需求关键词" />
        </a-form-item>
        <a-form-item label="封面地址">
          <a-input v-model:value="searchForm.cover" allow-clear placeholder="输入封面关键词" />
        </a-form-item>
        <a-form-item label="部署标识">
          <a-input v-model:value="searchForm.deployKey" allow-clear placeholder="输入 deployKey" />
        </a-form-item>
        <a-form-item label="优先级">
          <InputNumber
            v-model:value="searchForm.priority"
            :min="0"
            allow-clear
            placeholder="全部优先级"
          />
        </a-form-item>
        <a-form-item class="search-actions">
          <a-button type="primary" html-type="submit" :loading="loading">
            <SearchOutlined /> 搜索
          </a-button>
          <a-button :disabled="loading" @click="handleReset"> <ReloadOutlined /> 重置 </a-button>
        </a-form-item>
      </a-form>

      <Divider />

      <Table
        row-key="id"
        :columns="columns"
        :data-source="apps"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1310 }"
        @change="handleTableChange"
      >
        <template #emptyText>
          <div class="empty-copy">没有找到符合当前条件的应用</div>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'appName'">
            <div class="app-identity">
              <span
                class="mini-cover"
                :style="record.cover ? { backgroundImage: `url(${record.cover})` } : undefined"
                >✦</span
              >
              <div>
                <strong>{{ record.appName || '未命名应用' }}</strong>
                <small>{{ record.initPrompt || '暂无初始化需求' }}</small>
              </div>
            </div>
          </template>

          <template v-else-if="column.dataIndex === 'user'">
            <span>{{ record.user?.userName || record.user?.userAccount || '—' }}</span>
          </template>

          <template v-else-if="column.dataIndex === 'codeGenType'">
            <a-tag>{{ getCodeGenTypeLabel(record.codeGenType) }}</a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'deployKey'">
            <a-tag :color="record.deployKey ? 'green' : 'default'">
              {{ record.deployKey ? '已部署' : '未部署' }}
            </a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'priority'">
            <strong :class="{ featured: record.priority === GOOD_APP_PRIORITY }">
              {{ record.priority ?? 0 }}
            </strong>
          </template>

          <template v-else-if="column.dataIndex === 'createTime'">
            <time class="create-time">{{ formatAppDate(record.createTime) }}</time>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space :size="1">
              <a-button type="link" @click="router.push(`/app/chat/${record.id}`)">
                <EyeOutlined /> 查看
              </a-button>
              <a-button type="link" @click="router.push(`/app/edit/${record.id}`)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button
                type="link"
                :loading="updatingPriorityId === record.id"
                @click="toggleFeatured(record)"
              >
                <StarFilled v-if="record.priority === GOOD_APP_PRIORITY" />
                <StarOutlined v-else />
                {{ record.priority === GOOD_APP_PRIORITY ? '取消精选' : '设为精选' }}
              </a-button>
              <a-popconfirm
                title="确定删除这个应用吗？"
                description="删除后它不会再出现在普通查询中。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" danger :loading="deletingId === record.id">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.app-manage-page {
  padding-top: 54px;
}

.manage-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 36px;
  margin-bottom: 32px;
}

.manage-heading h1 {
  margin: 12px 0 10px;
  font-size: clamp(32px, 4vw, 46px);
  font-weight: 680;
  line-height: 1.2;
  letter-spacing: -1.5px;
}

.manage-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.result-count {
  min-width: 110px;
  padding-left: 22px;
  border-left: 1px solid var(--line);
  text-align: right;
}

.result-count strong,
.result-count span {
  display: block;
}

.result-count strong {
  font-family: Georgia, serif;
  font-size: 34px;
  font-weight: 500;
}

.result-count span {
  color: #929388;
  font-size: 10px;
  letter-spacing: 1px;
}

.manage-panel {
  padding: 27px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fffefb;
  box-shadow: 0 14px 40px #4d46310a;
}

.search-form {
  row-gap: 15px;
}

.search-form :deep(.ant-form-item) {
  margin-bottom: 0;
}

.search-form :deep(.ant-input),
.search-form :deep(.ant-select),
.search-form :deep(.ant-input-number) {
  min-width: 185px;
}

.search-actions :deep(.ant-form-item-control-input-content) {
  display: flex;
  gap: 9px;
}

.manage-panel :deep(.ant-divider) {
  margin: 24px 0;
  border-color: var(--line);
}

.manage-panel :deep(.ant-table-thead > tr > th) {
  color: #65665e;
  background: #f2f0e9;
  font-size: 11px;
}

.manage-panel :deep(.ant-table-tbody > tr > td) {
  color: #55564f;
  font-size: 11px;
}

.app-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.mini-cover {
  display: grid;
  width: 42px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 5px;
  color: #a86f55;
  background: linear-gradient(135deg, #e8e4d7 55%, #d7b29e 55%);
  background-position: center;
  background-size: cover;
}

.app-identity div {
  min-width: 0;
}

.app-identity strong,
.app-identity small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-identity strong {
  max-width: 145px;
  color: var(--ink);
  font-size: 12px;
}

.app-identity small {
  max-width: 145px;
  margin-top: 4px;
  color: #97988e;
  font-size: 9px;
}

.featured {
  color: #ba7b45;
}

.create-time {
  color: #77786f;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.empty-copy {
  padding: 38px 0;
  color: #96978d;
}

@media (max-width: 700px) {
  .manage-heading {
    align-items: flex-start;
  }

  .result-count {
    display: none;
  }

  .manage-panel {
    padding: 20px 16px;
  }

  .search-form {
    display: grid;
    grid-template-columns: 1fr;
  }

  .search-form :deep(.ant-form-item) {
    display: block;
  }

  .search-form :deep(.ant-input),
  .search-form :deep(.ant-select),
  .search-form :deep(.ant-input-number) {
    width: 100%;
  }
}
</style>
