import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'
import ACCESS_ENUM, { type AccessValue } from './accessEnum'
import { checkAccess } from './checkAccess'

/**
 * 在每次路由切换前完成统一的登录和角色校验。
 *
 * 首次进入网站时先向服务端查询 Session，得到可信的登录身份后再决定是否放行。
 * 未登录用户访问受保护页面会前往登录页，并保留原地址供登录成功后返回；已经登录
 * 但权限不足的用户会进入无权限提示页。
 */
router.beforeEach(async (to) => {
  const loginUserStore = useLoginUserStore()
  if (!loginUserStore.initialized) {
    await loginUserStore.fetchLoginUser()
  }

  const needAccess = (to.meta.access as AccessValue | undefined) ?? ACCESS_ENUM.NOT_LOGIN
  if (checkAccess(loginUserStore.loginUser, needAccess)) {
    return true
  }
  if (!loginUserStore.loginUser.id) {
    return {
      path: '/user/login',
      query: { redirect: to.fullPath },
    }
  }
  return { path: '/no-auth' }
})
