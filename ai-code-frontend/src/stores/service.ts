import { defineStore } from 'pinia'
import { ref } from 'vue'
import { healthCheck } from '@/api/healthController'

export const useServiceStore = defineStore('service', () => {
  const status = ref<'checking' | 'online' | 'offline'>('checking')
  const checking = ref(false)
  async function checkHealth() {
    if (checking.value) return
    checking.value = true
    status.value = 'checking'
    try {
      const response = await healthCheck({ timeout: 5000 })
      status.value = response.data.code === 0 && response.data.data === 'ok' ? 'online' : 'offline'
    } catch {
      status.value = 'offline'
    } finally {
      checking.value = false
    }
  }
  return { status, checking, checkHealth }
})
