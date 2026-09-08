import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController'

/**
 * 创建一份统一的游客状态，确保首次进入页面和退出登录后使用相同的数据结构。
 */
const createGuestUser = (): API.LoginUserVO => ({
  userName: '未登录',
})

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>(createGuestUser())
  const loading = ref(false)
  const initialized = ref(false)

  /**
   * 从服务端 Session 获取当前登录用户。
   */
  async function fetchLoginUser() {
    if (loading.value) return
    loading.value = true
    try {
      const response = await getLoginUser()
      if (response.data.code === 0 && response.data.data) {
        loginUser.value = response.data.data
      } else {
        loginUser.value = createGuestUser()
      }
    } catch {
      loginUser.value = createGuestUser()
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  /**
   * 用刚刚登录或主动更新后的用户信息刷新全局状态。
   */
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
    initialized.value = true
  }

  /**
   * 清除浏览器内存中的当前用户信息，并把全局状态恢复成游客状态。
   * 后端 Session 由注销接口负责删除，这个方法只负责让前端页面立即响应身份变化。
   */
  function clearLoginUser() {
    loginUser.value = createGuestUser()
    initialized.value = true
  }

  return {
    loginUser,
    loading,
    initialized,
    fetchLoginUser,
    setLoginUser,
    clearLoginUser,
  }
})
