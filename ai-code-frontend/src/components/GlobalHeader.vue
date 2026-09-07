<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import type { MenuProps } from 'ant-design-vue'
import { routes } from '@/router'
const route = useRoute()
const router = useRouter()
const menuItems = routes
  .filter((item) => item.meta.inMenu)
  .map((item) => ({ key: item.path, label: item.meta.title }))
const selectedKeys = computed(() => [route.path])
const onMenuClick: MenuProps['onClick'] = ({ key }) => router.push(String(key))
</script>
<template>
  <a-layout-header class="global-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" aria-label="灵构首页">
        <img src="/logo.svg" width="36" height="36" alt="" />
        <span>灵构<span class="brand-en">LINGBUILD</span></span>
      </RouterLink>
      <nav aria-label="主导航">
        <a-menu
          mode="horizontal"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="onMenuClick"
        />
      </nav>
      <a-button class="login-button" @click="router.push('/user/login')"
        >登录 <ArrowUpOutlined class="diagonal-arrow"
      /></a-button>
    </div>
  </a-layout-header>
</template>
