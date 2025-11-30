<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { renderAsync } from 'docx-preview'
import type { Options } from 'docx-preview'

const docxBufferCache = new Map<string, ArrayBuffer>()
const docHtmlCache = new Map<string, string>()
const zoomCache = new Map<string, number>() // 缓存每个文件的适配缩放比例

const props = defineProps<{
  url: string
  fileName: string
  fileType?: string
  htmlUrl?: string
}>()

const emit = defineEmits<{
  zoomChange: [value: number]
}>()

const containerRef = ref<HTMLElement | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const zoom = ref(100)
const ready = ref(false)
const showLoading = ref(false)
let loadingTimer: number | null = null
const autoFit = ref(true)
const transitionEnabled = ref(true)

const getCacheKey = () => props.url || props.htmlUrl

const getCachedZoom = () => {
  const cacheKey = getCacheKey()
  if (!cacheKey) return undefined

  if (zoomCache.has(cacheKey)) {
    return zoomCache.get(cacheKey)
  }

  const stored = localStorage.getItem(`doc-zoom:${cacheKey}`)
  if (stored !== null) {
    const parsed = parseInt(stored, 10)
    if (!Number.isNaN(parsed)) {
      zoomCache.set(cacheKey, parsed)
      return parsed
    }
  }

  return undefined
}

const saveCachedZoom = (value: number) => {
  const cacheKey = getCacheKey()
  if (!cacheKey) return
  zoomCache.set(cacheKey, value)
  localStorage.setItem(`doc-zoom:${cacheKey}`, String(value))
}

const resetZoom = () => {
  zoom.value = 100
  applyZoom()
}

const loadDoc = async () => {
  if (!containerRef.value) return

  if (!props.url && !props.htmlUrl) {
    containerRef.value.innerHTML = ''
    error.value = null
    ready.value = false
    return
  }

  const isDoc = props.fileType && props.fileType.toLowerCase() === 'doc'
  if (isDoc) {
    await renderDocHtml()
    return
  }

  loading.value = true
  showLoading.value = false
  startLoadingDelay()
  error.value = null
  containerRef.value.innerHTML = ''
  ready.value = false

  try {
    if (!props.url) {
      throw new Error('未找到文档地址')
    }

    const cachedZoom = getCachedZoom()

    let buffer = docxBufferCache.get(props.url)
    if (!buffer) {
      const res = await fetch(props.url)
      if (!res.ok) {
        throw new Error(`获取文档失败 (${res.status})`)
      }
      buffer = await res.arrayBuffer()
      docxBufferCache.set(props.url, buffer)
    }

    const options: Partial<Options> = {
      className: 'docx',
      inWrapper: true,
      ignoreWidth: false,
      ignoreHeight: false,
      breakPages: true,
      useBase64URL: true,
      experimental: true,
      renderHeaders: true,
      renderFooters: true,
      renderFootnotes: true,
      renderEndnotes: true,
    }

    await nextTick()
    await renderAsync(buffer, containerRef.value, undefined, options)
    if (cachedZoom !== undefined) {
      zoom.value = cachedZoom
      applyZoom({ skipTransition: true })
      containerRef.value.scrollTop = 0
    } else {
      applyZoom()
    }
    ready.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '文档预览失败'
  } finally {
    stopLoadingDelay()
    loading.value = false
  }
}

const renderDocHtml = async () => {
  if (!containerRef.value) return

  loading.value = true
  showLoading.value = false
  startLoadingDelay()
  error.value = null
  containerRef.value.innerHTML = ''
  ready.value = false

  try {
    if (!props.htmlUrl) throw new Error('未找到预览地址')

    const cachedZoom = getCachedZoom()

    let html = docHtmlCache.get(props.htmlUrl)
    if (!html) {
      const res = await fetch(props.htmlUrl)
      if (!res.ok) throw new Error(`获取预览失败 (${res.status})`)
      const payload = await res.json()
      if (!payload.success) throw new Error(payload.message || '预览失败')
      html = payload.data as string
      docHtmlCache.set(props.htmlUrl, html)
    }

    containerRef.value.innerHTML = html
    if (cachedZoom !== undefined) {
      zoom.value = cachedZoom
      applyZoom({ skipTransition: true })
      containerRef.value.scrollTop = 0
    } else {
      applyZoom()
    }
    ready.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '文档预览失败'
  } finally {
    stopLoadingDelay()
    loading.value = false
  }
}

const zoomIn = () => {
  autoFit.value = false
  zoom.value = Math.min(200, zoom.value + 10)
  applyZoom()
}

const zoomOut = () => {
  autoFit.value = false
  zoom.value = Math.max(50, zoom.value - 10)
  applyZoom()
}

const applyZoom = (options?: { skipTransition?: boolean }) => {
  if (!containerRef.value) return

  if (options?.skipTransition) {
    transitionEnabled.value = false
    containerRef.value.style.transition = 'none'
  }

  containerRef.value.style.transform = `scale(${zoom.value / 100})`
  containerRef.value.style.marginTop = zoom.value < 100 ? '0' : '12px'
  emit('zoomChange', zoom.value)

  if (options?.skipTransition) {
    requestAnimationFrame(() => {
      transitionEnabled.value = true
      if (containerRef.value) {
        containerRef.value.style.transition = ''
      }
    })
  }
}

watch(
  () => [props.url, props.htmlUrl],
  () => {
    resetZoom()
    loadDoc()
  },
  { flush: 'post' }
)

onMounted(() => {
  loadDoc()
})

defineExpose({
  zoomIn,
  zoomOut,
  resetZoom,
  reload: loadDoc,
  getZoom: () => zoom.value,
  autoFit: () => {
    autoFit.value = true
    fitToWidth()
  },
})

const startLoadingDelay = () => {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
  }
  loadingTimer = window.setTimeout(() => {
    if (loading.value) {
      showLoading.value = true;
    }
  }, 500)
}

const stopLoadingDelay = () => {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
    loadingTimer = null
  }
  showLoading.value = false
}

const fitToWidth = (retryCount = 0) => {
  if (!containerRef.value) return

  // docx-preview 渲染结构: containerRef > .docx-wrapper > .docx (页面)
  const docPage = containerRef.value.querySelector('.docx') as HTMLElement | null
  if (!docPage) {
    // 文档还未渲染，延迟重试
    if (retryCount < 10) {
      setTimeout(() => fitToWidth(retryCount + 1), 100)
    }
    return
  }

  const currentScale = zoom.value / 100
  const docRect = docPage.getBoundingClientRect()
  const containerRect = containerRef.value.getBoundingClientRect()

  if (docRect.width === 0) {
    // 宽度为0，可能还在渲染中，延迟重试
    if (retryCount < 10) {
      setTimeout(() => fitToWidth(retryCount + 1), 100)
    }
    return
  }

  const docWidthUnscaled = docPage.offsetWidth || docRect.width / Math.max(currentScale, 0.01)
  const padding = 48
  const availableWidth = Math.max(200, (containerRef.value.clientWidth || containerRect.width) - padding)
  const scale = Math.max(30, Math.min(200, Math.floor((availableWidth / docWidthUnscaled) * 100)))
  zoom.value = scale
  applyZoom()
  containerRef.value.scrollTop = 0

  // 保存适配后的缩放比例到缓存
  saveCachedZoom(scale)
}

watch(ready, (val) => {
  if (val && autoFit.value) {
    // 检查是否有缓存的缩放比例
    const cachedZoom = getCachedZoom()

    if (cachedZoom !== undefined) {
      // 缓存命中时直接使用已应用的缩放比例，避免重复动画
      containerRef.value?.scrollTo({ top: 0 })
      return
    }

    // 没有缓存，执行自适应计算
    requestAnimationFrame(() => {
      setTimeout(() => fitToWidth(), 100)
    })
  }
})
</script>

<template>
  <div
    ref="containerRef"
    :class="[
      'docx-container flex-1 relative overflow-auto origin-top',
      transitionEnabled ? 'transition-transform' : ''
    ]"
  >
    <div
      v-if="showLoading"
      class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none z-10"
    >
      <div class="pointer-events-auto bg-white/92 border border-slate-200 shadow-lg rounded-xl px-6 py-5 flex flex-col items-center gap-3 max-w-[320px] w-fit text-center text-sky-600">
        <div class="w-10 h-10 border-2 border-sky-100 border-t-sky-500 rounded-full animate-spin"></div>
        <div class="text-sm font-medium">正在渲染文档...</div>
        <div class="text-xs text-slate-400">首次加载稍慢，将自动缓存以加速后续预览</div>
      </div>
    </div>

    <div
      v-if="error"
      class="absolute inset-0 bg-white/80 backdrop-blur-sm flex flex-col items-center justify-center text-red-600 gap-2 px-4 text-center z-10"
    >
      <i class="fa-solid fa-circle-exclamation text-2xl"></i>
      <span class="text-sm font-medium">{{ error }}</span>
    </div>
  </div>
</template>

<style scoped>
.docx-container :deep(.docx) {
  margin: 0 auto 16px auto;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 24px;
}

.docx-container {
  min-height: 260px;
}

.docx-container :deep(.docx-wrapper) {
  background: transparent;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.docx-container :deep(.docx) p {
  margin: 0;
}
</style>
