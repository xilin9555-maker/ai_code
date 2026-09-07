<script setup lang="ts">
import { computed } from 'vue'
import { useServiceStore } from '@/stores/service'
const service = useServiceStore()
const statusText = computed(
  () =>
    ({ checking: '正在连接服务', online: '服务已连接', offline: '服务暂未连接' })[service.status],
)
</script>
<template>
  <a-layout-footer class="global-footer">
    <div class="footer-inner">
      <span
        >© {{ new Date().getFullYear() }} 灵构
        <span class="footer-divider">/</span> 每个想法，都值得被看见。</span
      >
      <button
        class="service-status"
        :disabled="service.checking"
        title="点击重新检查连接"
        aria-label="重新检查服务连接"
        @click="service.checkHealth"
      >
        <span class="status-dot" :class="service.status" /><span role="status">{{
          statusText
        }}</span
        ><span aria-hidden="true">↻</span>
      </button>
    </div>
  </a-layout-footer>
</template>
