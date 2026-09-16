const APP_MODE_STORAGE_PREFIX = 'ai-code:generation-mode:'
const NEW_APP_MODE_STORAGE_KEY = `${APP_MODE_STORAGE_PREFIX}new-app`

/** 从浏览器存储读取模式；存储不可用或没有记录时默认使用普通模式。 */
function readMode(storageKey: string) {
  try {
    return window.localStorage.getItem(storageKey) === 'agent'
  } catch {
    return false
  }
}

/** 保存模式偏好；浏览器禁用本地存储时不影响当前页面继续使用。 */
function writeMode(storageKey: string, enabled: boolean) {
  try {
    window.localStorage.setItem(storageKey, enabled ? 'agent' : 'normal')
  } catch {
    // 当前页面仍保留响应式状态，不需要因为偏好保存失败而阻止生成。
  }
}

/** 读取主页创建新应用时最后选择的模式。 */
export function loadNewAppAgentMode() {
  return readMode(NEW_APP_MODE_STORAGE_KEY)
}

/** 保存主页创建新应用时的模式偏好。 */
export function saveNewAppAgentMode(enabled: boolean) {
  writeMode(NEW_APP_MODE_STORAGE_KEY, enabled)
}

/** 读取指定应用最近使用的生成模式。 */
export function loadAppAgentMode(appId: string) {
  return readMode(`${APP_MODE_STORAGE_PREFIX}${appId}`)
}

/** 保存指定应用的生成模式，普通模式与工作流模式共用同一应用记录。 */
export function saveAppAgentMode(appId: string, enabled: boolean) {
  writeMode(`${APP_MODE_STORAGE_PREFIX}${appId}`, enabled)
}
