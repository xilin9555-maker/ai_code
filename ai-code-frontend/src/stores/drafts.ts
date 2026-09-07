import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface IdeaDraft {
  id: string
  prompt: string
  updatedAt: string
}
const STORAGE_KEY = 'lingbuild.drafts.v1'
function readDrafts(): IdeaDraft[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(value)) return []
    return value.filter(
      (item): item is IdeaDraft =>
        item !== null &&
        typeof item === 'object' &&
        typeof item.id === 'string' &&
        typeof item.prompt === 'string' &&
        typeof item.updatedAt === 'string' &&
        Number.isFinite(Date.parse(item.updatedAt)),
    )
  } catch {
    return []
  }
}

export const useDraftStore = defineStore('drafts', () => {
  const drafts = ref<IdeaDraft[]>(readDrafts())
  const prompt = ref('')
  const editingId = ref<string | null>(null)
  function persist(next: IdeaDraft[]) {
    // 写入成功后再更新页面，避免存储失败却提示已保存。
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    drafts.value = next
  }
  function save() {
    const value = prompt.value.trim()
    if (value.length < 3 || value.length > 2000) throw new Error('请填写 3 至 2000 字的创意描述')
    const draft = {
      id: editingId.value || crypto.randomUUID(),
      prompt: value,
      updatedAt: new Date().toISOString(),
    }
    persist([draft, ...drafts.value.filter((item) => item.id !== draft.id)])
    editingId.value = null
    prompt.value = ''
  }
  function edit(draft: IdeaDraft) {
    editingId.value = draft.id
    prompt.value = draft.prompt
  }
  function remove(id: string) {
    persist(drafts.value.filter((item) => item.id !== id))
    if (editingId.value === id) {
      editingId.value = null
      prompt.value = ''
    }
  }
  function useExample(value: string) {
    editingId.value = null
    prompt.value = value
  }
  return { drafts, prompt, editingId, save, edit, remove, useExample }
})
