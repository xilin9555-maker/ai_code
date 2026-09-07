<script setup lang="ts">
import { onMounted } from 'vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { useServiceStore } from '@/stores/service'
const service = useServiceStore()
onMounted(() => service.checkHealth())
</script>
<template>
  <a-layout class="basic-layout">
    <a href="#main-content" class="skip-link">跳转到内容</a>
    <GlobalHeader />
    <a-layout-content id="main-content" class="main-content"><RouterView /></a-layout-content>
    <GlobalFooter />
  </a-layout>
</template>
