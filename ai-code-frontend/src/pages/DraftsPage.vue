<script setup lang="ts">
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons-vue'
import { useDraftStore, type IdeaDraft } from '@/stores/drafts'
const store = useDraftStore()
const router = useRouter()
function edit(draft: IdeaDraft) {
  store.edit(draft)
  router.push('/')
}
function remove(id: string) {
  try {
    store.remove(id)
    message.success('草稿已删除')
  } catch {
    message.error('删除失败，请检查浏览器存储设置')
  }
}
function createIdea() {
  store.useExample('')
  router.push('/')
}
const formatDate = (date: string) =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(date))
</script>
<template>
  <div class="page-width interior-page">
    <div class="page-heading">
      <div>
        <span class="section-kicker">YOUR NEXT BIG THING</span>
        <h1>
          我的草稿<span class="heading-count">{{ store.drafts.length }}</span>
        </h1>
        <p>收好每一次灵光一现。草稿保存在当前浏览器，暂不支持跨设备同步。</p>
      </div>
      <a-button type="primary" @click="createIdea"><PlusOutlined /> 新的想法</a-button>
    </div>
    <div v-if="!store.drafts.length" class="empty-state">
      <div class="empty-symbol"><FolderOpenOutlined /></div>
      <h2>这里，等着你的第一个想法。</h2>
      <p>一个作品集、一间线上小店，或是一个让生活更轻松的工具。</p>
      <a-button type="primary" size="large" @click="createIdea">去记录灵感 ↗</a-button>
    </div>
    <div v-else class="draft-grid">
      <article class="draft-card" v-for="(draft, index) in store.drafts" :key="draft.id">
        <div class="draft-card-top">
          <span class="draft-number">{{ String(index + 1).padStart(2, '0') }}</span
          ><a-tag :bordered="false">创意草稿</a-tag>
        </div>
        <h2>{{ draft.prompt.slice(0, 32) }}{{ draft.prompt.length > 32 ? '…' : '' }}</h2>
        <p class="draft-description">{{ draft.prompt }}</p>
        <time :datetime="draft.updatedAt">{{ formatDate(draft.updatedAt) }}</time>
        <div class="draft-actions">
          <a-button @click="edit(draft)"><EditOutlined /> 继续编辑</a-button
          ><a-popconfirm
            title="删除这份草稿？"
            description="删除后无法恢复。"
            ok-text="删除"
            cancel-text="保留"
            @confirm="remove(draft.id)"
            ><a-button type="text" danger aria-label="删除草稿"><DeleteOutlined /></a-button
          ></a-popconfirm>
        </div>
      </article>
    </div>
  </div>
</template>
