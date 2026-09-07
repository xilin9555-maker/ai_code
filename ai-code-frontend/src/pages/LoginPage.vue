<script setup lang="ts">
import { LockOutlined } from '@ant-design/icons-vue'
</script>
<template>
  <div class="page-width interior-page">
    <section class="empty-state">
      <div class="empty-symbol"><LockOutlined /></div>
      <span class="section-kicker">COMING SOON</span>
      <h1>你的创作空间，即将相连。</h1>
      <p>账号登录将在用户模块开放。现在，你可以自由探索灵感，并将草稿保存在本机。</p>
      <RouterLink to="/"><a-button type="primary" size="large">先去探索 ↗</a-button></RouterLink>
    </section>
  </div>
</template>
