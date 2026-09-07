<script setup lang="ts">
import { useServiceStore } from '@/stores/service'
const service = useServiceStore()
const stages = [
  {
    number: '01',
    title: '捕捉想法',
    text: '写下需求，用灵感示例找到方向，把想法保存为本地草稿。',
    state: '已开放',
  },
  {
    number: '02',
    title: '对话式创作',
    text: '通过自然语言生成应用，边预览边修改，让想法逐步变成作品。',
    state: '规划中',
  },
  {
    number: '03',
    title: '让作品上线',
    text: '下载源码、部署应用，把自己的创造分享给更多人。',
    state: '规划中',
  },
]
</script>
<template>
  <div class="page-width interior-page about-page">
    <span class="section-kicker">MADE FOR YOUR IDEAS</span>
    <h1>创造，从来不只属于<br /><span class="text-accent">会写代码的人。</span></h1>
    <p class="about-lead">
      灵构是一个正在成长的 AI 应用工坊。我们希望让每一个有想法的人，<br />都能跨过技术的门槛，做出自己的作品。
    </p>
    <div class="roadmap-grid">
      <article v-for="stage in stages" :key="stage.number">
        <span class="stage-number">{{ stage.number }}</span
        ><a-tag :color="stage.number === '01' ? 'green' : undefined">{{ stage.state }}</a-tag>
        <h2>{{ stage.title }}</h2>
        <p>{{ stage.text }}</p>
      </article>
    </div>
    <section class="about-note">
      <div>
        <h2>当前版本 · 创作空间初版</h2>
        <p>
          现已提供页面导航、灵感示例、本地草稿和后端连接检测。账号登录、AI
          生成与云端存储将在后续版本开放。
        </p>
      </div>
      <a-button :loading="service.checking" @click="service.checkHealth">重新检查连接</a-button>
    </section>
  </div>
</template>
