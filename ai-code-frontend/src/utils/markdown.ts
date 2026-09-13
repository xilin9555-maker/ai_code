import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import xml from 'highlight.js/lib/languages/xml'

// 代码生成目前只会返回网页相关语言，按需注册可以避免把所有语言定义打进前端产物。
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)

const markdownRenderer = new MarkdownIt({
  breaks: true,
  linkify: true,
  // AI 回复最终会通过 v-html 放入页面。关闭原始 HTML 后，网页代码只会作为文本展示，
  // 其中的 script、事件属性和其他标签不会在平台页面中执行。
  html: false,
  highlight(code, language) {
    const normalizedLanguage = language.trim().toLowerCase()
    const highlightedCode =
      normalizedLanguage && hljs.getLanguage(normalizedLanguage)
        ? hljs.highlight(code, {
            language: normalizedLanguage,
            ignoreIllegals: true,
          }).value
        : hljs.highlightAuto(code).value

    return `<pre class="hljs"><code>${highlightedCode}</code></pre>`
  },
})

/**
 * 将已经接收完整的 AI 回复转换成可展示的 Markdown HTML。
 * MarkdownIt 负责代码块、段落和列表结构，Highlight.js 负责代码中的语法颜色。
 */
export function renderMarkdown(content: string) {
  return markdownRenderer.render(content)
}
