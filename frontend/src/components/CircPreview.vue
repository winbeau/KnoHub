<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  previewUrl: string
  fileName?: string
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const version = ref(0)

const cacheBustedUrl = computed(() => {
  if (!props.previewUrl) return ''
  const sep = props.previewUrl.includes('?') ? '&' : '?'
  return `${props.previewUrl}${sep}v=${version.value}`
})

const reload = () => {
  error.value = null
  loading.value = true
  version.value += 1
}

watch(
  () => props.previewUrl,
  () => {
    error.value = null
    loading.value = true
    version.value += 1
  },
  { immediate: true }
)

const handleLoad = () => {
  loading.value = false
}

const handleError = () => {
  loading.value = false
  error.value = '预览加载失败，请检查后端 Logisim 配置或稍后重试'
}
</script>

<template>
  <div class="w-full h-full flex flex-col gap-3 items-center justify-start">
    <div class="flex items-center gap-3 text-slate-600 text-sm w-full">
      <div class="flex items-center gap-2">
        <i class="fa-solid fa-microchip text-sky-500"></i>
        <span class="font-medium">Logisim 预览</span>
      </div>
      <button
        class="ml-auto px-3 h-8 rounded-md border border-slate-200 bg-white text-xs text-slate-600 hover:border-sky-300 hover:text-sky-600 transition"
        @click="reload"
        title="重新加载预览"
      >
        <i class="fa-solid fa-rotate-right mr-1"></i>刷新
      </button>
    </div>

    <div
      class="flex-1 w-full min-h-[320px] bg-white border border-slate-200 rounded-xl shadow-sm flex items-center justify-center overflow-auto p-4"
    >
      <div v-if="!previewUrl" class="text-slate-400 text-sm text-center">
        <i class="fa-solid fa-circle-exclamation text-2xl mb-2"></i>
        <p>暂无预览链接，请确认后端已配置 Logisim 渲染</p>
      </div>
      <div v-else-if="error" class="text-red-600 text-sm text-center">
        <i class="fa-solid fa-triangle-exclamation text-xl mb-2"></i>
        <p>{{ error }}</p>
      </div>
      <div v-else class="relative">
        <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white/70">
          <i class="fa-solid fa-spinner fa-spin text-sky-500 text-lg"></i>
        </div>
        <img
          :src="cacheBustedUrl"
          class="max-w-full max-h-[70vh] object-contain rounded border border-slate-100 shadow-sm"
          :alt="fileName || 'Logisim 预览图'"
          @load="handleLoad"
          @error="handleError"
        />
      </div>
    </div>
  </div>
</template>
