<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { EditOutlined, ReloadOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons-vue'
import {
  Divider,
  message,
  Modal,
  Select,
  Table,
  type FormInstance,
  type TableColumnsType,
  type TablePaginationConfig,
} from 'ant-design-vue'
import { deleteUser, listUserVoByPage, updateUser } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()

/**
 * 描述用户表格需要展示的字段。
 * dataIndex 用于读取普通字段，action 列通过插槽渲染编辑和删除按钮。
 */
const columns: TableColumnsType<API.UserVO> = [
  { title: 'ID', dataIndex: 'id', width: 190, fixed: 'left' },
  { title: '用户', dataIndex: 'userAvatar', width: 210 },
  { title: '账号', dataIndex: 'userAccount', width: 160 },
  { title: '简介', dataIndex: 'userProfile', width: 240 },
  { title: '角色', dataIndex: 'userRole', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
]

/** 当前页的用户数据，由分页查询接口返回。 */
const users = ref<API.UserVO[]>([])

/** 数据库中符合当前搜索条件的用户总数。 */
const total = ref(0)

/** 请求用户列表时显示表格加载状态，避免用户误以为页面没有响应。 */
const loading = ref(false)

/** 记录正在删除的用户 ID，只让对应行的按钮显示加载状态。 */
const deletingId = ref<string>()

/** 编辑弹窗的显示状态。 */
const editOpen = ref(false)

/** 保存更新请求时显示加载状态，同时防止用户重复提交。 */
const updating = ref(false)

/** 编辑表单实例用于提交前执行统一校验。 */
const editFormRef = ref<FormInstance>()

/**
 * 记录正在编辑的账号，仅用于在弹窗中帮助管理员确认当前操作对象。
 * 账号本身是登录凭据，不通过普通资料编辑接口修改。
 */
const editingAccount = ref('')

/**
 * 编辑表单的数据结构与后端 UserUpdateRequest 保持一致。
 * 每次打开弹窗都会从表格当前行重新赋值，避免上一次编辑留下的数据影响下一位用户。
 */
const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

/** 角色下拉框只提供服务端支持的两个稳定值。 */
const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

/**
 * 集中保存分页和搜索参数。
 * 字段名称与后端 UserQueryRequest 对应，可以直接作为请求体提交。
 */
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
})

/**
 * 根据当前查询状态生成表格分页配置。
 * 使用计算属性后，页码、每页数量和总数变化时，分页器会自动刷新。
 */
const pagination = computed<TablePaginationConfig>(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (count) => `共 ${count} 位用户`,
}))

/**
 * 分页查询符合当前条件的用户，并更新表格数据和总数。
 * 查询失败时保留当前搜索条件，管理员可以直接重试，无需重新输入。
 */
async function fetchUsers() {
  if (loading.value) return
  loading.value = true

  try {
    const response = await listUserVoByPage({ ...searchParams })
    if (response.data.code === 0 && response.data.data) {
      users.value = response.data.data.records ?? []
      // 后端会把 Long 转成字符串来保护精度，分页组件需要显式转换为数字总数。
      total.value = Number(response.data.data.totalRow ?? 0)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取用户列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/**
 * 按账号和昵称执行搜索。
 * 每次提交新条件都回到第一页，避免旧页码超过新结果的总页数。
 */
async function handleSearch() {
  searchParams.pageNum = 1
  await fetchUsers()
}

/**
 * 清空账号和昵称条件并重新加载第一页，恢复查看全部用户。
 */
async function handleReset() {
  searchParams.userAccount = ''
  searchParams.userName = ''
  searchParams.pageNum = 1
  await fetchUsers()
}

/**
 * 响应表格分页器的页码或每页数量变化，然后按照新分页条件重新查询。
 *
 * @param page Ant Design Vue 表格提供的最新分页信息
 */
async function handleTableChange(page: TablePaginationConfig) {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  await fetchUsers()
}

/**
 * 删除指定用户并刷新当前列表。
 * 当前管理员账号不能在这里删除；若删除的是某页最后一条数据，则自动回到上一页。
 *
 * @param id 需要删除的用户 ID
 */
async function handleDelete(id?: number) {
  if (id == null) return
  if (String(id) === String(loginUserStore.loginUser.id)) {
    message.warning('不能删除当前登录账号')
    return
  }

  deletingId.value = String(id)
  try {
    const response = await deleteUser({ id })
    if (response.data.code === 0 && response.data.data) {
      message.success('用户已删除')
      if (users.value.length === 1 && (searchParams.pageNum ?? 1) > 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await fetchUsers()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除用户失败，请稍后重试')
  } finally {
    deletingId.value = undefined
  }
}

/**
 * 使用选中行的最新数据初始化编辑表单并打开弹窗。
 *
 * @param user 表格中准备编辑的用户记录
 */
function openEditModal(user: API.UserVO) {
  editingAccount.value = user.userAccount ?? ''
  Object.assign(editForm, {
    id: user.id,
    userName: user.userName ?? '',
    userAvatar: user.userAvatar ?? '',
    userProfile: user.userProfile ?? '',
    userRole: user.userRole ?? 'user',
  })
  editOpen.value = true
}

/**
 * 关闭编辑弹窗。保存过程中暂时禁止关闭，避免请求已经发出但界面看起来像被取消。
 */
function closeEditModal() {
  if (!updating.value) {
    editOpen.value = false
  }
}

/**
 * 校验并提交用户资料，然后刷新表格中的最新数据。
 * 如果管理员编辑的是自己，还会重新读取当前登录用户，让页头昵称和头像立即更新。
 */
async function handleUpdate() {
  if (updating.value || editForm.id == null) return

  try {
    await editFormRef.value?.validate()
    updating.value = true
    const response = await updateUser({ ...editForm })
    if (response.data.code === 0 && response.data.data) {
      const editedCurrentUser = isCurrentUser(editForm.id)
      message.success('用户信息已更新')
      editOpen.value = false
      await fetchUsers()
      if (editedCurrentUser) {
        await loginUserStore.fetchLoginUser()
      }
    }
  } catch (error) {
    // 表单校验失败时，组件已经在具体字段下给出原因，不再重复弹出笼统提示。
    if (typeof error === 'object' && error !== null && 'errorFields' in error) return
    message.error(error instanceof Error ? error.message : '更新用户信息失败，请稍后重试')
  } finally {
    updating.value = false
  }
}

/**
 * 将服务端时间转换为便于阅读的本地日期时间。
 * 接口未提供时间或时间格式无法识别时显示占位符，避免表格出现 Invalid Date。
 *
 * @param value 服务端返回的创建时间
 */
function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

/** 判断一行数据是否为当前登录的管理员账号。 */
function isCurrentUser(id?: number) {
  return id != null && String(id) === String(loginUserStore.loginUser.id)
}

// 页面首次挂载时自动加载第一页用户，不需要管理员手动点击搜索。
onMounted(() => {
  void fetchUsers()
})
</script>

<template>
  <div class="user-manage-page page-width interior-page">
    <header class="manage-heading">
      <div>
        <span class="section-kicker">ADMIN CONSOLE</span>
        <h1>用户管理</h1>
        <p>查看平台注册用户，按账号或昵称快速定位，并处理不应继续保留的账号。</p>
      </div>
      <div class="user-count">
        <strong>{{ total }}</strong>
        <span>当前结果</span>
      </div>
    </header>

    <section class="manage-panel" aria-label="用户查询和列表">
      <a-form class="search-form" layout="inline" :model="searchParams" @finish="handleSearch">
        <a-form-item label="账号">
          <a-input
            v-model:value="searchParams.userAccount"
            allow-clear
            placeholder="输入账号关键词"
          />
        </a-form-item>
        <a-form-item label="用户昵称">
          <a-input v-model:value="searchParams.userName" allow-clear placeholder="输入昵称关键词" />
        </a-form-item>
        <a-form-item class="search-actions">
          <a-button type="primary" html-type="submit" :loading="loading">
            <template #icon><SearchOutlined /></template>
            搜索
          </a-button>
          <a-button :disabled="loading" @click="handleReset">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-form-item>
      </a-form>

      <Divider />

      <Table
        row-key="id"
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1190 }"
        @change="handleTableChange"
      >
        <template #emptyText>
          <div class="empty-copy">没有找到符合当前条件的用户</div>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <div class="user-identity">
              <a-avatar :src="record.userAvatar" :size="38">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div>
                <strong>{{ record.userName || '未设置昵称' }}</strong>
                <small v-if="isCurrentUser(record.id)">当前账号</small>
              </div>
            </div>
          </template>

          <template v-else-if="column.dataIndex === 'userProfile'">
            <span class="profile-text">{{ record.userProfile || '暂未填写' }}</span>
          </template>

          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'createTime'">
            <time class="create-time">{{ formatDate(record.createTime) }}</time>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" @click="openEditModal(record)">
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <a-popconfirm
                title="确定删除这个用户吗？"
                description="该记录会从数据库永久删除，操作无法撤销。"
                ok-text="删除"
                cancel-text="取消"
                :disabled="isCurrentUser(record.id)"
                @confirm="handleDelete(record.id)"
              >
                <a-button
                  danger
                  type="link"
                  :disabled="isCurrentUser(record.id)"
                  :loading="deletingId === String(record.id)"
                >
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </Table>
    </section>

    <Modal
      :open="editOpen"
      title="编辑用户信息"
      ok-text="保存修改"
      cancel-text="取消"
      :confirm-loading="updating"
      :mask-closable="!updating"
      @ok="handleUpdate"
      @cancel="closeEditModal"
    >
      <a-form ref="editFormRef" :model="editForm" layout="vertical" class="edit-form">
        <a-form-item label="用户账号">
          <a-input :value="editingAccount" disabled />
          <span class="field-hint">账号用于登录，不能在资料编辑中修改。</span>
        </a-form-item>

        <a-form-item
          label="用户昵称"
          name="userName"
          :rules="[
            { required: true, whitespace: true, message: '请输入用户昵称' },
            { max: 256, message: '用户昵称不能超过 256 个字符' },
          ]"
        >
          <a-input v-model:value="editForm.userName" allow-clear placeholder="请输入用户昵称" />
        </a-form-item>

        <a-form-item
          label="头像地址"
          name="userAvatar"
          :rules="[{ max: 1024, message: '头像地址不能超过 1024 个字符' }]"
        >
          <a-input
            v-model:value="editForm.userAvatar"
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
            v-model:value="editForm.userProfile"
            :auto-size="{ minRows: 3, maxRows: 6 }"
            :maxlength="512"
            show-count
            placeholder="简单介绍一下这位用户"
          />
        </a-form-item>

        <a-form-item
          label="用户角色"
          name="userRole"
          :rules="[{ required: true, message: '请选择用户角色' }]"
        >
          <Select
            v-model:value="editForm.userRole"
            :options="roleOptions"
            :disabled="isCurrentUser(editForm.id)"
          />
          <span v-if="isCurrentUser(editForm.id)" class="field-hint">
            当前登录账号不能在这里取消自己的管理员权限。
          </span>
        </a-form-item>
      </a-form>
    </Modal>
  </div>
</template>

<style scoped>
.user-manage-page {
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
  line-height: 1.2;
  letter-spacing: -1.5px;
  font-weight: 680;
}

.manage-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.user-count {
  min-width: 110px;
  padding-left: 22px;
  border-left: 1px solid var(--line);
  text-align: right;
}

.user-count strong,
.user-count span {
  display: block;
}

.user-count strong {
  font-family: Georgia, serif;
  font-size: 34px;
  font-weight: 500;
}

.user-count span {
  margin-top: 3px;
  color: #929388;
  font-size: 10px;
  letter-spacing: 1px;
}

.manage-panel {
  padding: 27px;
  background: #fffefb;
  border: 1px solid var(--line);
  border-radius: 15px;
  box-shadow: 0 14px 40px #4d46310a;
}

.search-form {
  row-gap: 15px;
}

.search-form :deep(.ant-form-item) {
  margin-bottom: 0;
}

.search-form :deep(.ant-input) {
  min-width: 210px;
  background: #fbfaf6;
}

.search-actions :deep(.ant-form-item-control-input-content) {
  display: flex;
  gap: 9px;
}

.manage-panel :deep(.ant-divider) {
  border-color: var(--line);
  margin: 24px 0;
}

.manage-panel :deep(.ant-table) {
  background: transparent;
}

.manage-panel :deep(.ant-table-thead > tr > th) {
  background: #f2f0e9;
  color: #65665e;
  font-size: 11px;
  letter-spacing: 0.4px;
}

.manage-panel :deep(.ant-table-tbody > tr > td) {
  color: #55564f;
  font-size: 12px;
}

.user-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.user-identity strong,
.user-identity small {
  display: block;
}

.user-identity strong {
  max-width: 135px;
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-identity small {
  margin-top: 3px;
  color: var(--accent);
  font-size: 9px;
}

.profile-text {
  display: -webkit-box;
  overflow: hidden;
  color: #77786f;
  line-height: 1.6;
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
  font-size: 12px;
}

.edit-form {
  padding-top: 14px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: #929388;
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 700px) {
  .manage-heading {
    align-items: flex-start;
  }

  .user-count {
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

  .search-form :deep(.ant-input) {
    width: 100%;
    min-width: 0;
  }
}
</style>
