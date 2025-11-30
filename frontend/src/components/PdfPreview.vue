<script setup lang="ts">
import { ref, watch, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import type { PDFDocumentProxy, RenderParameters } from 'pdfjs-dist/types/src/display/api'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.js?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerSrc

const pdfBufferCache = new Map<string, ArrayBuffer>()
const zoomCache = new Map<string, number>()

const props = defineProps<{
  url: string
}>()

const emit = defineEmits<{
  zoomChange: [value: number]
}>()

const containerRef = ref<HTMLElement | null>(null)
const pagesWrapRef = ref<HTMLElement | null>(null)
const loading = ref(false)
const showLoading = ref(false)
const error = ref<string | null>(null)
const ready = ref(false)
const zoom = ref(100)
const autoFit = ref(true)
const transitionEnabled = ref(true)
let pdfDoc: PDFDocumentProxy | null = null
let basePageSize: { width: number; height: number } | null = null
let loadingTimer: number | null = null
let renderToken = 0
let resizeTimer: number | null = null

const getCacheKey = () => props.url

const getCachedZoom = () => {
  const cacheKey = getCacheKey()
  if (!cacheKey) return undefined

  if (zoomCache.has(cacheKey)) return zoomCache.get(cacheKey)

  const stored = localStorage.getItem(`pdf-zoom:${cacheKey}`)
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
  localStorage.setItem(`pdf-zoom:${cacheKey}`, String(value))
}

const startLoadingDelay = () => {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
  }
  loadingTimer = window.setTimeout(() => {
    if (loading.value) {
      showLoading.value = true
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

const resetZoom = () => {
  zoom.value = 100
  renderAllPages()
}

const cleanupCanvases = () => {
  if (!pagesWrapRef.value) return
  pagesWrapRef.value.innerHTML = ''
}

const loadPdf = async () => {
  if (!containerRef.value) return

  if (!props.url) {
    error.value = '未找到 PDF 地址'
    ready.value = false
    return
  }

  loading.value = true
  showLoading.value = false
  startLoadingDelay()
  error.value = null
  ready.value = false
  cleanupCanvases()
  pdfDoc = null
  basePageSize = null

  try {
    const cacheKey = getCacheKey()
    const cachedZoom = getCachedZoom()

    let buffer = cacheKey ? pdfBufferCache.get(cacheKey) : undefined
    if (!buffer) {
      const res = await fetch(props.url)
      if (!res.ok) throw new Error(`获取 PDF 失败 (${res.status})`)
      buffer = await res.arrayBuffer()
      if (cacheKey) pdfBufferCache.set(cacheKey, buffer)
    }

    const loadingTask = pdfjsLib.getDocument({
      data: buffer,
      cMapUrl: 'https://unpkg.com/pdfjs-dist@3.11.174/cmaps/',
      cMapPacked: true,
      standardFontDataUrl: 'https://unpkg.com/pdfjs-dist@3.11.174/standard_fonts/',
      enableXfa: true,
    })

    pdfDoc = await loadingTask.promise
    const firstPage = await pdfDoc.getPage(1)
    const viewport = firstPage.getViewport({ scale: 1 })
    basePageSize = { width: viewport.width, height: viewport.height }

    if (cachedZoom !== undefined) {
      zoom.value = cachedZoom
      await renderAllPages({ skipTransition: true })
      containerRef.value.scrollTop = 0
    } else {
      await nextTick()
      fitToWidth()
    }
    ready.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'PDF 预览失败'
  } finally {
    stopLoadingDelay()
    loading.value = false
  }
}

const ensureCanvas = (pageNum: number) => {
  if (!pagesWrapRef.value) return null
  let pageWrap = pagesWrapRef.value.querySelector(`[data-page="${pageNum}"]`) as HTMLDivElement | null
  if (!pageWrap) {
    pageWrap = document.createElement('div')
    pageWrap.dataset.page = String(pageNum)
    pageWrap.className = 'pdf-page'
    const canvas = document.createElement('canvas')
    canvas.className = 'bg-white shadow-md rounded'
    pageWrap.appendChild(canvas)
    pagesWrapRef.value.appendChild(pageWrap)
  }
  return pageWrap.querySelector('canvas') as HTMLCanvasElement | null
}

const renderAllPages = async (options?: { skipTransition?: boolean }) => {
  if (!pdfDoc || !containerRef.value) return
  const token = ++renderToken
  const scaleValue = zoom.value / 100
  const outputScale = window.devicePixelRatio || 1

  if (options?.skipTransition) {
    transitionEnabled.value = false
    containerRef.value.style.transition = 'none'
  }

  for (let i = 1; i <= pdfDoc.numPages; i++) {
    if (token !== renderToken) return
    const page = await pdfDoc.getPage(i)
    const viewport = page.getViewport({ scale: scaleValue })
    const canvas = ensureCanvas(i)
    if (!canvas) continue

    const context = canvas.getContext('2d')
    if (!context) continue

    canvas.width = Math.floor(viewport.width * outputScale)
    canvas.height = Math.floor(viewport.height * outputScale)
    canvas.style.width = `${Math.floor(viewport.width)}px`
    canvas.style.height = `${Math.floor(viewport.height)}px`

    const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : undefined

    const renderContext: RenderParameters = {
      canvasContext: context,
      transform,
      viewport,
    }
    await page.render(renderContext).promise
  }

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

const fitToWidth = () => {
  if (!containerRef.value || !basePageSize) return
  const padding = 48
  const availableWidth = Math.max(200, containerRef.value.clientWidth - padding)
  const scale = Math.max(30, Math.min(300, Math.floor((availableWidth / basePageSize.width) * 100)))
  zoom.value = scale
  saveCachedZoom(scale)
  renderAllPages({ skipTransition: true })
  containerRef.value.scrollTop = 0
}

const handleResize = () => {
  if (!autoFit.value || !ready.value) return
  if (resizeTimer) {
    clearTimeout(resizeTimer)
  }
  resizeTimer = window.setTimeout(() => {
    fitToWidth()
  }, 150)
}

const zoomIn = () => {
  autoFit.value = false
  zoom.value = Math.min(300, zoom.value + 10)
  saveCachedZoom(zoom.value)
  renderAllPages()
}

const zoomOut = () => {
  autoFit.value = false
  zoom.value = Math.max(30, zoom.value - 10)
  saveCachedZoom(zoom.value)
  renderAllPages()
}

watch(
  () => props.url,
  () => {
    zoom.value = 100
    autoFit.value = true
    loadPdf()
  },
  { flush: 'post' }
)

onMounted(() => {
  loadPdf()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
    loadingTimer = null
  }
  if (resizeTimer) {
    clearTimeout(resizeTimer)
    resizeTimer = null
  }
  window.removeEventListener('resize', handleResize)
})

defineExpose({
  zoomIn,
  zoomOut,
  resetZoom,
  reload: loadPdf,
  getZoom: () => zoom.value,
  autoFit: () => {
    autoFit.value = true
    fitToWidth()
  },
})
</script>

<template>
  <div
    ref="containerRef"
    :class="[
      'pdf-container flex-1 relative overflow-auto origin-top',
      transitionEnabled ? 'transition-transform' : ''
    ]"
  >
    <div
      v-if="showLoading"
      class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none z-10"
    >
      <div class="pointer-events-auto bg-white/92 border border-slate-200 shadow-lg rounded-xl px-6 py-5 flex flex-col items-center gap-3 max-w-[320px] w-fit text-center text-sky-600">
        <div class="w-10 h-10 border-2 border-sky-100 border-t-sky-500 rounded-full animate-spin"></div>
        <div class="text-sm font-medium">正在加载 PDF...</div>
        <div class="text-xs text-slate-400">已缓存内容，将加速后续预览</div>
      </div>
    </div>

    <div
      v-if="error"
      class="absolute inset-0 bg-white/80 backdrop-blur-sm flex flex-col items-center justify-center text-red-600 gap-2 px-4 text-center z-10"
    >
      <i class="fa-solid fa-circle-exclamation text-2xl"></i>
      <span class="text-sm font-medium">{{ error }}</span>
    </div>
    <div ref="pagesWrapRef" class="pdf-pages relative z-0 flex flex-col items-center"></div>
  </div>
</template>

<style scoped>
.pdf-container {
  min-height: 260px;
  padding: 12px;
}

.pdf-page {
  margin: 0;
  display: flex;
  justify-content: center;
  padding: 12px 0;
  position: relative;
}

.pdf-pages {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
</style>
