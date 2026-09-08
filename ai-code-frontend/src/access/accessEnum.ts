/**
 * 页面支持的访问级别。
 *
 * 路由、导航菜单和权限校验都引用这里的固定值，避免各处分别书写角色字符串后
 * 出现拼写不一致。普通用户权限也允许管理员访问，因为管理员同样处于登录状态。
 */
const ACCESS_ENUM = {
  NOT_LOGIN: 'notLogin',
  USER: 'user',
  ADMIN: 'admin',
} as const

export type AccessValue = (typeof ACCESS_ENUM)[keyof typeof ACCESS_ENUM]

export default ACCESS_ENUM
