<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { Resource, FileItem } from '../types'

const props = defineProps<{
  visible: boolean
  targetId: number | null
  targetTitle: string
  resources: Resource[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'confirm': [files: File[], folderId: number | null]
}>()

type PendingFile = {
  file: File
  baseName: string
  extension: string
}

// 选择的文件列表（未上传）
const pendingFiles = ref<PendingFile[]>([])
const targetFolderId = ref<number | null>(null)

// 上传状态
const uploadState = ref<'idle' | 'uploading' | 'success' | 'error'>('idle')
const uploadProgress = ref(0)
const errorMessage = ref('')

// 递归获取所有文件夹（包括嵌套）
const getAllFolders = (files: FileItem[], prefix = ''): { id: number; name: string; path: string }[] => {
  let result: { id: number; name: string; path: string }[] = []
  for (const f of files) {
    if (f.isFolder) {
      const path = prefix ? `${prefix} / ${f.name}` : f.name
      result.push({ id: f.id, name: f.name, path })
      if (f.children && f.children.length > 0) {
        result = result.concat(getAllFolders(f.children, path))
      }
    }
  }
  return result
}

// 获取当前资源的所有文件夹列表（包括嵌套）
const getFolders = computed(() => {
  const res = props.resources.find((r) => r.id === props.targetId)
  return res ? getAllFolders(res.files) : []
})

const baseNameFromFile = (file: File) => {
  const name = file.name
  const lastDot = name.lastIndexOf('.')
  if (lastDot > 0) {
    return name.slice(0, lastDot)
  }
  return name
}

const totalSize = computed(() => pendingFiles.value.reduce((sum, f) => sum + f.file.size, 0))

const formatSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + ['B', 'KB', 'MB', 'GB'][i]
}

// 监听 visible 变化，重置状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    // 打开时重置状态
    pendingFiles.value = []
    targetFolderId.value = null
    uploadState.value = 'idle'
    uploadProgress.value = 0
    errorMessage.value = ''
  }
})

const splitFileName = (file: File) => {
  const name = file.name
  const lastDot = name.lastIndexOf('.')
  if (lastDot > 0 && lastDot < name.length - 1) {
    return { base: name.slice(0, lastDot), extension: name.slice(lastDot + 1) }
  }
  return { base: name, extension: '' }
}

const addFiles = (files: File[]) => {
  const mapped = files.map((file) => {
    const parts = splitFileName(file)
    return { file, baseName: parts.base, extension: parts.extension }
  })
  pendingFiles.value = pendingFiles.value.concat(mapped)
  uploadState.value = 'idle'
  errorMessage.value = ''
}

// 选择文件
const handleFileSelect = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files?.length) {
    addFiles(Array.from(target.files))
  }
  // 重置 input 以便可以重复选择同一文件
  target.value = ''
}

// 拖拽文件
const handleFileDrop = (e: DragEvent) => {
  if (e.dataTransfer?.files?.length) {
    addFiles(Array.from(e.dataTransfer.files))
  }
}

// 关闭弹窗
const closeModal = () => {
  emit('update:visible', false)
}

// 清除缓存的文件
const clearFile = () => {
  pendingFiles.value = []
  uploadState.value = 'idle'
  uploadProgress.value = 0
  errorMessage.value = ''
}

const removeFile = (index: number) => {
  pendingFiles.value.splice(index, 1)
}

// 确认上传 - 真正执行上传
const confirmUpload = () => {
  if (!pendingFiles.value.length) return
  const filesToSend = pendingFiles.value.map(({ file, baseName, extension }) => {
    const finalName = extension ? `${baseName.trim() || baseNameFromFile(file)}.${extension}` : (baseName.trim() || baseNameFromFile(file))
    if (finalName === file.name) return file
    return new File([file], finalName, { type: file.type })
  })
  emit('confirm', filesToSend, targetFolderId.value)
}

// 暴露方法给父组件控制上传状态
const setUploading = () => {
  uploadState.value = 'uploading'
  uploadProgress.value = 0
  // 模拟进度
  const interval = setInterval(() => {
    if (uploadProgress.value < 90) {
      uploadProgress.value += Math.random() * 15
    }
  }, 200)
  return () => clearInterval(interval)
}

const setSuccess = () => {
  uploadProgress.value = 100
  uploadState.value = 'success'
}

const setError = (msg: string) => {
  uploadState.value = 'error'
  errorMessage.value = msg
}

defineExpose({
  setUploading,
  setSuccess,
  setError,
  clearFile,
  setTargetFolder: (folderId: number | null) => {
    targetFolderId.value = folderId
  }
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="closeModal"
    >
      <div
        class="bg-white rounded-xl shadow-2xl w-full max-w-md p-6 transform transition-all"
        @click.stop
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-slate-800">
            <i class="fa-solid fa-cloud-arrow-up text-sky-500 mr-2"></i>
            上传资料
          </h3>
          <button
            @click="closeModal"
            class="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
          >
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>

        <!-- 目标信息 -->
        <p class="text-sm text-slate-500 mb-4">
          上传至: <span class="text-sky-600 font-medium">{{ targetTitle }}</span>
        </p>

        <!-- 文件选择/上传区域 -->
        <div
          v-if="uploadState === 'idle' || uploadState === 'error'"
          class="border-2 border-dashed rounded-xl p-8 text-center transition-all cursor-pointer relative"
          :class="pendingFiles.length ? 'border-sky-300 bg-sky-50/50' : 'border-slate-200 hover:border-sky-300 hover:bg-sky-50/30'"
          @dragover.prevent
          @drop.prevent="handleFileDrop"
        >
          <input
            type="file"
            @change="handleFileSelect"
            class="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
            multiple
            accept=".jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.zip,.rar,.txt,.md"
          />

          <!-- 未选择文件 -->
          <div v-if="!pendingFiles.length">
            <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-sky-100 flex items-center justify-center">
              <i class="fa-solid fa-cloud-arrow-up text-2xl text-sky-500"></i>
            </div>
            <p class="text-sm text-slate-600 font-medium mb-1">点击或拖拽文件到此处（可多选）</p>
            <p class="text-xs text-slate-400">支持 jpg, png, pdf, doc, zip 等格式</p>
          </div>

          <!-- 已选择文件（缓存中） -->
          <div v-else class="space-y-3 text-left">
            <div
              v-for="(item, idx) in pendingFiles"
              :key="item.file.name + idx"
              class="flex items-start gap-3 p-3 bg-white/80 rounded-lg border border-slate-100"
            >
              <div class="w-9 h-9 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <i class="fa-solid fa-file-lines"></i>
              </div>
              <div class="flex-1 min-w-0 space-y-1">
                <div class="flex items-center gap-2">
                  <input
                    v-model="item.baseName"
                    type="text"
                    placeholder="文件名"
                    class="flex-1 px-3 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none text-sm"
                  />
                  <span
                    v-if="item.extension"
                    class="px-2 py-1 bg-slate-100 border border-slate-200 rounded text-xs text-slate-500"
                  >
                    .{{ item.extension }}
                  </span>
                </div>
                <div class="text-xs text-slate-400">{{ formatSize(item.file.size) }}</div>
              </div>
              <button
                class="text-slate-400 hover:text-red-500 transition"
                title="移除"
                @click.stop="removeFile(idx)"
              >
                <i class="fa-solid fa-xmark"></i>
              </button>
            </div>
            <div class="text-xs text-slate-500">
              共 {{ pendingFiles.length }} 个文件 · {{ formatSize(totalSize) }}
            </div>
          </div>
        </div>

        <!-- 上传进度 -->
        <div v-else-if="uploadState === 'uploading'" class="border-2 border-sky-200 rounded-xl p-8 text-center bg-sky-50/30">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-sky-100 flex items-center justify-center">
            <i class="fa-solid fa-spinner fa-spin text-2xl text-sky-500"></i>
          </div>
          <p class="text-sm text-slate-700 font-medium mb-3">正在上传...</p>
          <div class="w-full bg-slate-200 rounded-full h-2 mb-2">
            <div
              class="bg-sky-500 h-2 rounded-full transition-all duration-300"
              :style="{ width: `${Math.min(uploadProgress, 100)}%` }"
            ></div>
          </div>
          <p class="text-xs text-slate-400">{{ Math.round(uploadProgress) }}%</p>
        </div>

        <!-- 上传成功 -->
        <div v-else-if="uploadState === 'success'" class="border-2 border-emerald-200 rounded-xl p-8 text-center bg-emerald-50/30">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-emerald-100 flex items-center justify-center">
            <i class="fa-solid fa-circle-check text-2xl text-emerald-500"></i>
          </div>
          <p class="text-sm text-emerald-600 font-medium">上传成功！</p>
        </div>

        <!-- 错误信息 -->
        <div v-if="uploadState === 'error' && errorMessage" class="mt-3 p-3 bg-red-50 rounded-lg">
          <p class="text-sm text-red-600">
            <i class="fa-solid fa-circle-exclamation mr-1"></i>
            {{ errorMessage }}
          </p>
        </div>

        <!-- 清除文件按钮 -->
        <div v-if="pendingFiles.length && (uploadState === 'idle' || uploadState === 'error')" class="mt-3 text-right">
          <button
            @click.stop="clearFile"
            class="text-sm text-slate-400 hover:text-red-500 transition"
          >
            <i class="fa-solid fa-trash-can mr-1"></i>清除全部
          </button>
        </div>

        <!-- 文件夹选择 -->
        <div v-if="uploadState !== 'success'" class="mt-4">
          <label class="block text-sm font-medium text-slate-700 mb-2">选择目标文件夹</label>
          <select
            v-model="targetFolderId"
            class="w-full border border-slate-200 rounded-lg p-2.5 text-sm bg-slate-50 outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500 transition"
          >
            <option :value="null">📁 根目录</option>
            <option v-for="folder in getFolders" :key="folder.id" :value="folder.id">
              📂 {{ folder.path }}
            </option>
          </select>
        </div>

        <!-- 操作按钮 -->
        <div class="mt-6 flex justify-end gap-3">
          <button
            v-if="uploadState !== 'success'"
            @click="closeModal"
            class="px-5 py-2.5 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium transition"
          >
            取消
          </button>
          <button
            v-if="uploadState === 'idle' || uploadState === 'error'"
            @click="confirmUpload"
            :disabled="!pendingFiles.length"
            class="px-5 py-2.5 bg-sky-600 text-white rounded-lg text-sm font-medium hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed transition shadow-sm"
          >
            <i class="fa-solid fa-upload mr-1"></i>
            确认上传（{{ pendingFiles.length }}）
          </button>
          <button
            v-if="uploadState === 'success'"
            @click="closeModal"
            class="px-5 py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 transition shadow-sm"
          >
            完成
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
