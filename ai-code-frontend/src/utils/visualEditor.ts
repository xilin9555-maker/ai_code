/**
 * 可视化编辑器只负责父页面与预览 iframe 之间的交互，不参与业务状态管理。
 *
 * 预览页面和工作台同源时，父页面会动态注入一个很小的选择器脚本。脚本使用覆盖层标记
 * 悬浮、选中的元素，不修改用户生成页面自身的 class 或 style，下载的源码也不会被污染。
 */

export interface ElementInfo {
  tagName: string
  id: string
  className: string
  attributes: Record<string, string>
  textContent: string
  htmlSnippet: string
  selector: string
  selectorMatchCount: number
  pagePath: string
  rect: {
    top: number
    left: number
    width: number
    height: number
  }
}

export interface VisualEditorOptions {
  onElementSelected?: (elementInfo: ElementInfo) => void
}

export type VisualEditorActivationResult =
  | { ok: true }
  | { ok: false; reason: 'not-ready' | 'cross-origin' | 'inject-failed' }

type BridgeMessage = {
  channel: string
  type: string
  data?: unknown
}

type PreviewWindow = Window & {
  __LINGOU_VISUAL_EDITOR__?: unknown
}

const BRIDGE_CHANNEL = 'lingou-visual-editor'
const SCRIPT_ELEMENT_ID = 'lingou-visual-editor-script'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function toSafeText(value: unknown, maxLength: number) {
  return typeof value === 'string' ? value.slice(0, maxLength) : ''
}

function toFiniteNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

/** 只接受当前预览页回传的、结构完整的元素信息。 */
function normalizeElementInfo(value: unknown): ElementInfo | undefined {
  if (!isRecord(value) || !isRecord(value.rect)) return undefined

  const tagName = toSafeText(value.tagName, 40).toUpperCase()
  const selector = toSafeText(value.selector, 1000)
  if (!tagName || !selector) return undefined

  const attributes: Record<string, string> = {}
  if (isRecord(value.attributes)) {
    Object.entries(value.attributes)
      .slice(0, 16)
      .forEach(([name, attributeValue]) => {
        if (typeof attributeValue === 'string') {
          attributes[name.slice(0, 80)] = attributeValue.slice(0, 300)
        }
      })
  }

  return {
    tagName,
    id: toSafeText(value.id, 200),
    className: toSafeText(value.className, 500),
    attributes,
    textContent: toSafeText(value.textContent, 300),
    htmlSnippet: toSafeText(value.htmlSnippet, 1000),
    selector,
    selectorMatchCount:
      typeof value.selectorMatchCount === 'number' && Number.isFinite(value.selectorMatchCount)
        ? Math.max(0, Math.floor(value.selectorMatchCount))
        : 0,
    pagePath: toSafeText(value.pagePath, 500),
    rect: {
      top: toFiniteNumber(value.rect.top),
      left: toFiniteNumber(value.rect.left),
      width: toFiniteNumber(value.rect.width),
      height: toFiniteNumber(value.rect.height),
    },
  }
}

/**
 * 该函数会通过 toString 注入 iframe，函数体必须保持自包含，不能引用文件顶层变量。
 * 参数和类型标注会在 Vite 编译阶段移除，最终注入的是普通浏览器 JavaScript。
 */
function installVisualEditorBridge(channel: string) {
  type EditableElement = HTMLElement | SVGElement
  type EditorWindow = Window & {
    __LINGOU_VISUAL_EDITOR__?: {
      setEditMode: (enabled: boolean) => void
      clearSelection: () => void
      destroy: () => void
    }
  }

  const editorWindow = window as EditorWindow
  const existingEditor = editorWindow.__LINGOU_VISUAL_EDITOR__
  if (existingEditor) {
    existingEditor.setEditMode(true)
    return
  }

  const overlayAttribute = 'data-lingou-visual-editor'
  const styleId = 'lingou-visual-editor-styles'
  let isEditMode = true
  let hoverElement: EditableElement | null = null
  let selectedElement: EditableElement | null = null

  const style = document.createElement('style')
  style.id = styleId
  style.textContent = `
    [${overlayAttribute}] {
      position: fixed !important;
      display: none;
      margin: 0 !important;
      padding: 0 !important;
      pointer-events: none !important;
      box-sizing: border-box !important;
      background: transparent !important;
      z-index: 2147483647 !important;
    }
    [${overlayAttribute}="hover"] {
      border: 2px dashed #d65a36 !important;
      box-shadow: 0 0 0 2px rgba(214, 90, 54, 0.1) !important;
    }
    [${overlayAttribute}="selected"] {
      border: 3px solid #a94227 !important;
      box-shadow: 0 0 0 3px rgba(169, 66, 39, 0.16) !important;
    }
  `
  ;(document.head || document.documentElement).appendChild(style)

  function createOverlay(kind: 'hover' | 'selected') {
    const overlay = document.createElement('div')
    overlay.setAttribute(overlayAttribute, kind)
    overlay.setAttribute('aria-hidden', 'true')
    ;(document.body || document.documentElement).appendChild(overlay)
    return overlay
  }

  const hoverOverlay = createOverlay('hover')
  const selectedOverlay = createOverlay('selected')

  function isEditableElement(value: EventTarget | null): value is EditableElement {
    return (
      value instanceof Element &&
      value !== document.documentElement &&
      value !== document.body &&
      !value.hasAttribute(overlayAttribute)
    )
  }

  function placeOverlay(overlay: HTMLDivElement, element: EditableElement | null) {
    if (!isEditMode || !element || !element.isConnected) {
      overlay.style.setProperty('display', 'none', 'important')
      return
    }

    const rect = element.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) {
      overlay.style.setProperty('display', 'none', 'important')
      return
    }

    // 生成页面可能包含全局的 !important 样式，覆盖层的几何属性也需要相同优先级。
    overlay.style.setProperty('display', 'block', 'important')
    overlay.style.setProperty('top', `${rect.top}px`, 'important')
    overlay.style.setProperty('left', `${rect.left}px`, 'important')
    overlay.style.setProperty('width', `${rect.width}px`, 'important')
    overlay.style.setProperty('height', `${rect.height}px`, 'important')
  }

  function refreshOverlays() {
    if (hoverElement === selectedElement) hoverElement = null
    placeOverlay(hoverOverlay, hoverElement)
    placeOverlay(selectedOverlay, selectedElement)
  }

  function escapeCssIdentifier(value: string) {
    if (window.CSS?.escape) return window.CSS.escape(value)
    return value.replace(/([^a-zA-Z0-9_-])/g, '\\$1')
  }

  function countSelectorMatches(selector: string) {
    try {
      return document.querySelectorAll(selector).length
    } catch {
      return 0
    }
  }

  function selectorUniquelyMatches(selector: string, element: EditableElement) {
    try {
      const matches = document.querySelectorAll(selector)
      return matches.length === 1 && matches[0] === element
    } catch {
      return false
    }
  }

  /** 生成足够稳定且不依赖编辑器临时标记的 CSS 路径。 */
  function generateSelector(element: EditableElement) {
    const path: string[] = []
    let current: Element | null = element

    while (current && current !== document.body && current !== document.documentElement) {
      const tagName = current.tagName.toLowerCase()
      const currentId = current.getAttribute('id')
      if (currentId) {
        const idSelector = `#${escapeCssIdentifier(currentId)}`
        if (selectorUniquelyMatches(idSelector, current as EditableElement)) {
          path.unshift(idSelector)
          break
        }
      }

      const classNames = Array.from(current.classList)
        .filter((className) => className && !className.startsWith('lingou-'))
        .slice(0, 3)
      let segment = tagName
      if (classNames.length) {
        segment += classNames.map((className) => `.${escapeCssIdentifier(className)}`).join('')
      }

      const parentElement: Element | null = current.parentElement
      if (parentElement) {
        const sameTagSiblings = Array.from(parentElement.children).filter(
          (sibling: Element) => sibling.tagName === current?.tagName,
        )
        if (sameTagSiblings.length > 1) {
          segment += `:nth-of-type(${sameTagSiblings.indexOf(current) + 1})`
        }
      }

      path.unshift(segment)
      current = parentElement
    }

    const semanticSelector = path.join(' > ')
    if (selectorUniquelyMatches(semanticSelector, element)) return semanticSelector

    // 类名重复或 DOM 结构特殊时，退化为从 body 开始的 nth-child 精确路径。
    const exactPath: string[] = []
    let exactCurrent: Element | null = element
    while (exactCurrent && exactCurrent !== document.body) {
      const parentElement: Element | null = exactCurrent.parentElement
      let segment = exactCurrent.tagName.toLowerCase()
      if (parentElement) {
        segment += `:nth-child(${Array.from(parentElement.children).indexOf(exactCurrent) + 1})`
      }
      exactPath.unshift(segment)
      exactCurrent = parentElement
    }
    exactPath.unshift('body')
    return exactPath.join(' > ')
  }

  /** 提取能帮助 AI 识别语义、但不会把整份页面样式传回去的属性。 */
  function getUsefulAttributes(element: EditableElement) {
    const attributes: Record<string, string> = {}
    const usefulNames = new Set([
      'alt',
      'aria-label',
      'href',
      'name',
      'placeholder',
      'role',
      'src',
      'title',
      'type',
      'value',
    ])
    Array.from(element.attributes).forEach((attribute) => {
      if (
        usefulNames.has(attribute.name) ||
        attribute.name.startsWith('data-') ||
        attribute.name.startsWith('aria-')
      ) {
        attributes[attribute.name] = attribute.value.slice(0, 300)
      }
    })
    return attributes
  }

  function getPagePath() {
    const query = new URLSearchParams(window.location.search)
    // t 只用于让 iframe 绕过缓存，不属于生成网站自身的页面信息。
    query.delete('t')
    const search = query.toString() ? `?${query.toString()}` : ''
    return `${search}${window.location.hash}` || '/'
  }

  function getElementInfo(element: EditableElement) {
    const rect = element.getBoundingClientRect()
    const selector = generateSelector(element)
    return {
      tagName: element.tagName,
      id: element.getAttribute('id') || '',
      className: element.getAttribute('class') || '',
      attributes: getUsefulAttributes(element),
      textContent: (element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 300),
      htmlSnippet: element.outerHTML.replace(/\s+/g, ' ').trim().slice(0, 1000),
      selector,
      selectorMatchCount: countSelectorMatches(selector),
      // hash 路由和业务查询参数能区分不同页面，同时不暴露静态预览目录。
      pagePath: getPagePath(),
      rect: {
        top: Math.round(rect.top),
        left: Math.round(rect.left),
        width: Math.round(rect.width),
        height: Math.round(rect.height),
      },
    }
  }

  function postToParent(type: string, data?: unknown) {
    window.parent.postMessage({ channel, type, data }, window.location.origin)
  }

  function clearSelection() {
    selectedElement = null
    selectedOverlay.style.setProperty('display', 'none', 'important')
  }

  function clearAllEffects() {
    hoverElement = null
    clearSelection()
    hoverOverlay.style.setProperty('display', 'none', 'important')
  }

  function setEditMode(enabled: boolean) {
    isEditMode = enabled
    if (!enabled) clearAllEffects()
  }

  function handlePointerOver(event: PointerEvent) {
    if (!isEditMode || !isEditableElement(event.target) || event.target === selectedElement) return
    hoverElement = event.target
    placeOverlay(hoverOverlay, hoverElement)
  }

  function handlePointerOut(event: PointerEvent) {
    if (!isEditMode || event.target !== hoverElement) return
    hoverElement = null
    hoverOverlay.style.setProperty('display', 'none', 'important')
  }

  function handleClick(event: MouseEvent) {
    if (!isEditMode || !isEditableElement(event.target)) return

    // 编辑模式只选择元素，不触发生成页面原本的跳转、提交或业务事件。
    event.preventDefault()
    event.stopPropagation()
    event.stopImmediatePropagation()
    selectedElement = event.target
    hoverElement = null
    refreshOverlays()
    postToParent('ELEMENT_SELECTED', { elementInfo: getElementInfo(selectedElement) })
  }

  function handleParentMessage(event: MessageEvent) {
    if (event.source !== window.parent || event.origin !== window.location.origin) return
    const message = event.data as BridgeMessage
    if (!message || message.channel !== channel) return

    switch (message.type) {
      case 'SET_EDIT_MODE':
        setEditMode(Boolean((message.data as { enabled?: unknown })?.enabled))
        break
      case 'CLEAR_SELECTION':
        clearSelection()
        break
      case 'CLEAR_ALL_EFFECTS':
        clearAllEffects()
        break
      case 'DESTROY':
        destroy()
        break
    }
  }

  function destroy() {
    document.removeEventListener('pointerover', handlePointerOver, true)
    document.removeEventListener('pointerout', handlePointerOut, true)
    document.removeEventListener('click', handleClick, true)
    window.removeEventListener('scroll', refreshOverlays, true)
    window.removeEventListener('resize', refreshOverlays)
    window.removeEventListener('message', handleParentMessage)
    observer.disconnect()
    hoverOverlay.remove()
    selectedOverlay.remove()
    style.remove()
    delete editorWindow.__LINGOU_VISUAL_EDITOR__
  }

  const observer = new MutationObserver(() => {
    if (!isEditMode) return
    if (selectedElement && !selectedElement.isConnected) clearSelection()
    if (hoverElement && !hoverElement.isConnected) hoverElement = null
    refreshOverlays()
  })
  observer.observe(document.documentElement, { childList: true, subtree: true })

  document.addEventListener('pointerover', handlePointerOver, true)
  document.addEventListener('pointerout', handlePointerOut, true)
  document.addEventListener('click', handleClick, true)
  window.addEventListener('scroll', refreshOverlays, true)
  window.addEventListener('resize', refreshOverlays)
  window.addEventListener('message', handleParentMessage)

  editorWindow.__LINGOU_VISUAL_EDITOR__ = { setEditMode, clearSelection, destroy }
  postToParent('EDITOR_READY')
}

export class VisualEditor {
  private iframe: HTMLIFrameElement | null = null
  private isEditMode = false

  constructor(private readonly options: VisualEditorOptions = {}) {}

  init(iframe: HTMLIFrameElement) {
    this.iframe = iframe
  }

  enableEditMode(): VisualEditorActivationResult {
    if (!this.iframe?.contentWindow) return { ok: false, reason: 'not-ready' }

    this.isEditMode = true
    const result = this.injectBridge()
    if (!result.ok) this.isEditMode = false
    return result
  }

  disableEditMode() {
    this.isEditMode = false
    this.sendMessage('SET_EDIT_MODE', { enabled: false })
    this.sendMessage('CLEAR_ALL_EFFECTS')
  }

  clearSelection() {
    this.sendMessage('CLEAR_SELECTION')
  }

  /** iframe 每次刷新都会得到一个新 document，需要重新注入桥接脚本。 */
  onIframeLoad(): VisualEditorActivationResult {
    if (!this.isEditMode) {
      this.sendMessage('CLEAR_ALL_EFFECTS')
      return { ok: true }
    }
    const result = this.injectBridge()
    if (!result.ok) this.isEditMode = false
    return result
  }

  handleIframeMessage(event: MessageEvent) {
    if (
      !this.iframe?.contentWindow ||
      event.source !== this.iframe.contentWindow ||
      event.origin !== window.location.origin ||
      !isRecord(event.data)
    ) {
      return
    }

    const message = event.data as BridgeMessage
    if (message.channel !== BRIDGE_CHANNEL || message.type !== 'ELEMENT_SELECTED') return
    if (!isRecord(message.data)) return

    const elementInfo = normalizeElementInfo(message.data.elementInfo)
    if (elementInfo) this.options.onElementSelected?.(elementInfo)
  }

  destroy() {
    this.sendMessage('DESTROY')
    this.iframe = null
    this.isEditMode = false
  }

  private sendMessage(type: string, data?: unknown) {
    this.iframe?.contentWindow?.postMessage(
      { channel: BRIDGE_CHANNEL, type, data },
      window.location.origin,
    )
  }

  private injectBridge(): VisualEditorActivationResult {
    if (!this.iframe) return { ok: false, reason: 'not-ready' }

    let frameDocument: Document | null
    try {
      frameDocument = this.iframe.contentDocument
    } catch {
      return { ok: false, reason: 'cross-origin' }
    }
    if (!frameDocument?.documentElement) return { ok: false, reason: 'not-ready' }

    try {
      const frameWindow = this.iframe.contentWindow as PreviewWindow | null
      let script = frameDocument.getElementById(SCRIPT_ELEMENT_ID)
      // CSP 等因素可能阻止脚本执行，此时不能仅凭 script 标签存在就误报初始化成功。
      if (script && !frameWindow?.__LINGOU_VISUAL_EDITOR__) {
        script.remove()
        script = null
      }
      if (!script) {
        const script = frameDocument.createElement('script')
        script.id = SCRIPT_ELEMENT_ID
        script.textContent = `;(${installVisualEditorBridge.toString()})(${JSON.stringify(BRIDGE_CHANNEL)});`
        ;(frameDocument.head || frameDocument.documentElement).appendChild(script)
      }
      if (!frameWindow?.__LINGOU_VISUAL_EDITOR__) {
        return { ok: false, reason: 'inject-failed' }
      }
      this.sendMessage('SET_EDIT_MODE', { enabled: true })
      return { ok: true }
    } catch (error) {
      return {
        ok: false,
        reason:
          error instanceof DOMException && error.name === 'SecurityError'
            ? 'cross-origin'
            : 'inject-failed',
      }
    }
  }
}
