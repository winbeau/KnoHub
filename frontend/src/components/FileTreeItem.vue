<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { FileItem } from '../types'

// Global event name for closing all context menus
const CLOSE_MENU_EVENT = 'close-all-context-menus'

const props = defineProps<{
  item: FileItem
  level: number
  activeFileId: number | null
  isLast?: boolean
}>()

const emit = defineEmits<{
  preview: [file: FileItem]
  upload: [folderId: number]
  reorder: [dragId: number, dropId: number, position: 'before' | 'after' | 'inside']
  download: [file: FileItem]
  delete: [file: FileItem]
  rename: [file: FileItem]
}>()

const isOpen = ref(true)

const toggle = () => {
  if (props.item.isFolder) {
    isOpen.value = !isOpen.value
  } else {
    emit('preview', props.item)
  }
}

const onChildPreview = (file: FileItem) => {
  emit('preview', file)
}

const onChildUpload = (folderId: number) => {
  emit('upload', folderId)
}

const onChildReorder = (dragId: number, dropId: number, position: 'before' | 'after' | 'inside') => {
  emit('reorder', dragId, dropId, position)
}

const onChildDownload = (file: FileItem) => {
  emit('download', file)
}

const onChildDelete = (file: FileItem) => {
  emit('delete', file)
}

const onChildRename = (file: FileItem) => {
  emit('rename', file)
}

const handleUploadToFolder = (e: Event) => {
  e.stopPropagation()
  emit('upload', props.item.id)
}

// 右键菜单相关
const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0
})

const handleContextMenu = (e: MouseEvent) => {
  e.preventDefault()
  e.stopPropagation()

  // First, close all other context menus
  window.dispatchEvent(new CustomEvent(CLOSE_MENU_EVENT))

  // Then open this one (use setTimeout to ensure close event fires first)
  setTimeout(() => {
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY
    }
  }, 0)
}

const closeContextMenu = () => {
  contextMenu.value.visible = false
}

// Global listeners for closing menu
const handleGlobalClick = () => {
  closeContextMenu()
}

const handleCloseAllMenus = () => {
  closeContextMenu()
}

onMounted(() => {
  // Listen for clicks anywhere to close menu
  document.addEventListener('click', handleGlobalClick)
  // Listen for custom close event from other FileTreeItems
  window.addEventListener(CLOSE_MENU_EVENT, handleCloseAllMenus)
})

onUnmounted(() => {
  document.removeEventListener('click', handleGlobalClick)
  window.removeEventListener(CLOSE_MENU_EVENT, handleCloseAllMenus)
})

const handleMenuDownload = () => {
  closeContextMenu()
  emit('download', props.item)
}

const handleMenuUpload = () => {
  closeContextMenu()
  emit('upload', props.item.id)
}

const handleMenuDelete = () => {
  closeContextMenu()
  emit('delete', props.item)
}

const handleMenuRename = () => {
  closeContextMenu()
  emit('rename', props.item)
}

// 拖拽相关
const isDragging = ref(false)
const dragOver = ref<'before' | 'after' | 'inside' | null>(null)

const handleDragStart = (e: DragEvent) => {
  if (!e.dataTransfer) return
  isDragging.value = true
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(props.item.id))
}

const handleDragEnd = () => {
  isDragging.value = false
  dragOver.value = null
}

const handleDragOver = (e: DragEvent) => {
  e.preventDefault()
  if (!e.dataTransfer) return
  e.dataTransfer.dropEffect = 'move'

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const y = e.clientY - rect.top
  const height = rect.height

  if (props.item.isFolder && y > height * 0.25 && y < height * 0.75) {
    dragOver.value = 'inside'
  } else if (y < height / 2) {
    dragOver.value = 'before'
  } else {
    dragOver.value = 'after'
  }
}

const handleDragLeave = () => {
  dragOver.value = null
}

const handleDrop = (e: DragEvent) => {
  e.preventDefault()
  if (!e.dataTransfer) return

  const dragId = Number(e.dataTransfer.getData('text/plain'))
  if (dragId === props.item.id || !dragOver.value) {
    dragOver.value = null
    return
  }

  emit('reorder', dragId, props.item.id, dragOver.value)
  dragOver.value = null
}

const iconClass = computed(() => {
  if (props.item.isFolder) {
    return isOpen.value ? 'fa-folder-open text-amber-500' : 'fa-folder text-amber-400'
  }
  const ext = props.item.type?.toLowerCase()
  // PDF
  if (ext === 'pdf') return 'fa-file-pdf text-red-500'
  // Word
  if (ext && ['doc', 'docx'].includes(ext)) return 'fa-file-word text-blue-600'
  // Excel
  if (ext && ['xls', 'xlsx'].includes(ext)) return 'fa-file-excel text-green-600'
  // PowerPoint
  if (ext && ['ppt', 'pptx'].includes(ext)) return 'fa-file-powerpoint text-orange-500'
  // Images
  if (ext && ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return 'fa-file-image text-purple-500'
  // Archives
  if (ext && ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'fa-file-zipper text-yellow-600'
  // Code
  if (ext && ['js', 'ts', 'jsx', 'tsx', 'vue', 'py', 'java', 'cpp', 'c', 'h', 'css', 'scss', 'html', 'vhd'].includes(ext)) return 'fa-file-code text-emerald-500'
  // Text/Markdown
  if (ext && ['txt', 'md', 'markdown'].includes(ext)) return 'fa-file-lines text-slate-500'
  // Video
  if (ext && ['mp4', 'avi', 'mov', 'mkv', 'webm'].includes(ext)) return 'fa-file-video text-pink-500'
  // Audio
  if (ext && ['mp3', 'wav', 'flac', 'ogg', 'm4a'].includes(ext)) return 'fa-file-audio text-indigo-500'
  // Jupyter Notebook
  if (ext === 'ipynb') return 'fa-file-code text-orange-500'
  // Default
  return 'fa-file text-slate-400'
})

const isActive = computed(() => !props.item.isFolder && props.item.id === props.activeFileId)
const hasChildren = computed(() => props.item.isFolder && props.item.children && props.item.children.length > 0)
const isEmpty = computed(() => props.item.isFolder && (!props.item.children || props.item.children.length === 0))
</script>

<template>
  <div class="select-none relative">
    <!-- 拖拽指示线 -->
    <div
      v-if="dragOver === 'before'"
      class="absolute left-0 right-0 h-0.5 bg-sky-500 z-10"
      :style="{ marginLeft: level * 16 + 'px', top: '0' }"
    ></div>
    <div
      v-if="dragOver === 'after'"
      class="absolute left-0 right-0 h-0.5 bg-sky-500 z-10"
      :style="{ marginLeft: level * 16 + 'px', bottom: '0' }"
    ></div>

    <!-- 节点内容 -->
    <div
      @click.stop="toggle"
      @contextmenu="handleContextMenu"
      draggable="true"
      @dragstart="handleDragStart"
      @dragend="handleDragEnd"
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
      class="flex items-center gap-1.5 py-1.5 px-2 rounded-md cursor-pointer text-[13px] transition-all duration-150 group relative"
      :class="[
        isActive ? 'bg-sky-100 text-sky-700 shadow-sm' : 'hover:bg-slate-100 text-slate-600',
        item.isFolder ? 'font-medium' : '',
        isDragging ? 'opacity-50' : '',
        dragOver === 'inside' ? 'bg-sky-50 ring-2 ring-sky-300' : ''
      ]"
      :style="{ marginLeft: level * 16 + 'px' }"
    >
      <!-- 树形连接符 └ (仅用于非文件夹) -->
      <span
        v-if="!item.isFolder && level > 0"
        class="text-slate-300 text-[10px] mr-0.5 font-mono"
      >└</span>

      <!-- 展开/收起图标 (仅文件夹) -->
      <span
        v-if="item.isFolder"
        class="w-4 h-4 flex items-center justify-center text-slate-400 hover:text-slate-600 transition-colors"
      >
        <i
          class="fa-solid text-[10px] transition-transform duration-200"
          :class="isOpen ? 'fa-chevron-down' : 'fa-chevron-right'"
        ></i>
      </span>

      <!-- 文件/文件夹图标 -->
      <i :class="['fa-solid text-sm', iconClass]"></i>

      <!-- 名称 -->
      <span class="flex-grow truncate">{{ item.name }}</span>

      <!-- 文件大小 -->
      <span v-if="item.size && !item.isFolder" class="text-slate-400 text-[11px] ml-1 opacity-60">
        {{ item.size }}
      </span>

      <!-- 文件夹上传按钮 -->
      <button
        v-if="item.isFolder"
        @click="handleUploadToFolder"
        class="opacity-0 group-hover:opacity-100 w-5 h-5 flex items-center justify-center text-slate-400 hover:text-sky-500 transition-all"
        title="上传文件到此文件夹"
      >
        <i class="fa-solid fa-cloud-arrow-up text-[10px]"></i>
      </button>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="fixed z-[9999] bg-white rounded-lg shadow-xl border border-slate-200 py-1 min-w-[140px]"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
      >
        <!-- 文件菜单 -->
        <template v-if="!item.isFolder">
          <button
            @click="handleMenuDownload"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-sky-50 hover:text-sky-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-download text-xs w-4"></i>
            <span>下载</span>
          </button>
          <button
            @click="handleMenuRename"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-sky-50 hover:text-sky-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-pen text-xs w-4"></i>
            <span>重命名</span>
          </button>
          <button
            @click="handleMenuDelete"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-red-50 hover:text-red-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-trash text-xs w-4"></i>
            <span>删除</span>
          </button>
        </template>
        <!-- 文件夹菜单 -->
        <template v-else>
          <button
            @click="handleMenuUpload"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-sky-50 hover:text-sky-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-cloud-arrow-up text-xs w-4"></i>
            <span>上传文件</span>
          </button>
          <button
            @click="handleMenuRename"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-sky-50 hover:text-sky-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-pen text-xs w-4"></i>
            <span>重命名</span>
          </button>
          <button
            @click="handleMenuDelete"
            class="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-red-50 hover:text-red-600 flex items-center gap-2 transition-colors"
          >
            <i class="fa-solid fa-trash text-xs w-4"></i>
            <span>删除</span>
          </button>
        </template>
      </div>
    </Teleport>

    <!-- 子节点 -->
    <div v-if="item.isFolder && isOpen" class="relative">
      <!-- 垂直连接线 -->
      <div
        v-if="hasChildren"
        class="absolute w-px bg-slate-200"
        :style="{ left: level * 16 + 12 + 'px', top: '0', bottom: '12px' }"
      ></div>

      <!-- 空文件夹提示 -->
      <div
        v-if="isEmpty"
        class="flex items-center gap-2 py-2 px-2 text-[12px] text-slate-400 italic"
        :style="{ marginLeft: (level + 1) * 16 + 'px' }"
      >
        <button
          @click="handleUploadToFolder"
          class="flex items-center gap-1 px-2 py-1 rounded border border-dashed border-slate-300 hover:border-sky-400 hover:text-sky-500 hover:bg-sky-50 transition-all"
        >
          <i class="fa-solid fa-cloud-arrow-up text-[10px]"></i>
          <span>上传文件</span>
        </button>
      </div>

      <!-- 子项列表 -->
      <FileTreeItem
        v-for="(child, index) in item.children"
        :key="child.id"
        :item="child"
        :level="level + 1"
        :active-file-id="activeFileId"
        :is-last="index === (item.children?.length ?? 0) - 1"
        @preview="onChildPreview"
        @upload="onChildUpload"
        @reorder="onChildReorder"
        @download="onChildDownload"
        @delete="onChildDelete"
        @rename="onChildRename"
      />
    </div>
  </div>
</template>
