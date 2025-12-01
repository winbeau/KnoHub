<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'

const props = defineProps<{
  url: string
  fileName?: string
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const content = ref('')
const copied = ref(false)

const escapeHtml = (str: string) =>
  str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const highlightVhdl = (code: string) => {
  const escaped = escapeHtml(code)
  const keywords = [
    'library',
    'use',
    'entity',
    'is',
    'port',
    'end',
    'architecture',
    'of',
    'signal',
    'begin',
    'process',
    'component',
    'map',
    'generic',
    'if',
    'then',
    'else',
    'elsif',
    'case',
    'when',
    'rising_edge',
    'falling_edge',
    'std_logic',
    'std_logic_vector',
    'downto',
    'to'
  ]
  const keywordPattern = new RegExp(`\\b(${keywords.join('|')})\\b`, 'gi')
  return escaped.replace(keywordPattern, '<span class="text-indigo-600 font-semibold">$1</span>')
}

const highlightedContent = computed(() => highlightVhdl(content.value))

const loadContent = async () => {
  loading.value = true
  error.value = null
  copied.value = false
  try {
    const res = await fetch(props.url)
    if (!res.ok) throw new Error(`加载失败 (${res.status})`)
    content.value = await res.text()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

const copyContent = async () => {
  if (!content.value) return
  try {
    await navigator.clipboard.writeText(content.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch (e) {
    console.error('Copy failed', e)
    return false
  }
  return true
}

watch(
  () => props.url,
  () => loadContent()
)

onMounted(() => {
  loadContent()
})

defineExpose({
  copyContent
})
</script>

<template>
  <div class="w-full h-full flex flex-col gap-3">
    <div class="flex-1 w-full overflow-auto border border-slate-200 rounded-xl bg-white text-slate-800 shadow-[0_10px_30px_-20px_rgba(15,23,42,0.3)]">
      <div v-if="loading" class="p-6 text-center text-slate-400">
        <i class="fa-solid fa-spinner fa-spin mr-2"></i> 加载中...
      </div>
      <div v-else-if="error" class="p-4 text-red-600 bg-red-50 border-b border-red-200">
        <i class="fa-solid fa-circle-exclamation mr-2"></i>{{ error }}
      </div>
      <pre
        v-else
        class="p-4 whitespace-pre-wrap text-xs leading-relaxed font-mono bg-gradient-to-br from-slate-50 via-white to-slate-100"
        v-html="highlightedContent"
      ></pre>
    </div>
  </div>
</template>
