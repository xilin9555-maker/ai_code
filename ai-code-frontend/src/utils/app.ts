/**
 * 前端使用字符串保存雪花 ID，避免超过 JavaScript 安全整数范围后发生精度丢失。
 * OpenAPI 当前仍把 Java Long 描述为 number，因此只在调用生成代码时做类型断言，
 * 运行时的值始终保持字符串，不会经过 Number 转换。
 */
export type AppView = Omit<API.AppVO, 'id' | 'userId'> & {
  id?: string
  userId?: string
}

export function normalizeApp(app: API.AppVO): AppView {
  return {
    ...app,
    id: app.id == null ? undefined : String(app.id),
    userId: app.userId == null ? undefined : String(app.userId),
  }
}

export function toApiId(id: string): number {
  return id as unknown as number
}

/**
 * 优先使用应用已配置的封面；没有封面时，根据应用 ID 生成一张稳定的随机图片。
 * 同一个应用始终使用相同的 seed，因此列表卡片与详情弹窗不会显示成两张不同的图。
 */
export function getAppCoverUrl(app: AppView, width = 640, height = 360) {
  const customCover = app.cover?.trim()
  if (customCover) return customCover

  const seed = encodeURIComponent(`lingbuild-${app.id || app.appName || 'default'}`)
  return `https://picsum.photos/seed/${seed}/${width}/${height}`
}

/** 将服务端时间转换成稳定的中文日期，缺少或无效时显示占位符。 */
export function formatAppDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
