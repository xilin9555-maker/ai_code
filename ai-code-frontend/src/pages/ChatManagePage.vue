<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  Divider,
  message,
  Select,
  Table,
  type TableColumnsType,
  type TablePaginationConfig,
} from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { formatAppDate, toApiId } from '@/utils/app'

/**
 * 雪花 ID 在浏览器中统一保留为字符串，防止超过 JavaScript 安全整数范围后丢失精度。
 */
type ChatHistoryView = Omit<API.ChatHistory, 'id' | 'appId' | 'userId'> & {
  id?: string
  appId?: string
  userId?: string
}

const router = useRouter()
const histories = ref<ChatHistoryView[]>([])
const total = ref(0)
const loading = ref(false)

const columns: TableColumnsType<ChatHistoryView> = [
  { title: 'ID', dataIndex: 'id', width: 190, fixed: 'left' },
  { title: '消息内容', dataIndex: 'message', width: 380 },
  { title: '发送方', dataIndex: 'messageType', width: 105 },
  { title: '应用 ID', dataIndex: 'appId', width: 190 },
  { title: '用户 ID', dataIndex: 'userId', width: 190 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 105, fixed: 'right' },
]

/** 搜索输入保留为字符串，只有发请求时才适配当前 OpenAPI 生成的 number 类型。 */
const searchForm = reactive({
  id: '',
  message: '',
  messageType: undefined as string | undefined,
  appId: '',
  userId: '',
})

const messageTypeOptions = [
  { label: '用户消息', value: 'user' },
  { label: 'AI 消息', value: 'ai' },
]

const pageQuery = reactive({ pageNum: 1, pageSize: 10 })
const pagination = computed<TablePaginationConfig>(() => ({
  current: pageQuery.pageNum,
  pageSize: pageQuery.pageSize,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (count) => `共 ${count} 条消息`,
}))

/** 把服务端实体中的 Long 字段转换为字符串，表格展示和路由跳转都使用精确值。 */
function normalizeHistory(history: API.ChatHistory): ChatHistoryView {
  return {
    ...history,
    id: history.id == null ? undefined : String(history.id),
    appId: history.appId == null ? undefined : String(history.appId),
    userId: history.userId == null ? undefined : String(history.userId),
  }
}

/**
 * 表格只展示消息摘要，避免一整段生成代码撑高行高并拖慢管理页渲染。
 * 原始内容仍保留在 histories 中，后续增加详情弹窗时无需重新查询。
 */
function getMessageSummary(content?: string) {
  if (!content) return '—'
  const normalized = content.replace(/\s+/g, ' ').trim()
  return normalized.length > 140 ? `${normalized.slice(0, 140)}…` : normalized
}

/** 根据当前搜索与分页状态查询全站对话历史，该接口只能由管理员访问。 */
async function loadHistories() {
  if (loading.value) return
  loading.value = true
  try {
    const query: API.ChatHistoryQueryRequest = {
      ...pageQuery,
      message: searchForm.message.trim() || undefined,
      messageType: searchForm.messageType,
      sortField: 'createTime',
      sortOrder: 'descend',
    }
    if (searchForm.id.trim()) query.id = toApiId(searchForm.id.trim())
    if (searchForm.appId.trim()) query.appId = toApiId(searchForm.appId.trim())
    if (searchForm.userId.trim()) query.userId = toApiId(searchForm.userId.trim())

    const response = await listAllChatHistoryByPageForAdmin(query)
    const page = response.data.data
    histories.value = (page?.records ?? []).map(normalizeHistory)
    total.value = Number(page?.totalRow ?? 0)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取对话历史失败')
  } finally {
    loading.value = false
  }
}

/** 新的搜索条件从第一页开始，避免旧页码超过筛选后的总页数。 */
async function handleSearch() {
  pageQuery.pageNum = 1
  await loadHistories()
}

/** 清空全部筛选条件并重新读取第一页。 */
async function handleReset() {
  Object.assign(searchForm, {
    id: '',
    message: '',
    messageType: undefined,
    appId: '',
    userId: '',
  })
  pageQuery.pageNum = 1
  await loadHistories()
}

async function handleTableChange(page: TablePaginationConfig) {
  pageQuery.pageNum = page.current ?? 1
  pageQuery.pageSize = page.pageSize ?? 10
  await loadHistories()
}

/** 从历史记录直接进入所属应用，应用工作台会继续按照当前管理员身份读取完整对话。 */
function viewAppChat(appId?: string) {
  if (appId) void router.push(`/app/chat/${appId}`)
}

onMounted(loadHistories)
</script>

<template>
  <div class="chat-manage-page page-width interior-page">
    <header class="manage-heading">
      <div>
        <span class="section-kicker">ADMIN CONSOLE</span>
        <h1>对话管理</h1>
        <p>按消息内容、发送方、应用和用户检索生成记录，查看平台近期的对话活动。</p>
      </div>
      <div class="result-count">
        <strong>{{ total }}</strong>
        <span>当前结果</span>
      </div>
    </header>

    <section class="manage-panel" aria-label="对话历史查询列表">
      <a-form class="search-form" layout="inline" :model="searchForm" @finish="handleSearch">
        <a-form-item label="消息 ID">
          <a-input v-model:value="searchForm.id" allow-clear placeholder="输入完整 ID" />
        </a-form-item>
        <a-form-item label="消息内容">
          <a-input v-model:value="searchForm.message" allow-clear placeholder="输入内容关键词" />
        </a-form-item>
        <a-form-item label="发送方">
          <Select
            v-model:value="searchForm.messageType"
            allow-clear
            :options="messageTypeOptions"
            placeholder="全部类型"
          />
        </a-form-item>
        <a-form-item label="应用 ID">
          <a-input v-model:value="searchForm.appId" allow-clear placeholder="输入完整应用 ID" />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input v-model:value="searchForm.userId" allow-clear placeholder="输入完整用户 ID" />
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
        :data-source="histories"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1240 }"
        @change="handleTableChange"
      >
        <template #emptyText>
          <div class="empty-copy">没有找到符合当前条件的对话记录</div>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'message'">
            <div class="message-summary" :title="getMessageSummary(record.message)">
              {{ getMessageSummary(record.message) }}
            </div>
          </template>

          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
              {{ record.messageType === 'user' ? '用户' : 'AI' }}
            </a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'createTime'">
            <time class="create-time">{{ formatAppDate(record.createTime) }}</time>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="viewAppChat(record.appId)">
              <EyeOutlined /> 查看对话
            </a-button>
          </template>
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.chat-manage-page {
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
.search-form :deep(.ant-select) {
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
  vertical-align: middle;
}

.message-summary {
  display: -webkit-box;
  max-width: 350px;
  overflow: hidden;
  line-height: 1.6;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
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
  .search-form :deep(.ant-select) {
    width: 100%;
  }
}
</style>
