import ACCESS_ENUM, { type AccessValue } from './accessEnum'

/**
 * 判断当前用户是否具备页面要求的访问级别。
 *
 * 没有声明权限的页面默认向所有访客开放；要求普通用户权限的页面允许任意已登录
 * 用户进入；管理员页面只接受 admin 角色。这个函数不负责跳转，因此路由守卫和
 * 菜单过滤都可以复用同一套判断标准。
 *
 * @param loginUser 全局状态中保存的当前用户，游客对象通常没有 id 和 userRole
 * @param needAccess 页面在路由 meta 中声明的访问级别
 * @return 当前用户满足访问要求时返回 true
 */
export function checkAccess(
  loginUser: API.LoginUserVO | undefined,
  needAccess: AccessValue = ACCESS_ENUM.NOT_LOGIN,
) {
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true
  }

  const currentAccess = loginUser?.userRole ?? ACCESS_ENUM.NOT_LOGIN
  if (needAccess === ACCESS_ENUM.USER) {
    return Boolean(loginUser?.id) && currentAccess !== ACCESS_ENUM.NOT_LOGIN
  }
  if (needAccess === ACCESS_ENUM.ADMIN) {
    return Boolean(loginUser?.id) && currentAccess === ACCESS_ENUM.ADMIN
  }
  return false
}
