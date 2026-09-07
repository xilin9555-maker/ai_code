<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const theme = {
  token: {
    colorPrimary: '#d65a36',
    colorText: '#272722',
    fontFamily: "'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif",
    borderRadius: 8,
    controlHeight: 40,
  },
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme"><BasicLayout /></a-config-provider>
</template>
