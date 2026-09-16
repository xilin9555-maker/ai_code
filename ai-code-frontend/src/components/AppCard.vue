<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  CodeOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { getDeployUrl } from '@/config/env'
import type { AppView } from '@/utils/app'
import { formatAppDate, getAppCoverUrl } from '@/utils/app'
import { getCodeGenTypeLabel } from '@/constants/codeGenType'

const props = withDefaults(
  defineProps<{
    app: AppView
    editable?: boolean
    deletable?: boolean
    deleting?: boolean
  }>(),
  { editable: false, deletable: false, deleting: false },
)
const emit = defineEmits<{
  delete: [app: AppView]
}>()

const router = useRouter()
const displayName = computed(() => props.app.appName || '未命名应用')
const coverUrl = computed(() => getAppCoverUrl(props.app))
const creatorName = computed(
  () => props.app.user?.userName || props.app.user?.userAccount || '匿名创作者',
)

/** 打开应用工作台；只有创建接口明确携带 generate 参数时才会自动开始生成。 */
function openConversation() {
  if (!props.app.id) return
  void router.push(`/app/chat/${props.app.id}`)
}

function openEditor() {
  if (!props.app.id) return
  void router.push(`/app/edit/${props.app.id}`)
}

function openWork() {
  const url = getDeployUrl(props.app.deployKey)
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <article class="app-card">
    <div class="app-cover" :style="{ backgroundImage: `url(${coverUrl})` }">
      <div v-if="!app.cover" class="generated-cover" aria-hidden="true">
        <span class="cover-mark"><CodeOutlined /></span>
        <strong>{{ displayName.slice(0, 8) }}</strong>
        <small>{{ getCodeGenTypeLabel(app.codeGenType) }}</small>
      </div>
      <div class="card-hover-actions">
        <a-button ghost @click="openConversation"><EyeOutlined /> 查看对话</a-button>
        <a-button v-if="app.deployKey" ghost @click="openWork">
          查看作品 <ArrowRightOutlined />
        </a-button>
      </div>
    </div>
    <div class="app-card-body">
      <div class="app-card-title">
        <div class="creator-summary">
          <a-avatar :size="38" :src="app.user?.userAvatar || undefined">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div class="creator-text">
            <h3>{{ displayName }}</h3>
            <span :title="creatorName">{{ creatorName }}</span>
          </div>
        </div>
        <div v-if="editable || deletable" class="card-title-actions">
          <a-button v-if="editable" type="text" aria-label="编辑应用" @click="openEditor">
            <EditOutlined />
          </a-button>
          <a-button
            v-if="deletable"
            type="text"
            danger
            aria-label="删除应用"
            :loading="deleting"
            @click="emit('delete', app)"
          >
            <DeleteOutlined v-if="!deleting" />
          </a-button>
        </div>
      </div>
      <p class="app-description">{{ app.initPrompt || '暂未填写应用介绍' }}</p>
      <div class="app-card-meta">
        <span>{{ formatAppDate(app.createTime) }}</span>
        <span v-if="app.deployKey" class="deployed-dot">已部署</span>
        <span v-else>未部署</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  min-width: 0;
}

.app-cover {
  position: relative;
  height: 190px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background-color: #e9e6dc;
  background-position: center;
  background-size: cover;
}

.generated-cover {
  display: flex;
  height: 100%;
  padding: 25px;
  align-items: flex-start;
  flex-direction: column;
  justify-content: flex-end;
  background: linear-gradient(180deg, transparent 32%, #272722c7 100%);
  color: white;
}

.cover-mark {
  position: absolute;
  top: 22px;
  left: 24px;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: 1px solid #ffffff80;
  border-radius: 50%;
  background: #2727225e;
}

.generated-cover strong {
  max-width: 90%;
  overflow: hidden;
  font-family: Georgia, 'Songti SC', serif;
  font-size: 25px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generated-cover small {
  margin-top: 7px;
  font-size: 8px;
  letter-spacing: 1.6px;
}

.card-hover-actions {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  background: #272722b8;
  opacity: 0;
  transition: opacity 0.2s;
}

.app-cover:hover .card-hover-actions,
.app-cover:focus-within .card-hover-actions {
  opacity: 1;
}

.card-hover-actions :deep(.ant-btn) {
  color: white;
  border-color: #ffffff8c;
}

.app-card-body {
  padding: 15px 2px 2px;
}

.app-card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.creator-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.creator-summary :deep(.ant-avatar) {
  flex: none;
  color: #827b70;
  background: #ece8de;
}

.creator-text {
  min-width: 0;
}

.card-title-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 2px;
}

.app-card h3 {
  margin: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 15px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.creator-text span {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #929389;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-description {
  display: -webkit-box;
  min-height: 35px;
  margin: 12px 0 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.app-card-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  color: #98998e;
  font-size: 9px;
  letter-spacing: 0.3px;
}

.deployed-dot {
  color: #728267;
}

.deployed-dot::before {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 6px;
  border-radius: 50%;
  background: #849a75;
  content: '';
}

@media (max-width: 700px) {
  .app-cover {
    height: 220px;
  }

  .card-hover-actions {
    align-items: flex-end;
    padding-bottom: 18px;
    background: linear-gradient(transparent 40%, #272722d6);
    opacity: 1;
  }
}
</style>
