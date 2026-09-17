<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Empty, message, Pagination, Skeleton } from 'ant-design-vue'
import {
  ArrowRightOutlined,
  ArrowUpOutlined,
  BulbOutlined,
  EditOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
import InspirationCard from '@/components/InspirationCard.vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { inspirations } from '@/data/inspirations'
import { useLoginUserStore } from '@/stores/loginUser'
import { normalizeApp, type AppView } from '@/utils/app'
import {
  loadNewAppAgentMode,
  saveAppAgentMode,
  saveNewAppAgentMode,
} from '@/utils/generationMode'

const router = useRouter()
const loginUserStore = useLoginUserStore()
/** 与后端输入护轨保持一致，避免用户提交后才发现需求超过安全上限。 */
const PROMPT_MAX_LENGTH = 1000
const prompt = ref('')
const promptInput = ref<{ focus: () => void } | null>(null)
const creating = ref(false)
const agentMode = ref(loadNewAppAgentMode())
const myAppsLoading = ref(false)
const goodAppsLoading = ref(false)
const myApps = ref<AppView[]>([])
const goodApps = ref<AppView[]>([])
const myTotal = ref(0)
const goodTotal = ref(0)

/** 首页使用较小的分页，让作品卡片保持舒展，也避免一次加载过多封面。 */
const myQuery = reactive({ pageNum: 1, pageSize: 6, appName: '' })
const goodQuery = reactive({ pageNum: 1, pageSize: 6, appName: '' })
const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser.id))
const generationModeDescription = computed(() =>
  agentMode.value
    ? '先规划素材、生成代码并检查质量，适合完整应用'
    : '直接生成代码，适合快速创建和日常调整',
)

/** 保存主页模式选择，创建成功后还会绑定到新应用。 */
function handleAgentModeChange(value: boolean | string | number) {
  const enabled = value === true
  agentMode.value = enabled
  saveNewAppAgentMode(enabled)
}

/**
 * 把灵感示例放入输入框并将页面滚动到创作区，用户仍可继续修改后再提交。
 */
async function selectIdea(value: string) {
  prompt.value = value
  await nextTick()
  promptInput.value?.focus()
  document.querySelector('#idea-composer')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/**
 * 创建应用记录后进入生成工作台。
 * 这里只保存初始化需求，真正的 AI 生成在工作台通过 SSE 发起，页面可以实时展示进度。
 */
async function createApplication() {
  const initPrompt = prompt.value.trim()
  if (creating.value || initPrompt.length < 3) return

  if (!isLoggedIn.value) {
    sessionStorage.setItem('pending-app-prompt', initPrompt)
    sessionStorage.setItem('pending-app-mode', agentMode.value ? 'agent' : 'normal')
    message.info('登录后即可用这段描述创建应用')
    await router.push({ path: '/user/login', query: { redirect: '/?resume=1' } })
    return
  }

  creating.value = true
  try {
    const response = await addApp({ initPrompt })
    if (response.data.code === 0 && response.data.data != null) {
      // 服务端的 Long 可能超过 JavaScript 安全整数范围，因此路由中始终保留原始字符串。
      const appId = String(response.data.data)
      // 工作台读取历史前会恢复该值，因此第一次自动生成就使用主页选择的模式。
      saveAppAgentMode(appId, agentMode.value)
      prompt.value = ''
      sessionStorage.removeItem('pending-app-prompt')
      sessionStorage.removeItem('pending-app-mode')
      // 工作台会先读取历史记录，确认这是一个空会话后再自动发送初始化需求。
      await router.push(`/app/chat/${appId}`)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建应用失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

/** 查询当前账号创建的应用，列表只会由后端 Session 对应的用户范围产生。 */
async function loadMyApps() {
  if (!isLoggedIn.value || myAppsLoading.value) return
  myAppsLoading.value = true
  try {
    const response = await listMyAppVoByPage({
      ...myQuery,
      appName: myQuery.appName.trim() || undefined,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    const page = response.data.data
    myApps.value = (page?.records ?? []).map(normalizeApp)
    myTotal.value = Number(page?.totalRow ?? 0)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取我的应用失败')
  } finally {
    myAppsLoading.value = false
  }
}

/** 查询由管理员设为精选的公开应用，未登录访客也可以浏览。 */
async function loadGoodApps() {
  if (goodAppsLoading.value) return
  goodAppsLoading.value = true
  try {
    const response = await listGoodAppVoByPage({
      ...goodQuery,
      appName: goodQuery.appName.trim() || undefined,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    const page = response.data.data
    goodApps.value = (page?.records ?? []).map(normalizeApp)
    goodTotal.value = Number(page?.totalRow ?? 0)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取精选应用失败')
  } finally {
    goodAppsLoading.value = false
  }
}

async function changeMyPage(page: number) {
  myQuery.pageNum = page
  await loadMyApps()
}

async function changeGoodPage(page: number) {
  goodQuery.pageNum = page
  await loadGoodApps()
}

/** 新的名称条件从第一页查询，避免沿用旧页码导致明明有结果却显示空列表。 */
async function searchMyApps() {
  myQuery.pageNum = 1
  await loadMyApps()
}

async function searchGoodApps() {
  goodQuery.pageNum = 1
  await loadGoodApps()
}

/** 登录或注销后立即刷新个人作品区，使首页身份状态与服务端保持一致。 */
watch(
  () => loginUserStore.loginUser.id,
  (userId) => {
    if (userId) {
      myQuery.pageNum = 1
      void loadMyApps()
    } else {
      myApps.value = []
      myTotal.value = 0
    }
  },
)

onMounted(() => {
  const pendingPrompt = sessionStorage.getItem('pending-app-prompt')
  if (pendingPrompt) prompt.value = pendingPrompt
  const pendingMode = sessionStorage.getItem('pending-app-mode')
  if (pendingMode === 'agent' || pendingMode === 'normal') {
    handleAgentModeChange(pendingMode === 'agent')
  }
  void loadGoodApps()
  if (isLoggedIn.value) void loadMyApps()
})
</script>

<template>
  <div class="home-page page-width">
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-orbit orbit-one" aria-hidden="true">✳</div>
      <div class="hero-orbit orbit-two" aria-hidden="true">✦</div>
      <span class="eyebrow">
        <span /> 想法的下一站，是作品 <span class="edition">AI BUILDER</span>
      </span>
      <h1 id="hero-title">
        <span class="hero-accent">
          AI 应用
          <svg viewBox="0 0 300 16" aria-hidden="true">
            <path d="M4 10Q130 -2 296 8M55 14Q186 4 268 12" />
          </svg>
        </span>
        生成平台
      </h1>
      <p class="hero-description">
        一句话轻松创建网站应用。<br
          class="mobile-break"
        />描述页面、功能和风格，实时看见想法变成可以运行的网站。
      </p>

      <form id="idea-composer" class="idea-composer" @submit.prevent="createApplication">
        <label for="idea-input" class="composer-label">
          <EditOutlined /> 今天，你想创造什么？
        </label>
        <a-textarea
          id="idea-input"
          ref="promptInput"
          v-model:value="prompt"
          :maxlength="PROMPT_MAX_LENGTH"
          :auto-size="{ minRows: 3, maxRows: 7 }"
          placeholder="帮我创建个人博客网站"
          :bordered="false"
        />
        <div class="composer-mode-row">
          <div class="composer-mode-copy">
            <strong>{{ agentMode ? 'AI 工作流模式' : '普通模式' }}</strong>
            <small>{{ generationModeDescription }}</small>
          </div>
          <a-switch
            :checked="agentMode"
            :disabled="creating"
            checked-children="AI 工作流"
            un-checked-children="普通模式"
            aria-label="选择首次生成模式"
            @update:checked="handleAgentModeChange"
          />
        </div>
        <div class="composer-bottom">
          <span class="composer-hint">
            <span class="tiny-spark">✳</span> 写清页面、功能和喜欢的风格
            <span class="character-count">{{ prompt.length }}/{{ PROMPT_MAX_LENGTH }}</span>
          </span>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            :loading="creating"
            :disabled="prompt.trim().length < 3"
          >
            开始生成 <ArrowUpOutlined class="diagonal-arrow" />
          </a-button>
        </div>
      </form>

      <div class="quick-ideas">
        <span>试试这些</span>
        <button
          v-for="idea in inspirations"
          :key="idea.id"
          type="button"
          @click="selectIdea(idea.prompt)"
        >
          <BulbOutlined /> {{ idea.category }} <span>+</span>
        </button>
      </div>
      <p class="availability-note">登录后创建 · 实时生成 · 一键部署</p>
    </section>

    <section v-if="isLoggedIn" class="application-section" aria-labelledby="my-apps-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">YOUR WORKSPACE</span>
          <h2 id="my-apps-title">
            我的应用 <span class="section-count">{{ myTotal }}</span>
          </h2>
        </div>
        <form class="section-search" @submit.prevent="searchMyApps">
          <a-input
            v-model:value="myQuery.appName"
            allow-clear
            placeholder="搜索我的应用"
            @clear="searchMyApps"
          />
          <a-button html-type="submit" :loading="myAppsLoading" aria-label="搜索我的应用">
            <SearchOutlined />
          </a-button>
          <button class="text-action" type="button" :disabled="myAppsLoading" @click="loadMyApps">
            <ReloadOutlined /> 刷新
          </button>
        </form>
      </div>

      <Skeleton v-if="myAppsLoading && !myApps.length" active :paragraph="{ rows: 5 }" />
      <div v-else-if="myApps.length" class="application-grid">
        <AppCard v-for="app in myApps" :key="app.id" :app="app" editable />
      </div>
      <Empty
        v-else
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="还没有应用，从上方写下第一个想法吧"
      />
      <Pagination
        v-if="myTotal > myQuery.pageSize"
        class="section-pagination"
        :current="myQuery.pageNum"
        :page-size="myQuery.pageSize"
        :total="myTotal"
        :show-size-changer="false"
        @change="changeMyPage"
      />
    </section>

    <section class="application-section" aria-labelledby="good-apps-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">FEATURED BUILDS</span>
          <h2 id="good-apps-title">精选应用</h2>
        </div>
        <form class="section-search" @submit.prevent="searchGoodApps">
          <a-input
            v-model:value="goodQuery.appName"
            allow-clear
            placeholder="搜索精选应用"
            @clear="searchGoodApps"
          />
          <a-button html-type="submit" :loading="goodAppsLoading" aria-label="搜索精选应用">
            <SearchOutlined />
          </a-button>
        </form>
      </div>

      <Skeleton v-if="goodAppsLoading && !goodApps.length" active :paragraph="{ rows: 5 }" />
      <div v-else-if="goodApps.length" class="application-grid">
        <AppCard v-for="app in goodApps" :key="app.id" :app="app" />
      </div>
      <div v-else class="quiet-empty">
        <span>✦</span>
        <p>精选作品正在准备中</p>
      </div>
      <Pagination
        v-if="goodTotal > goodQuery.pageSize"
        class="section-pagination"
        :current="goodQuery.pageNum"
        :page-size="goodQuery.pageSize"
        :total="goodTotal"
        :show-size-changer="false"
        @change="changeGoodPage"
      />
    </section>

    <section class="inspiration-section" aria-labelledby="inspiration-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">A LITTLE INSPIRATION</span>
          <h2 id="inspiration-title">不知道从哪开始？从这里开始。</h2>
        </div>
        <span class="section-aside">选一个灵感，写成你的版本 <span>↙</span></span>
      </div>
      <div class="inspiration-grid">
        <InspirationCard
          v-for="idea in inspirations"
          :key="idea.id"
          v-bind="idea"
          @select="selectIdea(idea.prompt)"
        />
      </div>
    </section>

    <section class="workspace-strip">
      <div class="strip-icon"><FolderOpenOutlined /></div>
      <div>
        <h2>先记录，还没准备生成的想法。</h2>
        <p>草稿保存在当前浏览器，随时可以继续整理。</p>
      </div>
      <RouterLink to="/drafts">我的草稿 <ArrowRightOutlined /></RouterLink>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  position: relative;
  isolation: isolate;
}

.home-page::before {
  position: absolute;
  top: 0;
  left: 50%;
  z-index: -1;
  width: 100vw;
  height: 610px;
  border-bottom: 1px solid #e5e3da99;
  background:
    linear-gradient(#77786f0b 1px, transparent 1px),
    linear-gradient(90deg, #77786f0b 1px, transparent 1px),
    radial-gradient(circle at 18% 20%, #f0b49d42, transparent 28%),
    radial-gradient(circle at 82% 12%, #aebda542, transparent 25%),
    linear-gradient(180deg, #fbfaf6, #f7f6f200);
  background-size:
    48px 48px,
    48px 48px,
    auto,
    auto,
    auto;
  content: '';
  pointer-events: none;
  transform: translateX(-50%);
}

.composer-mode-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 17px;
  padding: 10px 12px;
  border: 1px solid #ebe2dc;
  border-radius: 9px;
  background: #fcfaf7;
}

.composer-mode-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.composer-mode-copy strong {
  color: #5d5149;
  font-size: 11px;
}

.composer-mode-copy small {
  color: #969087;
  font-size: 10px;
  line-height: 1.45;
}

.application-section {
  padding: 48px 0 54px;
  border-top: 1px solid var(--line);
}

.application-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 30px 22px;
}

.section-count {
  margin-left: 8px;
  color: #a0a194;
  font-family: Georgia, serif;
  font-size: 14px;
  font-weight: 400;
}

.text-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 0;
  color: #73756b;
  background: transparent;
  border: 0;
  font-size: 11px;
}

.text-action:hover {
  color: var(--accent);
}

.section-search {
  display: flex;
  align-items: center;
  gap: 7px;
}

.section-search :deep(.ant-input-affix-wrapper) {
  width: 190px;
  background: #fbfaf6;
}

.text-action:disabled {
  cursor: wait;
  opacity: 0.5;
}

.section-pagination {
  margin-top: 32px;
  text-align: center;
}

.quiet-empty {
  display: grid;
  min-height: 190px;
  place-items: center;
  align-content: center;
  gap: 10px;
  border: 1px dashed #d8d6ca;
  border-radius: 12px;
  color: #a0a196;
}

.quiet-empty span {
  color: #b19e72;
  font-size: 24px;
}

.quiet-empty p {
  margin: 0;
  font-size: 12px;
}

.inspiration-section {
  padding-top: 48px;
  border-top: 1px solid var(--line);
}

@media (max-width: 900px) {
  .application-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .application-section {
    padding: 38px 0 44px;
  }

  .application-grid {
    grid-template-columns: 1fr;
    gap: 27px;
  }

  .application-section .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .section-search {
    width: 100%;
  }

  .section-search :deep(.ant-input-affix-wrapper) {
    width: auto;
    flex: 1;
  }

  .composer-mode-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
