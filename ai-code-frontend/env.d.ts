/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DEPLOY_DOMAIN?: string
  /** 兼容早期版本使用的部署域名变量。 */
  readonly VITE_DEPLOY_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
