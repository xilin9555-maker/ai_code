<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  BulbOutlined,
  EditOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons-vue'
import InspirationCard from '@/components/InspirationCard.vue'
import { inspirations } from '@/data/inspirations'
import { useDraftStore } from '@/stores/drafts'

const drafts = useDraftStore()
const router = useRouter()
const promptInput = ref<{ focus: () => void } | null>(null)
async function selectIdea(value: string) {
  drafts.useExample(value)
  await nextTick()
  promptInput.value?.focus()
  document.querySelector('#idea-composer')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function saveIdea() {
  try {
    drafts.save()
    message.success('创意已保存在当前浏览器')
    router.push('/drafts')
  } catch (error) {
    message.error(
      error instanceof Error && error.message.includes('创意描述')
        ? error.message
        : '保存失败，请检查浏览器存储空间或隐私设置',
    )
  }
}
</script>

<template>
  <div class="home-page page-width">
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-orbit orbit-one" aria-hidden="true">✳</div>
      <div class="hero-orbit orbit-two" aria-hidden="true">✦</div>
      <span class="eyebrow"
        ><span /> 想法的下一站，是作品 <span class="edition">EARLY ACCESS</span></span
      >
      <h1 id="hero-title">
        让想法，<span class="hero-accent"
          >即刻成形<svg viewBox="0 0 300 16" aria-hidden="true">
            <path d="M4 10Q130 -2 296 8M55 14Q186 4 268 12" /></svg></span
        >。
      </h1>
      <p class="hero-description">
        一句描述，开启你的下一件作品。<br class="mobile-break" />把创造留给自己，把复杂交给 AI。
      </p>
      <form id="idea-composer" class="idea-composer" @submit.prevent="saveIdea">
        <label for="idea-input" class="composer-label"
          ><EditOutlined />
          {{ drafts.editingId ? '继续打磨你的想法' : '今天，你想创造什么？' }}</label
        >
        <a-textarea
          id="idea-input"
          ref="promptInput"
          v-model:value="drafts.prompt"
          :maxlength="2000"
          :auto-size="{ minRows: 3, maxRows: 7 }"
          placeholder="比如，帮我做一个极简风格的个人作品集，展示我的设计作品…"
          :bordered="false"
        />
        <div class="composer-bottom">
          <span class="composer-hint"
            ><span class="tiny-spark">✳</span> 从一个小小的灵感开始
            <span class="character-count">{{ drafts.prompt.length }}/2000</span></span
          ><a-button
            type="primary"
            html-type="submit"
            size="large"
            :disabled="drafts.prompt.trim().length < 3"
            >{{ drafts.editingId ? '更新草稿' : '保存创意' }}
            <ArrowUpOutlined class="diagonal-arrow"
          /></a-button>
        </div>
      </form>
      <div class="quick-ideas">
        <span>试试这些</span
        ><button v-for="idea in inspirations" :key="idea.id" @click="selectIdea(idea.prompt)">
          <BulbOutlined /> {{ idea.category }} <span>+</span>
        </button>
      </div>
      <p class="availability-note">创意草稿已开放 · AI 生成与发布即将接入</p>
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
        <h2>给灵感留一个位置。</h2>
        <p>先记下来，下一次打开，接着创造。</p>
      </div>
      <RouterLink to="/drafts"
        >我的草稿 <span class="draft-count">{{ drafts.drafts.length }}</span>
        <span>↗</span></RouterLink
      >
    </section>
  </div>
</template>
