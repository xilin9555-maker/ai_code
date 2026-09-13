import { CodeGenTypeEnum } from '@/constants/codeGenType'

/** 去掉末尾斜杠，避免后续拼接路径时出现重复的 //。 */
function removeTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

/** 所有普通接口和 SSE 连接共用的请求前缀。 */
export const API_BASE_URL = removeTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api')

/**
 * Nginx 对外提供已部署网站的域名。
 * 继续兼容旧变量名，已有的 .env.local 无需立即修改也能正常工作。
 */
export const DEPLOY_DOMAIN = removeTrailingSlash(
  import.meta.env.VITE_DEPLOY_DOMAIN || import.meta.env.VITE_DEPLOY_BASE_URL || 'http://localhost',
)

/** 生成文件通过后端静态资源接口预览，路径统一从 API 地址派生。 */
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

/** EventSource 需要绝对地址，这里同时兼容 /api 和完整域名两种配置。 */
export function getApiBaseUrl() {
  return new URL(`${API_BASE_URL}/`, window.location.origin)
}

/**
 * 根据应用生成方式和 ID 构造实时预览地址，并附加缓存刷新参数。
 *
 * HTML 模式生成的 index.html 就在应用目录根部，可以直接访问目录地址。Vue 工程需要先
 * 由 Vite 编译，浏览器真正能运行的是 dist/index.html，因此要把构建目录加入预览路径。
 */
export function getStaticPreviewUrl(codeGenType?: string, appId?: string, cacheKey = Date.now()) {
  if (!codeGenType || !appId) return ''

  const staticBaseUrl = new URL(`${STATIC_BASE_URL}/`, window.location.origin)
  const directoryName = `${encodeURIComponent(codeGenType)}_${encodeURIComponent(appId)}`
  const resourcePath =
    codeGenType === CodeGenTypeEnum.VUE_PROJECT
      ? `${directoryName}/dist/index.html`
      : `${directoryName}/`
  const previewUrl = new URL(resourcePath, staticBaseUrl)
  previewUrl.searchParams.set('t', String(cacheKey))
  return previewUrl.toString()
}

/** 根据部署标识构造由 Nginx 提供的公开访问地址。 */
export function getDeployUrl(deployKey?: string) {
  if (!deployKey) return ''
  return `${DEPLOY_DOMAIN}/${encodeURIComponent(deployKey)}/`
}
