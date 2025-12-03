<script setup lang="ts">
import { computed } from 'vue'
import type { Resource } from '../types'

const props = defineProps<{
  data: Resource
}>()

const emit = defineEmits<{
  (e: 'delete', resource: Resource): void
}>()

const getStatusColor = (status?: string) => {
  if (status === 'New') return 'bg-emerald-100 text-emerald-700'
  if (status === 'Hot') return 'bg-rose-100 text-rose-700'
  return 'bg-slate-100 text-slate-600'
}

const fileCount = computed(() => {
  let count = 0
  const countRecursive = (items: typeof props.data.files) => {
    items.forEach((item) => {
      if (!item.isFolder) count++
      if (item.children) countRecursive(item.children)
    })
  }
  if (props.data.files) countRecursive(props.data.files)
  return count
})

const formatDate = (value?: string) => {
  if (!value) return '未知'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '未知'
  const month = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}
</script>

<template>
  <div
    class="bg-white rounded-lg shadow-sm border border-slate-200 hover:shadow-md hover:border-sky-300 transition-all duration-200 cursor-pointer flex flex-col h-[200px] group relative overflow-hidden"
  >
    <button
      class="absolute top-2 right-2 text-slate-400 hover:text-rose-600 bg-white/90 border border-slate-200 rounded-full w-8 h-8 flex items-center justify-center shadow-sm opacity-0 group-hover:opacity-100 focus:opacity-100 transition"
      title="删除卡片"
      @click.stop="emit('delete', data)"
    >
      <i class="fa-regular fa-trash-can text-xs"></i>
    </button>
    <div class="h-1 bg-gradient-to-r from-sky-400 to-blue-500 w-full"></div>
    <div class="p-4 flex-grow flex flex-col">
      <div class="flex justify-between items-center mb-3">
        <div class="flex gap-2">
          <span
            v-if="data.tag"
            class="text-[10px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wide"
            :class="getStatusColor(data.tag)"
          >
            {{ data.tag }}
          </span>
          <span
            class="text-[10px] font-medium px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 flex items-center gap-1"
          >
            <i class="fa-solid fa-file-lines"></i> {{ fileCount }}
          </span>
        </div>
        <span class="text-[10px] text-slate-400">{{ data.updateDate }}</span>
      </div>
      <div class="text-[10px] text-slate-400 mb-1">
        创建: {{ formatDate(data.createDate) }}
      </div>
      <h3
        class="text-lg font-bold text-slate-800 mb-3 leading-tight group-hover:text-sky-600 transition-colors line-clamp-2"
      >
        {{ data.title }}
      </h3>

      <div class="flex-grow bg-slate-50 rounded p-2 overflow-hidden border border-slate-100">
        <div class="text-[10px] text-slate-400 mb-1 flex items-center gap-1">
          <i class="fa-solid fa-sitemap"></i> 目录预览
        </div>
        <div
          v-for="f in data.files.slice(0, 3)"
          :key="f.id"
          class="flex items-center gap-1.5 text-[11px] text-slate-600 py-0.5"
        >
          <i
            class="fa-solid"
            :class="f.isFolder ? 'fa-folder text-amber-400' : 'fa-file text-slate-400'"
          ></i>
          <span class="truncate">{{ f.name }}</span>
        </div>
        <div v-if="data.files.length > 3" class="text-[10px] text-slate-400 mt-1 pl-1">
          ... 还有 {{ data.files.length - 3 }} 个文件
        </div>
      </div>
    </div>
  </div>
</template>
