<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'

const props = defineProps<{
  url: string
}>()

const emit = defineEmits<{
  (e: 'zoom-change', value: number): void
}>()

const containerRef = ref<HTMLElement | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)
const zoom = ref(100)

const clampZoom = (value: number) => Math.min(400, Math.max(10, value))

const emitZoom = () => emit('zoom-change', Math.round(zoom.value))

const setZoom = (value: number) => {
  zoom.value = clampZoom(value)
  emitZoom()
}

const zoomIn = () => setZoom(zoom.value + 10)
const zoomOut = () => setZoom(zoom.value - 10)

const autoFit = () => {
  const img = imageRef.value
  const container = containerRef.value || (img?.parentElement as HTMLElement | null)
  if (!container || !img || !img.naturalWidth || !img.naturalHeight) return

  // Leave some padding to avoid sticking to edges
  const padding = 32
  const availableWidth = Math.max(10, container.clientWidth - padding)
  const availableHeight = Math.max(10, container.clientHeight - padding)

  const scale = Math.min(
    availableWidth / img.naturalWidth,
    availableHeight / img.naturalHeight
  )

  setZoom(clampZoom(scale * 100))
}

const handleWheel = (e: WheelEvent) => {
  if (!e.ctrlKey) return
  e.preventDefault()
  const step = Math.abs(e.deltaY) > 50 ? 10 : 5
  const direction = e.deltaY < 0 ? step : -step
  setZoom(zoom.value + direction)
}

const updateContainer = () => {
  if (imageRef.value) {
    containerRef.value = imageRef.value.parentElement as HTMLElement | null
  }
}

const handleImageLoad = () => {
  updateContainer()
  nextTick(autoFit)
}

onMounted(() => {
  updateContainer()
  emitZoom()
})

watch(
  () => props.url,
  () => {
    zoom.value = 100
    emitZoom()
    nextTick(() => {
      updateContainer()
      autoFit()
    })
  }
)

defineExpose({
  zoomIn,
  zoomOut,
  autoFit
})
</script>

<template>
  <img
    ref="imageRef"
    :src="url"
    class="max-w-none max-h-none select-none block self-start"
    :style="{
      transform: `scale(${zoom / 100})`,
      transformOrigin: 'top center',
    }"
    @load="handleImageLoad"
    @wheel="handleWheel"
    draggable="false"
    alt="预览图片"
  />
</template>
