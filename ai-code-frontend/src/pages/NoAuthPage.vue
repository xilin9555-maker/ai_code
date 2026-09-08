<script setup lang="ts">
import { LockOutlined } from '@ant-design/icons-vue'
</script>

<template>
  <main class="no-auth-page page-width interior-page">
    <div class="permission-card">
      <span class="permission-icon" aria-hidden="true"><LockOutlined /></span>
      <span class="section-kicker">ACCESS LIMITED</span>
      <h1>当前账号没有访问权限</h1>
      <p>这个页面只向具备相应权限的账号开放。如果你的权限刚刚发生变化，可以重新登录后再试。</p>
      <RouterLink class="home-link" to="/">返回首页</RouterLink>
    </div>
  </main>
</template>

<style scoped>
.no-auth-page {
  display: grid;
  min-height: 68vh;
  padding-top: 72px;
  place-items: center;
}

.permission-card {
  width: min(560px, 100%);
  padding: 54px 48px;
  background: #fffefb;
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: 0 18px 55px #4d46310d;
  text-align: center;
}

.permission-icon {
  display: grid;
  width: 56px;
  height: 56px;
  margin: 0 auto 22px;
  color: var(--accent);
  background: #f8e6df;
  border-radius: 50%;
  font-size: 23px;
  place-items: center;
}

.permission-card h1 {
  margin: 13px 0 12px;
  color: var(--ink);
  font-size: clamp(28px, 4vw, 40px);
}

.permission-card p {
  margin: 0 auto 28px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.home-link {
  display: inline-flex;
  min-height: 40px;
  padding: 0 22px;
  align-items: center;
  color: #fff;
  background: var(--accent);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}
</style>
