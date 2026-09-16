<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExpandOutlined,
  InfoCircleOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { Alert, message as notification, Modal, Spin } from 'ant-design-vue'
import 'highlight.js/styles/github-dark.css'
import aiAvatar from '@/assets/ai-avatar.png'
import { deleteApp, deleteAppByAdmin, deployApp, getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import { getApiBaseUrl, getDeployUrl, getStaticPreviewUrl } from '@/config/env'
import { getCodeGenTypeLabel } from '@/constants/codeGenType'
import { useLoginUserStore } from '@/stores/loginUser'
import { renderMarkdown } from '@/utils/markdown'
import { normalizeApp, toApiId, type AppView } from '@/utils/app'
import { loadAppAgentMode, saveAppAgentMode } from '@/utils/generationMode'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  createTime?: string
  /** 流式接收期间保持轻量文本渲染，完整接收后再执行 Markdown 解析和代码高亮。 */
  streaming?: boolean
}

type BuildProgressPayload = {
  event: 'build_start' | 'build_progress' | 'build_complete'
  stage: string
  status: 'running' | 'success' | 'failed'
  percent: number
  message: string
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const app = ref<AppView>()
const pageLoading = ref(true)
const generating = ref(false)
const downloading = ref(false)
const deploying = ref(false)
const deploySuccessOpen = ref(false)
const deployedUrl = ref('')
const deleting = ref(false)
const detailsOpen = ref(false)
const userMessage = ref('')
const messages = ref<ChatMessage[]>([])
const loadingHistory = ref(false)
const historyLoaded = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const previewReady = ref(false)
const previewVersion = ref(Date.now())
const previewFrame = ref<HTMLIFrameElement>()
const previewLoaded = ref(false)
const messageList = ref<HTMLElement>()
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo>()
const agentMode = ref(false)
const buildProgress = ref<BuildProgressPayload>()
let eventSource: EventSource | undefined
let streamRenderTimer: number | undefined
let messageSequence = 0

const STREAM_RENDER_INTERVAL = 80
const HISTORY_PAGE_SIZE = 10
const appId = computed(() => String(route.params.id ?? ''))
const currentUserId = computed(() =>
  loginUserStore.loginUser.id == null ? '' : String(loginUserStore.loginUser.id),
)
const isOwner = computed(
  () => Boolean(app.value?.userId) && app.value?.userId === currentUserId.value,
)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const canManage = computed(() => isOwner.value || isAdmin.value)
const canViewHistory = computed(() => isOwner.value || isAdmin.value)
const conversationUserName = computed(() => {
  if (isOwner.value) return '你'
  return app.value?.user?.userName || app.value?.user?.userAccount || '应用创建者'
})
const previewUrl = computed(() =>
  previewReady.value && app.value
    ? getStaticPreviewUrl(app.value.codeGenType, app.value.id, previewVersion.value)
    : '',
)

function formatElementLabel(element: ElementInfo) {
  const id = element.id ? `#${element.id}` : ''
  const classNames = element.className
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .map((className) => `.${className}`)
    .join('')
  return `${element.tagName.toLowerCase()}${id}${classNames}`
}

function formatElementAttributes(element: ElementInfo) {
  return Object.entries(element.attributes)
    .map(([name, value]) => `${name}="${value}"`)
    .join(' · ')
}

const selectedElementLabel = computed(() => {
  const element = selectedElementInfo.value
  return element ? formatElementLabel(element) : ''
})
const selectedElementAttributes = computed(() => {
  const element = selectedElementInfo.value
  return element ? formatElementAttributes(element) : ''
})
const composerPlaceholder = computed(() => {
  if (!isOwner.value) return '只有应用创建者可以继续生成'
  if (selectedElementInfo.value) return `描述你想如何修改 ${selectedElementLabel.value}`
  return '请描述你想生成的网站，越详细效果越好哦'
})

const generationModeDescription = computed(() =>
  agentMode.value
    ? '先收集素材并检查代码质量，适合完整生成和较大改动'
    : '直接生成或修改代码，适合日常快速调整',
)

/** 按应用恢复用户上次选择的生成模式，切换模式不会清空共享对话。 */
function restoreGenerationMode() {
  agentMode.value = loadAppAgentMode(appId.value)
}

/** 保存当前应用的模式偏好，下一次进入工作台时继续沿用。 */
function handleAgentModeChange(value: boolean | string | number) {
  const enabled = value === true
  agentMode.value = enabled
  saveAppAgentMode(appId.value, enabled)
}

const visualEditor = new VisualEditor({
  onElementSelected(elementInfo) {
    selectedElementInfo.value = elementInfo
  },
})

/** 为刚发送、尚未取得数据库 id 的消息生成只在当前页面使用的稳定 key。 */
function createLocalMessageId() {
  return `local-${++messageSequence}`
}

/** 把网页定位信息作为纯上下文追加到用户需求后，帮助 AI 缩小修改范围。 */
function appendElementContext(content: string, elementInfo?: ElementInfo) {
  if (!elementInfo) return content

  const normalizeLine = (value: string, maxLength: number) =>
    value.replace(/\s+/g, ' ').trim().slice(0, maxLength)
  const lines = [
    '',
    '',
    '[可视化选中元素，仅用于定位要修改的区域]',
    `- 元素：${normalizeLine(formatElementLabel(elementInfo), 300)}`,
    `- CSS 选择器：${normalizeLine(elementInfo.selector, 800)}`,
    `- 选择器匹配数量：${elementInfo.selectorMatchCount}`,
  ]
  if (elementInfo.pagePath) {
    lines.push(`- 页面路径：${normalizeLine(elementInfo.pagePath, 400)}`)
  }
  if (elementInfo.textContent) {
    lines.push(`- 当前内容：${normalizeLine(elementInfo.textContent, 200)}`)
  }
  const attributes = formatElementAttributes(elementInfo)
  if (attributes) {
    lines.push(`- 语义属性：${normalizeLine(attributes, 500)}`)
  }
  if (elementInfo.htmlSnippet) {
    lines.push(`- HTML 摘要：${normalizeLine(elementInfo.htmlSnippet, 800)}`)
  }
  lines.push('[选中元素信息结束]')
  return content + lines.join('\n')
}

/** 清空父子页面中的选择状态；发送完成后同时退出编辑模式。 */
function finishVisualEditing() {
  selectedElementInfo.value = undefined
  visualEditor.clearSelection()
  if (isEditMode.value) {
    visualEditor.disableEditMode()
    isEditMode.value = false
  }
}

function clearSelectedElement() {
  selectedElementInfo.value = undefined
  visualEditor.clearSelection()
}

function toggleEditMode() {
  if (isEditMode.value) {
    visualEditor.disableEditMode()
    isEditMode.value = false
    return
  }
  if (!previewFrame.value || !previewLoaded.value) {
    notification.warning('请等待网站预览加载完成')
    return
  }

  visualEditor.init(previewFrame.value)
  const result = visualEditor.enableEditMode()
  if (result.ok) {
    isEditMode.value = true
    notification.info('已进入元素选择模式，点击预览中的内容即可定位')
    return
  }

  const errorMessage =
    result.reason === 'cross-origin'
      ? '预览页面与工作台不同源，无法开启可视化编辑'
      : '可视化编辑初始化失败，请刷新预览后重试'
  notification.error(errorMessage)
}

function handlePreviewLoad() {
  previewLoaded.value = true
  if (!previewFrame.value) return

  visualEditor.init(previewFrame.value)
  const result = visualEditor.onIframeLoad()
  if (isEditMode.value && !result.ok) {
    isEditMode.value = false
    notification.error('预览已刷新，但可视化编辑未能重新初始化')
  }
}

function refreshPreview() {
  clearSelectedElement()
  previewLoaded.value = false
  previewVersion.value = Math.max(Date.now(), previewVersion.value + 1)
}

function handleIframeMessage(event: MessageEvent) {
  visualEditor.handleIframeMessage(event)
}

/**
 * 读取应用基础资料，决定当前用户能否继续生成、编辑或部署。
 * 应用详情本身可公开读取，因此精选作品也能进入该页面查看结果。
 */
async function loadApp() {
  pageLoading.value = true
  try {
    const response = await getAppVoById({ id: toApiId(appId.value) })
    if (response.data.data) {
      app.value = normalizeApp(response.data.data)
    }
  } catch (error) {
    notification.error(error instanceof Error ? error.message : '获取应用信息失败')
    await router.replace('/')
  } finally {
    pageLoading.value = false
  }
}

/** 把数据库消息转换成页面使用的结构，同时保留雪花 ID 的完整字符串。 */
function normalizeHistoryMessage(history: API.ChatHistory, fallbackIndex: number): ChatMessage {
  const id =
    history.id == null
      ? `history-${history.createTime ?? 'unknown'}-${fallbackIndex}`
      : `history-${String(history.id)}`
  return {
    id,
    role: history.messageType === 'user' ? 'user' : 'assistant',
    content: history.message ?? '',
    createTime: history.createTime,
  }
}

/**
 * 加载应用的对话历史。
 *
 * 后端按 createTime 从新到旧返回，所以每一页先 reverse 成从旧到新。继续加载时，再把
 * 更早的一页插到现有数组开头，最终页面顺序始终是“上方旧消息、下方新消息”。
 * 方法会返回请求是否成功，调用方只有在成功确认历史为空后才允许自动发送 initPrompt。
 */
async function loadChatHistory(isLoadMore = false) {
  if (!appId.value || !canViewHistory.value || loadingHistory.value) return false
  if (isLoadMore && !lastCreateTime.value) return false

  loadingHistory.value = true
  const listElement = messageList.value
  const previousScrollHeight = isLoadMore ? (listElement?.scrollHeight ?? 0) : 0
  try {
    const params: API.listAppChatHistoryParams = {
      appId: toApiId(appId.value),
      pageSize: HISTORY_PAGE_SIZE,
      lastCreateTime: isLoadMore ? lastCreateTime.value : undefined,
    }
    const response = await listAppChatHistory(params)
    const records = response.data.data?.records ?? []
    const historyMessages = records.map(normalizeHistoryMessage).reverse()

    // 网络重试时可能收到同一页数据，按数据库消息 id 去重后再合并。
    const existingIds = new Set(messages.value.map((item) => item.id))
    const newMessages = historyMessages.filter((item) => !existingIds.has(item.id))
    if (isLoadMore) {
      messages.value.unshift(...newMessages)
    } else {
      messages.value = newMessages
    }

    // records 仍保持后端的倒序，数组末尾就是本页最早记录，可直接作为下一页游标。
    const oldestRecord = records[records.length - 1]
    lastCreateTime.value = oldestRecord?.createTime
    hasMoreHistory.value = records.length === HISTORY_PAGE_SIZE && Boolean(lastCreateTime.value)
    historyLoaded.value = true

    await nextTick()
    if (isLoadMore && listElement) {
      // 插入旧消息后补偿新增高度，让用户仍停留在原来正在阅读的位置。
      listElement.scrollTop += listElement.scrollHeight - previousScrollHeight
    } else {
      await scrollMessagesToBottom()
    }
    return true
  } catch (error) {
    notification.error(error instanceof Error ? error.message : '加载对话历史失败')
    return false
  } finally {
    loadingHistory.value = false
  }
}

/** 使用当前最早消息的创建时间继续加载上一页。 */
async function loadMoreHistory() {
  if (hasMoreHistory.value) await loadChatHistory(true)
}

/**
 * 读取本轮刚保存的完整 AI 回复。
 *
 * 生成过程中直接展示工具参数增量，让代码持续出现在页面中。多个文件的工具参数可能
 * 交错到达，所以流结束后再读取服务端整理好的完整消息，用结构稳定的 Markdown 替换
 * 临时内容，确保每个文件都有各自闭合的代码块。
 */
async function loadLatestAssistantContent() {
  try {
    const response = await listAppChatHistory({
      appId: toApiId(appId.value),
      pageSize: 5,
    })
    const latestAssistant = response.data.data?.records?.find(
      (record) => record.messageType === 'ai',
    )
    return latestAssistant?.message
  } catch {
    // 刷新失败时继续保留已经收到的流式内容，不能影响本轮生成正常结束。
    return undefined
  }
}

/** 等待 DOM 更新后把消息区滚动到末尾，持续生成时始终展示最新代码。 */
async function scrollMessagesToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

/** 用户主动向上翻看内容后暂停自动跟随，避免流式输出把滚动位置抢回底部。 */
function isMessageListNearBottom() {
  const element = messageList.value
  if (!element) return true
  return element.scrollHeight - element.scrollTop - element.clientHeight < 120
}

/**
 * 使用原生 EventSource 接收服务端的 text/event-stream 响应。
 * 普通消息的 d 字段是一个代码片段。对于 Vue 工程，收到 done 事件代表后端不仅写完
 * 源码，也已经同步生成最新 dist，此时刷新右侧 iframe 不会再读到旧构建产物。
 */
async function generateCode(text = userMessage.value) {
  const content = text.trim()
  if (!content || generating.value || !isOwner.value) return

  const prompt = appendElementContext(content, selectedElementInfo.value)
  // 请求期间禁止切换开关，并固定本轮模式，避免结束回调读取到下一轮的选择。
  const requestUsesAgent = agentMode.value
  userMessage.value = ''
  generating.value = true
  buildProgress.value = undefined
  messages.value.push({ id: createLocalMessageId(), role: 'user', content: prompt })
  finishVisualEditing()
  const assistantMessageIndex = messages.value.length
  messages.value.push({
    id: createLocalMessageId(),
    role: 'assistant',
    content: '',
    streaming: true,
  })
  await scrollMessagesToBottom()

  const params = new URLSearchParams({
    appId: appId.value,
    message: prompt,
    agent: String(requestUsesAgent),
  })
  const streamUrl = new URL(`app/chat/gen/code?${params}`, getApiBaseUrl()).toString()
  let completed = false
  let renderedContent = ''
  let pendingChunks: string[] = []

  /**
   * SSE 可能在很短时间内送来大量小片段。这里先合并片段，再统一更新响应式数据，
   * 避免每个字都触发整段代码的 DOM 排版和滚动，从而保持页面操作流畅。
   */
  const flushPendingChunks = () => {
    if (streamRenderTimer !== undefined) {
      window.clearTimeout(streamRenderTimer)
      streamRenderTimer = undefined
    }
    if (!pendingChunks.length) return

    const shouldFollowOutput = isMessageListNearBottom()
    renderedContent += pendingChunks.join('')
    pendingChunks = []
    const reactiveMessage = messages.value[assistantMessageIndex]
    if (reactiveMessage) reactiveMessage.content = renderedContent
    if (shouldFollowOutput) void scrollMessagesToBottom()
  }

  /** 将多次网络回调压缩为每 80 毫秒最多一次页面刷新。 */
  const scheduleStreamRender = () => {
    if (streamRenderTimer !== undefined) return
    streamRenderTimer = window.setTimeout(flushPendingChunks, STREAM_RENDER_INTERVAL)
  }

  /** 闲聊回复不包含完整 HTML，此时应保留原网站预览而不是假装重新生成成功。 */
  const responseContainsWebsite = () => {
    const normalizedContent = renderedContent.toLowerCase()
    return (
      normalizedContent.includes('<!doctype html') &&
      normalizedContent.includes('<html') &&
      normalizedContent.includes('<body')
    )
  }

  eventSource = new EventSource(streamUrl, { withCredentials: true })

  eventSource.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data) as { d?: string }
      if (typeof payload.d === 'string') {
        pendingChunks.push(payload.d)
        scheduleStreamRender()
      }
    } catch {
      flushPendingChunks()
      const assistantMessage = messages.value[assistantMessageIndex]
      if (assistantMessage) assistantMessage.streaming = false
      closeStream()
      generating.value = false
      notification.error('生成数据格式无法识别，请重新尝试')
    }
  }

  /** 构建阶段使用具名事件传输，不会混入 AI 回复或被写进聊天历史。 */
  const handleBuildProgress = (event: MessageEvent<string>) => {
    try {
      const payload = JSON.parse(event.data) as BuildProgressPayload
      if (
        typeof payload.message === 'string' &&
        typeof payload.percent === 'number' &&
        ['running', 'success', 'failed'].includes(payload.status)
      ) {
        buildProgress.value = {
          ...payload,
          percent: Math.min(100, Math.max(0, payload.percent)),
        }
      }
    } catch {
      // 构建进度只用于辅助展示，单个进度事件异常不应中断代码生成连接。
    }
  }

  eventSource.addEventListener('build_start', handleBuildProgress)
  eventSource.addEventListener('build_progress', handleBuildProgress)
  eventSource.addEventListener('build_complete', handleBuildProgress)

  eventSource.addEventListener('generation_error', (event) => {
    flushPendingChunks()
    let errorMessage = '生成失败，请稍后重试'
    try {
      const payload = JSON.parse(event.data) as { message?: string }
      if (payload.message) errorMessage = payload.message
    } catch {
      // 错误事件无法解析时使用稳定的兜底文案。
    }

    renderedContent += `\n\n${errorMessage}\n\n`
    const assistantMessage = messages.value[assistantMessageIndex]
    if (assistantMessage) {
      assistantMessage.content = renderedContent
      assistantMessage.streaming = false
    }
    if (buildProgress.value && buildProgress.value.status !== 'failed') {
      buildProgress.value = {
        ...buildProgress.value,
        status: 'failed',
        message: errorMessage,
      }
    }
    completed = true
    closeStream()
    generating.value = false
    notification.error(errorMessage)
  })

  eventSource.addEventListener('done', async () => {
    // done 事件可能比定时刷新先到，结束前必须把缓存中的最后几个片段写入页面。
    flushPendingChunks()
    completed = true
    closeStream()

    // 流式阶段优先保证实时显示，结束后再换成服务端整理过的完整代码块。
    const completedContent = await loadLatestAssistantContent()
    if (completedContent) renderedContent = completedContent
    const assistantMessage = messages.value[assistantMessageIndex]
    if (assistantMessage) {
      assistantMessage.content = renderedContent
      assistantMessage.streaming = false
    }
    generating.value = false
    if (
      responseContainsWebsite() ||
      app.value?.codeGenType === 'vue_project'
    ) {
      previewReady.value = true
      refreshPreview()
      notification.success('网站生成完成')
    } else {
      notification.success('回复完成，可以继续对话')
    }
  })

  eventSource.onerror = () => {
    // 网络错误前仍可能收到最后几个片段，先写入页面再结束本轮状态。
    flushPendingChunks()
    const assistantMessage = messages.value[assistantMessageIndex]
    if (assistantMessage) assistantMessage.streaming = false
    closeStream()
    generating.value = false
    if (buildProgress.value && buildProgress.value.status === 'running') {
      buildProgress.value = {
        ...buildProgress.value,
        status: 'failed',
        message: '生成连接已中断',
      }
    }
    if (!completed) notification.error('生成连接中断，请检查后端日志后重试')
  }
}

/** 主动关闭流连接，避免离开页面后浏览器仍继续占用后端连接。 */
function closeStream() {
  if (streamRenderTimer !== undefined) {
    window.clearTimeout(streamRenderTimer)
    streamRenderTimer = undefined
  }
  eventSource?.close()
  eventSource = undefined
}

/**
 * 从下载响应头中读取服务端指定的文件名。
 * 优先识别支持中文的 filename* 写法，同时兼容普通 filename；响应头没有文件名时，
 * 使用应用 ID 组成稳定的兜底名称，确保浏览器保存的文件始终带有 zip 后缀。
 */
function resolveDownloadFileName(contentDisposition: string | null) {
  const encodedFileName = contentDisposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encodedFileName) {
    try {
      return decodeURIComponent(encodedFileName)
    } catch {
      return encodedFileName
    }
  }

  return contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1] || `app-${appId.value}.zip`
}

/**
 * 下载当前应用的完整源代码压缩包。
 * fetch 会携带当前登录会话访问后端，后端校验应用归属并返回 ZIP 二进制数据；前端再把
 * Blob 转成浏览器临时地址，通过隐藏链接触发保存，完成后立即释放临时地址。
 */
async function downloadCode() {
  if (!appId.value) {
    notification.error('应用 ID 不存在')
    return
  }
  if (!isOwner.value || downloading.value) return

  downloading.value = true
  try {
    const downloadEndpoint = new URL(
      `app/download/${encodeURIComponent(appId.value)}`,
      getApiBaseUrl(),
    )
    const response = await fetch(downloadEndpoint, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/zip' },
    })
    const responseContentType = response.headers.get('Content-Type') || ''

    // 业务异常由后端按 JSON 返回，先读取其中的提示，避免把错误内容保存成伪 ZIP 文件。
    if (responseContentType.includes('application/json')) {
      const result = (await response.json()) as { message?: string }
      throw new Error(result.message || `下载失败（HTTP ${response.status}）`)
    }
    if (!response.ok) {
      throw new Error(`下载失败（HTTP ${response.status}）`)
    }

    const fileBlob = await response.blob()
    const objectUrl = URL.createObjectURL(fileBlob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = resolveDownloadFileName(response.headers.get('Content-Disposition'))
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
    notification.success('代码下载成功')
  } catch (error) {
    notification.error(error instanceof Error ? error.message : '下载失败，请稍后重试')
  } finally {
    downloading.value = false
  }
}

/**
 * 部署接口会把已生成文件复制到公开目录，并返回可长期访问的地址。
 * 成功后先展示结果弹窗，让用户自行决定复制链接、访问网站或留在当前工作台。
 */
async function handleDeploy() {
  if (!isOwner.value || deploying.value) return
  deploying.value = true
  try {
    const response = await deployApp({ appId: toApiId(appId.value) })
    const deployUrl = response.data.data
    if (deployUrl) {
      if (app.value) {
        const pathname = new URL(deployUrl, window.location.origin).pathname
        app.value.deployKey = pathname.split('/').filter(Boolean)[0] || app.value.deployKey
      }
      deployedUrl.value = deployUrl
      deploySuccessOpen.value = true
    }
  } catch (error) {
    notification.error(error instanceof Error ? error.message : '部署失败，请稍后重试')
  } finally {
    deploying.value = false
  }
}

function openDeployedWork() {
  const url = getDeployUrl(app.value?.deployKey)
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

/** 删除完成后回到首页，首页会重新读取个人应用列表。 */
async function handleDelete() {
  if (!canManage.value || deleting.value) return
  deleting.value = true
  try {
    const requestBody = { id: toApiId(appId.value) }
    const response = isAdmin.value
      ? await deleteAppByAdmin(requestBody)
      : await deleteApp(requestBody)
    if (response.data.data) {
      notification.success('应用已删除')
      await router.replace('/')
    }
  } catch (error) {
    notification.error(error instanceof Error ? error.message : '删除应用失败')
  } finally {
    deleting.value = false
  }
}

function confirmDelete() {
  Modal.confirm({
    title: '确定删除这个应用吗？',
    content: '删除后它不会再出现在应用列表中。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: handleDelete,
  })
}

onMounted(async () => {
  window.addEventListener('message', handleIframeMessage)
  restoreGenerationMode()
  await loadApp()
  if (!app.value) return

  if (canViewHistory.value) {
    // 历史查询成功后才能判断是否是空会话，查询失败时绝不能重复发送初始化需求。
    const loaded = await loadChatHistory()
    if (!loaded) return

    // 一轮完整对话至少包含一条用户消息和一条 AI 回复，此时生成文件已经可以预览。
    previewReady.value = messages.value.length >= 2
    if (isOwner.value && messages.value.length === 0 && app.value.initPrompt) {
      await generateCode(app.value.initPrompt)
    }
    return
  }

  /*
   * 对话历史只向创建者和管理员开放。其他访客查看公开作品时保留初始化需求作为作品说明，
   * 并直接展示生成结果，不向受保护的历史接口发送一个必然失败的请求。
   */
  historyLoaded.value = true
  previewReady.value = true
  if (app.value.initPrompt) {
    previewReady.value = true
    messages.value.push({
      id: `public-initial-${app.value.id ?? appId.value}`,
      role: 'user',
      content: app.value.initPrompt,
    })
  }
})

onBeforeUnmount(() => {
  closeStream()
  window.removeEventListener('message', handleIframeMessage)
  visualEditor.destroy()
})
</script>

<template>
  <div class="chat-page">
    <Spin :spinning="pageLoading">
      <header class="workbench-header">
        <div class="workbench-title">
          <a-button type="text" aria-label="返回首页" @click="router.push('/')">
            <ArrowLeftOutlined />
          </a-button>
          <div>
            <span class="section-kicker">APPLICATION WORKBENCH</span>
            <div class="app-title-row">
              <h1>{{ app?.appName || '应用工作台' }}</h1>
              <a-tag v-if="app?.codeGenType" color="blue" class="code-gen-type-tag">
                {{ getCodeGenTypeLabel(app.codeGenType) }}
              </a-tag>
            </div>
          </div>
        </div>
        <div class="workbench-actions">
          <a-button v-if="app?.deployKey" @click="openDeployedWork">
            <ExpandOutlined /> 查看已部署版本
          </a-button>
          <a-button
            v-if="isOwner"
            type="primary"
            ghost
            :loading="downloading"
            @click="downloadCode"
          >
            <DownloadOutlined /> 下载代码
          </a-button>
          <a-button v-if="isOwner" type="primary" :loading="deploying" @click="handleDeploy">
            <CloudUploadOutlined /> 部署应用
          </a-button>
          <a-button type="text" @click="detailsOpen = true"><InfoCircleOutlined /> 详情</a-button>
          <a-button v-if="canManage" type="text" @click="router.push(`/app/edit/${appId}`)">
            <EditOutlined /> 编辑
          </a-button>
          <a-button v-if="canManage" type="text" danger :loading="deleting" @click="confirmDelete">
            <DeleteOutlined /> 删除
          </a-button>
        </div>
      </header>

      <main class="workbench-grid">
        <section class="chat-panel" aria-label="应用生成对话">
          <div class="panel-label">
            <span>生成对话</span>
            <small>{{ isOwner ? '和 AI 一起继续修改网站' : '公开作品记录' }}</small>
          </div>

          <div ref="messageList" class="message-list">
            <div v-if="canViewHistory && (hasMoreHistory || loadingHistory)" class="history-loader">
              <a-button
                type="text"
                size="small"
                :loading="loadingHistory"
                :disabled="!hasMoreHistory"
                @click="loadMoreHistory"
              >
                {{ loadingHistory ? '正在加载历史消息' : '加载更早的消息' }}
              </a-button>
            </div>
            <div v-if="historyLoaded && !messages.length" class="chat-empty">
              <span>✳</span>
              <h2>描述一次修改，看看网站如何变化。</h2>
              <p>可以说明页面结构、颜色、内容以及需要的交互。</p>
            </div>
            <article
              v-for="item in messages"
              :key="item.id"
              class="chat-message"
              :class="item.role"
            >
              <div class="message-avatar">
                <UserOutlined v-if="item.role === 'user'" />
                <img v-else :src="aiAvatar" alt="灵构 AI 头像" />
              </div>
              <div>
                <strong>{{ item.role === 'user' ? conversationUserName : '灵构 AI' }}</strong>
                <pre v-if="item.content && item.role === 'user'">{{ item.content }}</pre>
                <pre v-else-if="item.content && item.streaming" class="streaming-output">{{
                  item.content
                }}</pre>
                <div
                  v-else-if="item.content"
                  class="markdown-content"
                  v-html="renderMarkdown(item.content)"
                />
                <p v-else class="typing"><i /><i /><i /> 正在生成代码</p>
              </div>
            </article>
          </div>

          <div
            v-if="buildProgress"
            class="build-progress-card"
            :class="`is-${buildProgress.status}`"
          >
            <div class="build-progress-heading">
              <strong>{{ buildProgress.message }}</strong>
              <span>{{ buildProgress.percent }}%</span>
            </div>
            <a-progress
              :percent="buildProgress.percent"
              :show-info="false"
              :status="
                buildProgress.status === 'failed'
                  ? 'exception'
                  : buildProgress.status === 'success' && buildProgress.percent === 100
                    ? 'success'
                    : 'active'
              "
              size="small"
            />
          </div>

          <form
            class="chat-composer"
            :title="!isOwner ? '无法在别人的作品下对话' : undefined"
            @submit.prevent="generateCode()"
          >
            <div class="generation-mode-row">
              <div>
                <strong>{{ agentMode ? 'AI 工作流模式' : '普通模式' }}</strong>
                <small>{{ generationModeDescription }}</small>
              </div>
              <a-switch
                :checked="agentMode"
                :disabled="!isOwner || generating"
                checked-children="AI 工作流"
                un-checked-children="普通模式"
                @update:checked="handleAgentModeChange"
              />
            </div>
            <Alert
              v-if="selectedElementInfo"
              class="selected-element-alert"
              type="info"
              show-icon
              closable
              @close="clearSelectedElement"
            >
              <template #message>
                <span class="selected-element-title">
                  选中元素：<code>{{ selectedElementLabel }}</code>
                </span>
              </template>
              <template #description>
                <dl class="selected-element-details">
                  <div v-if="selectedElementInfo.textContent">
                    <dt>内容：</dt>
                    <dd>{{ selectedElementInfo.textContent }}</dd>
                  </div>
                  <div>
                    <dt>页面路径：</dt>
                    <dd>
                      <code>{{ selectedElementInfo.pagePath }}</code>
                    </dd>
                  </div>
                  <div>
                    <dt>选择器：</dt>
                    <dd>
                      <code>{{ selectedElementInfo.selector }}</code>
                    </dd>
                  </div>
                  <div v-if="selectedElementAttributes">
                    <dt>属性：</dt>
                    <dd>
                      <code>{{ selectedElementAttributes }}</code>
                    </dd>
                  </div>
                  <div>
                    <dt>定位验证：</dt>
                    <dd
                      :class="{ 'selector-verified': selectedElementInfo.selectorMatchCount === 1 }"
                    >
                      {{
                        selectedElementInfo.selectorMatchCount === 1
                          ? '已唯一匹配当前元素'
                          : `匹配到 ${selectedElementInfo.selectorMatchCount} 个元素`
                      }}
                    </dd>
                  </div>
                </dl>
              </template>
            </Alert>
            <a-textarea
              v-model:value="userMessage"
              :disabled="!isOwner || generating"
              :maxlength="2000"
              :auto-size="{ minRows: 2, maxRows: 5 }"
              :placeholder="composerPlaceholder"
              @keydown.ctrl.enter="generateCode()"
            />
            <div class="composer-footer">
              <span>Ctrl + Enter 发送</span>
              <div class="composer-actions">
                <a-button
                  :type="isEditMode ? 'primary' : 'default'"
                  :class="{ 'edit-mode-active': isEditMode }"
                  :disabled="!isOwner || !previewUrl || !previewLoaded || generating"
                  @click="toggleEditMode"
                >
                  <EditOutlined /> {{ isEditMode ? '退出选择' : '选择元素' }}
                </a-button>
                <a-button
                  type="primary"
                  html-type="submit"
                  :loading="generating"
                  :disabled="!isOwner || !userMessage.trim()"
                >
                  <SendOutlined /> 发送
                </a-button>
              </div>
            </div>
          </form>
        </section>

        <section class="preview-panel" aria-label="网站预览">
          <div class="browser-bar">
            <span class="browser-dots"><i /><i /><i /></span>
            <span class="browser-address">{{ previewUrl || '等待网站生成完成' }}</span>
            <a-button
              type="text"
              aria-label="刷新预览"
              :disabled="!previewUrl"
              @click="refreshPreview"
            >
              ↻
            </a-button>
          </div>
          <iframe
            v-if="previewUrl"
            ref="previewFrame"
            :key="previewVersion"
            :src="previewUrl"
            title="生成网站预览"
            sandbox="allow-same-origin allow-scripts allow-forms allow-modals allow-popups"
            @load="handlePreviewLoad"
          />
          <div v-else class="preview-empty">
            <span>◎</span>
            <h2>{{ generating ? '网站正在生成' : '这里会出现你的网站' }}</h2>
            <p>
              {{ generating ? '代码接收完成后会自动刷新预览。' : '在左侧发送一段描述开始创作。' }}
            </p>
          </div>
        </section>
      </main>
    </Spin>

    <AppDetailModal
      v-model:open="detailsOpen"
      :app="app"
      :can-manage="canManage"
      :deleting="deleting"
      @edit="router.push(`/app/edit/${appId}`)"
      @delete="confirmDelete"
    />
    <DeploySuccessModal v-model:open="deploySuccessOpen" :deployed-url="deployedUrl" />
  </div>
</template>

<style scoped>
.chat-page {
  width: min(1500px, calc(100% - 48px));
  margin: 0 auto;
  padding: 18px 0 28px;
}

.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  margin-bottom: 14px;
}

.workbench-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.workbench-title h1 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 24px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-top: 6px;
}

.code-gen-type-tag {
  flex: none;
  margin-inline-end: 0;
  font-size: 11px;
}

.workbench-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 7px;
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(350px, 2fr) minmax(520px, 3fr);
  height: min(750px, calc(100dvh - 145px));
  min-height: 580px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fffefb;
  box-shadow: 0 18px 50px #4d46310d;
}

.chat-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--line);
}

.panel-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 15px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}

.panel-label span {
  font-size: 13px;
  font-weight: 650;
}

.panel-label small {
  color: #999a90;
  font-size: 9px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 18px;
  scroll-behavior: auto;
}

.history-loader {
  display: flex;
  justify-content: center;
  min-height: 34px;
  margin: -7px 0 14px;
}

.history-loader :deep(.ant-btn) {
  color: #8b796d;
  font-size: 10px;
}

.chat-empty,
.preview-empty {
  display: grid;
  height: 100%;
  place-items: center;
  align-content: center;
  text-align: center;
  color: #93948a;
}

.chat-empty span {
  color: #be866e;
  font-size: 26px;
}

.chat-empty h2,
.preview-empty h2 {
  margin: 15px 0 8px;
  color: #595a52;
  font-size: 15px;
  font-weight: 600;
}

.chat-empty p,
.preview-empty p {
  margin: 0;
  font-size: 11px;
}

.chat-message {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 11px;
  margin-bottom: 24px;
}

.message-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid #deddd4;
  border-radius: 50%;
  color: #7d7e74;
  background: #f3f1e9;
  font-size: 11px;
}

.chat-message.assistant .message-avatar {
  overflow: hidden;
  border-color: #dfd6c9;
  background: #f7f1e6;
}

.message-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.chat-message strong {
  display: block;
  margin: 5px 0 8px;
  color: #55564e;
  font-size: 10px;
}

.chat-message pre {
  margin: 0;
  overflow-wrap: anywhere;
  color: #43443e;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.chat-message pre.streaming-output {
  max-height: 460px;
  padding: 14px;
  overflow: auto;
  border: 1px solid #30363d;
  border-radius: 8px;
  color: #c9d1d9;
  background: #0d1117;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  line-height: 1.65;
  overflow-wrap: normal;
  white-space: pre;
}

.markdown-content {
  min-width: 0;
  color: #43443e;
  font-size: 12px;
  line-height: 1.75;
}

.markdown-content :deep(p) {
  margin: 0 0 10px;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.markdown-content :deep(pre.hljs) {
  max-height: 460px;
  margin: 0 0 12px;
  padding: 14px;
  overflow: auto;
  border: 1px solid #30363d;
  border-radius: 8px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  line-height: 1.65;
  white-space: pre;
}

.markdown-content :deep(pre.hljs:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(code) {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.markdown-content :deep(:not(pre) > code) {
  padding: 2px 5px;
  border-radius: 4px;
  color: #b34f2d;
  background: #f2eee7;
  font-size: 0.92em;
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  color: #8d8e84;
  font-size: 11px;
}

.typing i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #ca8267;
  animation: pulse 1s infinite alternate;
}

.typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.typing i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes pulse {
  to {
    opacity: 0.25;
    transform: translateY(-2px);
  }
}

.chat-composer {
  padding: 14px;
  border-top: 1px solid var(--line);
  background: #faf9f5;
}

.build-progress-card {
  margin: 0 14px 12px;
  padding: 10px 12px 7px;
  border: 1px solid #e4ded6;
  border-radius: 8px;
  background: #fffefb;
}

.build-progress-card.is-failed {
  border-color: #efc4bc;
  background: #fff8f6;
}

.build-progress-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
  color: #75675e;
  font-size: 10px;
}

.build-progress-heading strong {
  overflow: hidden;
  color: #5d5149;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generation-mode-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  padding: 9px 11px;
  border: 1px solid #e4ded6;
  border-radius: 8px;
  background: #fffefb;
}

.generation-mode-row > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.generation-mode-row strong {
  color: #5d5149;
  font-size: 11px;
}

.generation-mode-row small {
  color: #9a8f87;
  font-size: 9px;
  line-height: 1.4;
}

.chat-composer :deep(.ant-input) {
  resize: none;
  border-color: #dcdbd1;
  background: #fffefb;
  box-shadow: none;
  font-size: 12px;
}

.selected-element-alert {
  margin-bottom: 10px;
  border-color: #e2cfc2;
  background: #f8f1eb;
}

.selected-element-alert :deep(.ant-alert-message) {
  color: #5d5149;
  font-size: 11px;
  font-weight: 650;
}

.selected-element-alert :deep(.ant-alert-description) {
  color: #82766f;
  font-size: 10px;
}

.selected-element-title code,
.selected-element-details code {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.selected-element-title code {
  color: #b54f31;
}

.selected-element-details {
  display: grid;
  max-height: 128px;
  gap: 4px;
  margin: 5px 0 0;
  overflow: auto;
}

.selected-element-details > div {
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr);
  gap: 5px;
}

.selected-element-details dt {
  color: #6e625b;
  font-weight: 650;
}

.selected-element-details dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.selected-element-details .selector-verified {
  color: #4f7650;
  font-weight: 600;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.composer-footer > span {
  color: #a0a197;
  font-size: 9px;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-actions .edit-mode-active {
  border-color: #b54f31;
  background: #b54f31;
  box-shadow: none;
}

.composer-actions .edit-mode-active:hover,
.composer-actions .edit-mode-active:focus {
  border-color: #9f3f25;
  background: #9f3f25;
}

.preview-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: #e9e8e2;
}

.browser-bar {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr) 34px;
  align-items: center;
  gap: 12px;
  height: 54px;
  padding: 0 14px;
  border-bottom: 1px solid #d8d7cf;
  background: #f3f2ed;
}

.browser-dots {
  display: flex;
  gap: 6px;
}

.browser-dots i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d2a18e;
}

.browser-dots i:nth-child(2) {
  background: #d2c08f;
}

.browser-dots i:nth-child(3) {
  background: #98ad90;
}

.browser-address {
  overflow: hidden;
  padding: 7px 13px;
  border: 1px solid #dcdbd4;
  border-radius: 6px;
  color: #97988e;
  background: #fffefb;
  font-size: 9px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-panel iframe {
  width: 100%;
  flex: 1;
  border: 0;
  background: white;
}

.preview-empty > span {
  color: #aaa58f;
  font-family: Georgia, serif;
  font-size: 48px;
}

@media (max-width: 1000px) {
  .workbench-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .workbench-actions {
    justify-content: flex-start;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat-panel {
    min-height: 590px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .preview-panel {
    min-height: 640px;
  }
}

@media (max-width: 700px) {
  .chat-page {
    width: calc(100% - 24px);
    padding-top: 20px;
  }

  .workbench-title h1 {
    font-size: 20px;
  }

  .workbench-actions :deep(.ant-btn) {
    padding-inline: 9px;
    font-size: 11px;
  }

  .workbench-grid {
    border-radius: 11px;
  }

  .chat-panel {
    min-height: 540px;
  }

  .preview-panel {
    min-height: 520px;
  }

  .composer-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .composer-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
