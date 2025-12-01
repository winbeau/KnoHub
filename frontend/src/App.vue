<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import NavLink from './components/NavLink.vue'
import FileTreeItem from './components/FileTreeItem.vue'
import ResourceCard from './components/ResourceCard.vue'
import UploadModal from './components/UploadModal.vue'
import Toast from './components/Toast.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import DocPreview from './components/DocPreview.vue'
import PdfPreview from './components/PdfPreview.vue'
import ImagePreview from './components/ImagePreview.vue'
import CodePreview from './components/CodePreview.vue'
import knoHubLogo from './assets/knohub.svg'
import { tabs } from './data'
import { resourceApi, fileApi, metricsApi, API_ORIGIN } from './api'
import type { Resource, FileItem } from './types'

// --- State ---
const currentTab = ref('home')
const searchQuery = ref('')
const activeResource = ref<Resource | null>(null)
const currentPreviewFile = ref<FileItem | null>(null)
const resources = ref<Resource[]>([])
const activeUserCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// Upload Modal
const uploadModalRef = ref<InstanceType<typeof UploadModal> | null>(null)
const uploadModalVisible = ref(false)
const uploadTargetId = ref<number | null>(null)
const uploadTargetTitle = ref('')

// Toast and Confirm Dialog
const toastRef = ref<InstanceType<typeof Toast> | null>(null)
const confirmRef = ref<InstanceType<typeof ConfirmDialog> | null>(null)

// Download progress state
const downloadProgress = ref(0)
const downloadStatus = ref<'idle' | 'downloading' | 'success' | 'error'>('idle')
const downloadingFile = ref<FileItem | null>(null)
const downloadMessage = ref('')
const downloadTotalKnown = ref(true)

// Create Folder Modal
const folderModal = reactive({
  visible: false,
  resourceId: null as number | null,
  parentFolderId: null as number | null,
  folderName: '',
  loading: false,
})

// Rename modal
const renameModal = reactive({
  visible: false,
  target: null as FileItem | null,
  baseName: '',
  extension: '',
  loading: false,
})

// New resource modal
const newResourceModal = reactive({
  visible: false,
  type: 'course' as 'course' | 'tech' | 'info',
  title: '',
  description: '',
  loading: false,
})

// 文档/PDF 预览控制
const viewerRef = ref<
  | InstanceType<typeof DocPreview>
  | InstanceType<typeof PdfPreview>
  | InstanceType<typeof ImagePreview>
  | InstanceType<typeof CodePreview>
  | null
>(null)
const docZoom = ref(100)
const fitDoc = () => {
  if (!currentPreviewFile.value || !isZoomablePreview(currentPreviewFile.value.type)) return
  const viewer = viewerRef.value as InstanceType<typeof DocPreview> | InstanceType<typeof PdfPreview> | InstanceType<typeof ImagePreview> | null
  viewer?.autoFit?.()
}

// --- API Methods ---
const loadResources = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await resourceApi.getAll()
    resources.value = data.map(r => ({
      id: r.id,
      type: r.type as 'course' | 'tech' | 'info',
      title: r.title,
      description: r.description,
      tag: r.tag as 'New' | 'Hot' | 'Rec' | undefined,
      updateDate: r.updateDate,
      files: mapFiles(r.files)
    }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载资源失败'
    console.error('Failed to load resources:', e)
  } finally {
    loading.value = false
  }
}

const loadActiveUsers = async () => {
  try {
    activeUserCount.value = await metricsApi.getActiveUsers()
  } catch (e) {
    console.error('Failed to load active user count:', e)
    activeUserCount.value = null
  }
}

const mapFiles = (files: any[]): FileItem[] => {
  return files.map(f => ({
    id: f.id,
    name: f.name,
    isFolder: f.isFolder,
    type: f.type,
    size: f.size,
    url: f.url,
    children: f.children ? mapFiles(f.children) : undefined
  }))
}

const refreshActiveResource = async () => {
  if (!activeResource.value) return
  try {
    const data = await resourceApi.getById(activeResource.value.id)
    activeResource.value = {
      id: data.id,
      type: data.type as 'course' | 'tech' | 'info',
      title: data.title,
      description: data.description,
      tag: data.tag as 'New' | 'Hot' | 'Rec' | undefined,
      updateDate: data.updateDate,
      files: mapFiles(data.files)
    }
    // Also update in resources list
    const idx = resources.value.findIndex(r => r.id === data.id)
    if (idx >= 0) {
      resources.value[idx] = activeResource.value
    }
  } catch (e) {
    console.error('Failed to refresh resource:', e)
  }
}

// --- Computed ---
const filteredResources = computed(() => {
  return resources.value.filter((r) => {
    const matchType = r.type === currentTab.value
    const matchSearch =
      r.title.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      r.description.toLowerCase().includes(searchQuery.value.toLowerCase())
    return matchType && matchSearch
  })
})

const getCurrentTabName = computed(() => tabs.find((t) => t.id === currentTab.value)?.name || '')

const totalFiles = computed(() => {
  let count = 0
  const countFiles = (list: FileItem[]) => {
    list.forEach((item) => {
      if (!item.isFolder) count++
      if (item.children) countFiles(item.children)
    })
  }
  resources.value.forEach((r) => countFiles(r.files))
  return count
})

const activeUsersDisplay = computed(() => (activeUserCount.value !== null ? activeUserCount.value : '—'))

const recentUploads = computed(() => {
  const clone = [...resources.value]
  clone.sort((a, b) => {
    const tA = Date.parse(a.updateDate || '') || 0
    const tB = Date.parse(b.updateDate || '') || 0
    return tB - tA
  })
  return clone.slice(0, 6)
})

// --- Methods ---
const getStatusColor = (status?: string) => {
  if (status === 'New') return 'bg-emerald-100 text-emerald-700'
  if (status === 'Hot') return 'bg-rose-100 text-rose-700'
  if (status === 'Rec') return 'bg-violet-100 text-violet-700'
  return 'bg-slate-100 text-slate-600'
}

const formatDate = (value?: string) => {
  if (!value) return '未知时间'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '未知时间'
  const month = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

const isImage = (type?: string) => {
  if (!type) return false
  const t = type.toLowerCase()
  return ['png', 'jpg', 'jpeg', 'gif'].includes(t)
}

const getFileIcon = (type?: string) => {
  if (isImage(type)) return 'fa-solid fa-image'
  if (type === 'pdf') return 'fa-solid fa-file-pdf'
  if (type && ['doc', 'docx'].includes(type)) return 'fa-solid fa-file-word'
  if (type && type.toLowerCase() === 'vhd') return 'fa-solid fa-file-code'
  return 'fa-solid fa-file'
}

const isDocFile = (type?: string) => type && ['doc', 'docx'].includes(type.toLowerCase())
const isPdfFile = (type?: string) => type && type.toLowerCase() === 'pdf'
const isZoomablePreview = (type?: string) => isDocFile(type) || isPdfFile(type) || isImage(type)
const isVhdFile = (type?: string) => type && type.toLowerCase() === 'vhd'
const isLegacyDocFile = (type?: string) => type && type.toLowerCase() === 'doc'
const resolveFileUrl = (url?: string | null) => {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${API_ORIGIN}${url}`
}

const enterDetailView = (resource: Resource) => {
  activeResource.value = resource
  currentPreviewFile.value = null
}

const exitDetailView = () => {
  activeResource.value = null
  currentPreviewFile.value = null
}

const switchTab = (tabId: string) => {
  currentTab.value = tabId
  activeResource.value = null
}

const resetView = () => {
  currentTab.value = 'home'
  activeResource.value = null
}

const setPreviewFile = (file: FileItem) => {
  currentPreviewFile.value = file
  if (isZoomablePreview(file.type)) {
    docZoom.value = 100
  }
}

const resetDownloadState = () => {
  downloadProgress.value = 0
  downloadStatus.value = 'idle'
  downloadingFile.value = null
  downloadMessage.value = ''
  downloadTotalKnown.value = true
}

const triggerBrowserDownload = (blob: Blob, filename: string) => {
  const blobUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(blobUrl)
}

const fetchWithProgress = async (url: string, filename: string) => {
  const response = await fetch(url)

  if (!response.ok) {
    const rawText = await response.text().catch(() => '')
    let message = `下载失败（HTTP ${response.status}）`
    try {
      const parsed = JSON.parse(rawText)
      if (parsed?.message) message = parsed.message
    } catch {
      if (rawText) message = rawText.trim()
    }
    throw new Error(message)
  }

  const total = Number(response.headers.get('content-length') || '0')
  downloadTotalKnown.value = total > 0

  if (!response.body || !response.body.getReader) {
    const blob = await response.blob()
    triggerBrowserDownload(blob, filename)
    downloadProgress.value = 100
    return
  }

  const reader = response.body.getReader()
  const chunks: BlobPart[] = []
  let received = 0

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    if (value) {
      // Slice to a standalone ArrayBuffer (avoids SharedArrayBuffer typing issues)
      chunks.push(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength))
      received += value.length
      if (total > 0) {
        const percent = Math.round((received / total) * 100)
        downloadProgress.value = Math.min(percent, 99)
      } else {
        // When size is unknown, gently bump progress to show activity
        downloadProgress.value = Math.min(95, downloadProgress.value + Math.random() * 5)
      }
    }
  }

  const blob = new Blob(chunks, { type: response.headers.get('content-type') || 'application/octet-stream' })
  downloadProgress.value = 100
  triggerBrowserDownload(blob, filename)
}

const downloadFile = async (file: FileItem) => {
  const downloadUrl = resolveFileUrl(file.url)
  if (!downloadUrl) {
    toastRef.value?.warning(`文件 ${file.name} 暂无下载链接`)
    return
  }

  if (downloadStatus.value === 'downloading') {
    toastRef.value?.info('已有下载进行中，请稍候')
    return
  }

  downloadingFile.value = file
  downloadStatus.value = 'downloading'
  downloadProgress.value = 2
  downloadMessage.value = ''
  downloadTotalKnown.value = true

  try {
    await fetchWithProgress(downloadUrl, file.name)
    downloadStatus.value = 'success'
    toastRef.value?.success('下载完成')
    setTimeout(() => {
      if (downloadStatus.value === 'success') {
        resetDownloadState()
      }
    }, 1200)
  } catch (e) {
    downloadStatus.value = 'error'
    downloadMessage.value = e instanceof Error ? e.message : '下载失败'
    toastRef.value?.error(downloadMessage.value)
  }
}

const updateDocZoom = (value: number) => {
  docZoom.value = value
}

const zoomDocIn = () => {
  if (!currentPreviewFile.value || !isZoomablePreview(currentPreviewFile.value.type)) return
  const viewer = viewerRef.value as InstanceType<typeof DocPreview> | InstanceType<typeof PdfPreview> | InstanceType<typeof ImagePreview> | null
  viewer?.zoomIn?.()
}

const zoomDocOut = () => {
  if (!currentPreviewFile.value || !isZoomablePreview(currentPreviewFile.value.type)) return
  const viewer = viewerRef.value as InstanceType<typeof DocPreview> | InstanceType<typeof PdfPreview> | InstanceType<typeof ImagePreview> | null
  viewer?.zoomOut?.()
}

const vhdCopyState = reactive({
  copied: false,
  timer: null as number | null
})

const copyVhdContent = async () => {
  if (!currentPreviewFile.value || !isVhdFile(currentPreviewFile.value.type)) return
  const viewer = viewerRef.value as InstanceType<typeof CodePreview> | null
  const result = await viewer?.copyContent?.()
  if (result === false) {
    vhdCopyState.copied = false
    return
  }
  vhdCopyState.copied = true
  if (vhdCopyState.timer) {
    clearTimeout(vhdCopyState.timer)
  }
  vhdCopyState.timer = window.setTimeout(() => {
    vhdCopyState.copied = false
    vhdCopyState.timer = null
  }, 1500)
}

const handleUploadRequest = (resource: Resource) => {
  uploadTargetId.value = resource.id
  uploadTargetTitle.value = resource.title
  uploadModalVisible.value = true
}

const handleUploadToFolder = (folderId: number) => {
  if (!activeResource.value) return
  uploadTargetId.value = activeResource.value.id
  uploadTargetTitle.value = activeResource.value.title
  uploadModalVisible.value = true
  // 设置目标文件夹
  setTimeout(() => {
    uploadModalRef.value?.setTargetFolder(folderId)
  }, 50)
}

const handleReorder = async (dragId: number, dropId: number, position: 'before' | 'after' | 'inside') => {
  try {
    await fileApi.reorder(dragId, dropId, position)
    await refreshActiveResource()
  } catch (e) {
    toastRef.value?.error(e instanceof Error ? e.message : '排序失败')
  }
}

const handleUploadConfirm = async (files: File[], folderId: number | null) => {
  if (!uploadTargetId.value) return

  const stopProgress = uploadModalRef.value?.setUploading()

  try {
    const targetFiles = files && files.length ? files : []
    if (!targetFiles.length) throw new Error('请选择文件')
    await fileApi.uploadBatch(uploadTargetId.value, targetFiles, folderId)
    stopProgress?.()
    uploadModalRef.value?.setSuccess()
    // Refresh the resource to show new file
    await refreshActiveResource()
  } catch (e) {
    stopProgress?.()
    uploadModalRef.value?.setError(e instanceof Error ? e.message : '上传失败')
  }
}

const newResourceLabels = (type: 'course' | 'tech' | 'info') => {
  if (type === 'course') return { title: '课程名', description: '简介' }
  if (type === 'tech') return { title: '文档标题', description: '简介' }
  return { title: '信息标题', description: '简介' }
}

const openNewResourceModal = (type: 'course' | 'tech' | 'info') => {
  newResourceModal.type = type
  newResourceModal.title = ''
  newResourceModal.description = ''
  newResourceModal.visible = true
  newResourceModal.loading = false
}

const handleCreateResource = async () => {
  const title = newResourceModal.title.trim()
  const description = newResourceModal.description.trim()
  if (!title) {
    toastRef.value?.warning('请输入标题')
    return
  }

  newResourceModal.loading = true
  try {
    const payload = {
      type: newResourceModal.type,
      title,
      description,
    }
    const created = await resourceApi.create(payload)
    toastRef.value?.success('创建成功！')
    newResourceModal.visible = false
    newResourceModal.loading = false
    await loadResources()
    // 自动切换到对应模块并选中新建资源
    currentTab.value = created.type as 'course' | 'tech' | 'info'
    activeResource.value = resources.value.find(r => r.id === created.id) || null
  } catch (e) {
    newResourceModal.loading = false
    toastRef.value?.error(e instanceof Error ? e.message : '创建失败')
  }
}

const handleDeleteResource = async (resource: Resource) => {
  const confirmed = await confirmRef.value?.show({
    title: '删除资源',
    message: `确定要删除「${resource.title}」吗？相关文件夹及文件会被标记为已删除。`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger'
  })

  if (!confirmed) return

  try {
    await resourceApi.delete(resource.id)
    resources.value = resources.value.filter(r => r.id !== resource.id)
    if (activeResource.value?.id === resource.id) {
      activeResource.value = null
      currentPreviewFile.value = null
    }
    toastRef.value?.success('删除成功！')
  } catch (e) {
    toastRef.value?.error(e instanceof Error ? e.message : '删除失败')
  }
}

const splitNameParts = (item: FileItem) => {
  if (item.isFolder) {
    return { base: item.name, extension: '' }
  }
  const lastDot = item.name.lastIndexOf('.')
  if (lastDot > 0) {
    return { base: item.name.slice(0, lastDot), extension: item.name.slice(lastDot + 1) }
  }
  return { base: item.name, extension: item.type ?? '' }
}

const findFileById = (files: FileItem[], id: number): FileItem | null => {
  for (const f of files) {
    if (f.id === id) return f
    if (f.children) {
      const found = findFileById(f.children, id)
      if (found) return found
    }
  }
  return null
}

const openRenameModal = (item: FileItem) => {
  const parts = splitNameParts(item)
  renameModal.target = item
  renameModal.baseName = parts.base
  renameModal.extension = parts.extension
  renameModal.visible = true
  renameModal.loading = false
}

const handleRenameSubmit = async () => {
  if (!renameModal.target) return
  const base = renameModal.baseName.trim()
  if (!base) {
    toastRef.value?.warning('名称不能为空')
    return
  }

  const newName = renameModal.extension ? `${base}.${renameModal.extension}` : base
  if (newName === renameModal.target.name) {
    renameModal.visible = false
    return
  }

  renameModal.loading = true
  try {
    await fileApi.rename(renameModal.target.id, newName)
    await refreshActiveResource()

    if (activeResource.value) {
      const updated = findFileById(activeResource.value.files, renameModal.target.id)
      if (updated) {
        currentPreviewFile.value = updated
      }
    }

    toastRef.value?.success('重命名成功！')
    renameModal.visible = false
  } catch (e) {
    toastRef.value?.error(e instanceof Error ? e.message : '重命名失败')
  } finally {
    renameModal.loading = false
  }
}

// --- Folder Management ---
const openCreateFolderModal = (resourceId: number, parentFolderId: number | null = null) => {
  folderModal.resourceId = resourceId
  folderModal.parentFolderId = parentFolderId
  folderModal.folderName = ''
  folderModal.visible = true
}

const handleCreateFolder = async () => {
  if (!folderModal.folderName.trim() || !folderModal.resourceId) return

  folderModal.loading = true
  try {
    await fileApi.createFolder(
      folderModal.resourceId,
      folderModal.folderName.trim(),
      folderModal.parentFolderId
    )
    folderModal.visible = false
    await refreshActiveResource()
    toastRef.value?.success('文件夹创建成功！')
  } catch (e) {
    toastRef.value?.error(e instanceof Error ? e.message : '创建文件夹失败')
  } finally {
    folderModal.loading = false
  }
}

const handleDeleteFile = async (file: FileItem) => {
  const confirmed = await confirmRef.value?.show({
    title: '确认删除',
    message: `确定要删除 "${file.name}" 吗？${file.isFolder ? '文件夹内所有内容也将被删除。' : ''}`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger'
  })

  if (!confirmed) return

  try {
    if (file.isFolder) {
      await fileApi.deleteFolder(file.id)
    } else {
      await fileApi.deleteFile(file.id)
    }
    await refreshActiveResource()
    if (currentPreviewFile.value?.id === file.id) {
      currentPreviewFile.value = null
    }
    toastRef.value?.success('删除成功！')
  } catch (e) {
    toastRef.value?.error(e instanceof Error ? e.message : '删除失败')
  }
}

// --- Lifecycle ---
onMounted(() => {
  loadResources()
  loadActiveUsers()
})
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50 text-slate-800 font-sans">
    <!-- 顶部导航栏 -->
    <header class="bg-white shadow-sm flex-shrink-0 z-40 relative border-b border-sky-100">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-14">
          <div class="flex items-center">
            <div
              class="flex-shrink-0 flex items-center gap-2 text-sky-600 cursor-pointer hover:text-sky-500 transition"
              @click="resetView"
            >
              <i class="fa-solid fa-shapes text-xl"></i>
              <span class="font-bold text-lg tracking-wider">知源共享</span>
            </div>
            <div class="hidden sm:ml-8 sm:flex sm:space-x-6">
              <NavLink
                v-for="tab in tabs"
                :key="tab.id"
                :active="currentTab === tab.id && !activeResource"
                :icon="tab.icon"
                @click="switchTab(tab.id)"
              >
                {{ tab.name }}
              </NavLink>
            </div>
          </div>
          <!-- 搜索框 -->
          <div class="flex items-center" v-if="!activeResource && currentTab !== 'home'">
            <div class="relative">
              <i
                class="fa-solid fa-search absolute left-3 top-1/2 transform -translate-y-1/2 text-sky-300 text-xs"
              ></i>
              <input
                type="text"
                placeholder="搜索..."
                v-model="searchQuery"
                class="pl-8 pr-4 py-1.5 border border-sky-100 rounded-full text-xs focus:ring-1 focus:ring-sky-500 focus:border-sky-500 outline-none w-32 sm:w-48 transition-all bg-sky-50 focus:bg-white text-sky-800 placeholder-sky-300"
              />
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="flex-grow overflow-hidden relative bg-slate-50">
      <!-- Loading State -->
      <div v-if="loading" class="h-full flex items-center justify-center">
        <div class="text-center">
          <i class="fa-solid fa-spinner fa-spin text-4xl text-sky-500 mb-3"></i>
          <p class="text-slate-500">加载中...</p>
        </div>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="h-full flex items-center justify-center">
        <div class="text-center">
          <i class="fa-solid fa-exclamation-circle text-4xl text-red-400 mb-3"></i>
          <p class="text-slate-600">{{ error }}</p>
          <button
            @click="loadResources"
            class="mt-4 bg-sky-500 text-white px-4 py-2 rounded text-sm hover:bg-sky-600"
          >
            重试
          </button>
        </div>
      </div>

      <!-- 场景 A: 列表视图 -->
      <div v-else-if="!activeResource" class="h-full overflow-y-auto px-4 sm:px-6 lg:px-8 py-6 relative">
        <!-- 主页 Dashboard -->
        <section v-if="currentTab === 'home'" class="space-y-6 max-w-7xl mx-auto">
          <div
            class="rounded-2xl border border-sky-100 bg-gradient-to-r from-sky-50 via-white to-white shadow-sm p-5 sm:p-6 flex flex-col sm:flex-row items-start sm:items-center gap-4 sm:gap-6"
          >
            <div class="flex-1">
              <p class="text-xs uppercase tracking-[0.3em] text-sky-400 font-semibold mb-2">KnoHub</p>
              <h1 class="text-2xl sm:text-3xl font-extrabold text-slate-800 mb-2">欢迎来到知源</h1>
              <p class="text-slate-500 text-sm sm:text-base max-w-2xl mb-4">
                汇集最新课程与技术文档，轻松浏览、上传、下载；所有更新一目了然。
              </p>
              <div class="flex items-center gap-3">
                <button
                  @click="switchTab('course')"
                  class="bg-sky-600 text-white px-5 py-2 rounded-md font-semibold shadow hover:bg-sky-700 transition text-sm"
                >
                  浏览课程
                </button>
                <span class="text-xs text-slate-400 hidden sm:block">最近更新同步呈现</span>
              </div>
            </div>
            <div class="hidden sm:flex items-center justify-center pr-2 -ml-4">
              <img :src="knoHubLogo" alt="KnoHub" class="h-24 w-auto" />
            </div>
          </div>

          <!-- 统计数据 -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div
              class="bg-white p-4 rounded-lg shadow-sm border border-slate-200 flex items-center gap-3"
            >
              <div
                class="w-10 h-10 rounded-full bg-sky-100 text-sky-600 flex items-center justify-center text-lg"
              >
                <i class="fa-solid fa-folder-tree"></i>
              </div>
              <div>
                <div class="text-xl font-bold text-slate-700">{{ totalFiles }}</div>
                <div class="text-slate-400 text-xs">资源文件</div>
              </div>
            </div>
            <div
              class="bg-white p-4 rounded-lg shadow-sm border border-slate-200 flex items-center gap-3"
            >
              <div
                class="w-10 h-10 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-lg"
              >
                <i class="fa-solid fa-cloud-arrow-up"></i>
              </div>
              <div>
                <div class="text-xl font-bold text-slate-700">{{ resources.length }}</div>
                <div class="text-slate-400 text-xs">资源总数</div>
              </div>
            </div>
            <div
              class="bg-white p-4 rounded-lg shadow-sm border border-slate-200 flex items-center gap-3"
            >
              <div
                class="w-10 h-10 rounded-full bg-violet-100 text-violet-600 flex items-center justify-center text-lg"
              >
                <i class="fa-solid fa-users"></i>
              </div>
              <div>
                <div class="text-xl font-bold text-slate-700">{{ activeUsersDisplay }}</div>
                <div class="text-slate-400 text-xs">近24小时活跃IP</div>
              </div>
            </div>
          </div>

          <!-- 最近上传 -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-5 sm:p-6">
            <div class="flex items-center justify-between mb-4">
              <div>
                <p class="text-xs uppercase tracking-[0.2em] text-slate-400 font-semibold">最近上传</p>
                <h3 class="text-lg font-bold text-slate-800 mt-1">最新更新的资源</h3>
              </div>
              <button
                class="text-xs text-sky-600 hover:text-sky-700 flex items-center gap-1"
                @click="switchTab('course')"
              >
                浏览全部 <i class="fa-solid fa-arrow-right"></i>
              </button>
            </div>

            <div
              v-if="recentUploads.length"
              class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3"
            >
              <div
                v-for="item in recentUploads"
                :key="item.id"
                class="group border border-slate-200 rounded-lg p-4 bg-slate-50/60 hover:bg-white hover:border-sky-200 transition cursor-pointer shadow-sm"
                @click="enterDetailView(item)"
              >
                <div class="flex items-start gap-3">
                  <div
                    class="w-10 h-10 rounded-full flex items-center justify-center text-sky-600 bg-sky-100 shadow-inner"
                  >
                    <i :class="item.type === 'course' ? 'fa-solid fa-graduation-cap' : item.type === 'tech' ? 'fa-solid fa-code' : 'fa-solid fa-bullhorn'"></i>
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 mb-1">
                      <p class="font-semibold text-slate-800 truncate">{{ item.title }}</p>
                      <span
                        v-if="item.tag"
                        :class="[
                          'text-[10px] px-1.5 py-0.5 rounded-full border',
                          item.tag === 'New'
                            ? 'bg-emerald-50 border-emerald-200 text-emerald-600'
                            : item.tag === 'Hot'
                            ? 'bg-rose-50 border-rose-200 text-rose-600'
                            : 'bg-violet-50 border-violet-200 text-violet-600'
                        ]"
                      >
                        {{ item.tag }}
                      </span>
                    </div>
                    <p class="text-xs text-slate-500 line-clamp-2 mb-2">{{ item.description }}</p>
                    <div class="flex items-center gap-3 text-[11px] text-slate-400">
                      <span><i class="fa-regular fa-clock mr-1"></i>{{ formatDate(item.updateDate) }}</span>
                      <span class="flex items-center gap-1">
                        <i class="fa-regular fa-folder"></i>{{ item.files?.length || 0 }} 文件
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="text-slate-400 text-sm py-6 text-center">
              暂无上传记录，去各模块添加一些资料吧～
            </div>
          </div>
        </section>

        <!-- 资源列表 -->
        <section v-else class="max-w-7xl mx-auto h-full flex flex-col">
          <div class="flex justify-between items-center mb-4 flex-shrink-0">
            <h2 class="text-xl font-bold text-slate-800">{{ getCurrentTabName }}</h2>
            <span class="text-xs text-slate-500">共 {{ filteredResources.length }} 个资源</span>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 pb-8">
            <TransitionGroup name="fade">
              <ResourceCard
                v-for="item in filteredResources"
                :key="item.id"
                :data="item"
                @click="enterDetailView(item)"
                @delete="handleDeleteResource(item)"
              />
            </TransitionGroup>
          </div>

          <!-- 空状态 -->
          <div v-if="filteredResources.length === 0" class="text-center py-20 text-slate-400">
            <i class="fa-regular fa-folder-open text-4xl mb-3 text-slate-300"></i>
            <p class="text-sm">暂无相关资料</p>
          </div>
        </section>
      </div>

      <!-- 场景 B: 详情视图 -->
      <div v-else class="h-full flex flex-col bg-white">
        <!-- 详情页头部工具栏 -->
        <div
          class="h-12 border-b border-sky-100 flex items-center justify-between px-4 bg-sky-50/30 flex-shrink-0"
        >
          <div class="flex items-center gap-3 overflow-hidden">
            <button
              @click="exitDetailView"
              class="text-slate-500 hover:text-sky-600 transition flex items-center gap-1 text-sm font-medium"
            >
              <i class="fa-solid fa-arrow-left"></i> 返回
            </button>
            <div class="h-4 w-px bg-slate-300"></div>
            <span class="font-bold text-slate-800 truncate text-sm sm:text-base">
              {{ activeResource.title }}
            </span>
            <span
              v-if="activeResource.tag"
              :class="[
                'text-xs px-1.5 py-0.5 rounded scale-90 origin-left',
                getStatusColor(activeResource.tag),
              ]"
            >
              {{ activeResource.tag }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="activeResource && handleDeleteResource(activeResource)"
              class="text-rose-600 bg-rose-50 border border-rose-200 text-xs px-3 py-1.5 rounded hover:bg-rose-100 transition flex items-center gap-1 shadow-sm"
            >
              <i class="fa-solid fa-trash-can"></i> 删除
            </button>
            <button
              @click="openCreateFolderModal(activeResource.id)"
              class="bg-emerald-600 text-white text-xs px-3 py-1.5 rounded hover:bg-emerald-700 transition flex items-center gap-1 shadow-sm"
            >
              <i class="fa-solid fa-folder-plus"></i> 新建文件夹
            </button>
            <button
              @click="handleUploadRequest(activeResource)"
              class="bg-sky-600 text-white text-xs px-3 py-1.5 rounded hover:bg-sky-700 transition flex items-center gap-1 shadow-sm shadow-sky-100"
            >
              <i class="fa-solid fa-cloud-arrow-up"></i> 上传
            </button>
          </div>
        </div>

        <!-- 详情页主体: 左右分栏 -->
        <div class="flex-grow flex overflow-hidden">
          <!-- 左侧边栏 -->
          <div
            class="w-full sm:w-1/3 md:w-1/4 lg:w-1/5 border-r border-slate-100 bg-slate-50/50 flex flex-col min-w-[220px]"
          >
            <!-- 资源简介 -->
            <div class="p-4 border-b border-slate-100 bg-white flex-shrink-0">
              <p class="text-xs text-slate-400 mb-1 font-semibold uppercase tracking-wider">简介</p>
              <p
                class="text-xs text-slate-600 leading-relaxed line-clamp-3"
                :title="activeResource.description"
              >
                {{ activeResource.description }}
              </p>
              <div class="mt-2 text-xs text-slate-400">更新于: {{ activeResource.updateDate }}</div>
            </div>

            <!-- 目录树 -->
            <div class="flex-grow overflow-y-auto p-2 custom-scrollbar">
              <div class="text-xs font-bold text-slate-500 mb-2 px-2 uppercase tracking-wider">
                资料目录
              </div>
              <FileTreeItem
                v-for="file in activeResource.files"
                :key="file.id"
                :item="file"
                :level="0"
                :active-file-id="currentPreviewFile?.id ?? null"
                @preview="setPreviewFile"
                @upload="handleUploadToFolder"
                @reorder="handleReorder"
                @download="downloadFile"
                @delete="handleDeleteFile"
                @rename="openRenameModal"
              />

              <div
                v-if="activeResource.files.length === 0"
                class="text-center mt-8 text-slate-400 text-xs"
              >
                <i class="fa-regular fa-folder-open mb-1 text-xl"></i>
                <p>空空如也</p>
              </div>
            </div>
          </div>

          <!-- 右侧主区域 -->
          <div
            class="hidden sm:flex flex-grow bg-slate-50 flex-col relative w-2/3 md:w-3/4 lg:w-4/5"
          >
            <!-- 右侧顶部信息栏 -->
            <div
              class="h-10 bg-white border-b border-slate-100 flex items-center justify-between px-2 shadow-sm flex-shrink-0 z-10"
            >
              <div class="text-xs text-slate-500 font-medium flex items-center gap-2">
                <i class="fa-solid fa-eye text-sky-500"></i>
                <span>文件预览</span>
              </div>

              <!-- 文件信息 -->
              <div
                v-if="currentPreviewFile"
                class="flex items-center gap-3 text-xs text-slate-500"
              >
                <span class="truncate max-w-[220px] sm:max-w-[320px]">{{ currentPreviewFile.name }}</span>
                <template v-if="isZoomablePreview(currentPreviewFile.type)">
                  <div class="h-4 w-px bg-slate-200"></div>
                  <div class="flex items-center gap-1">
                    <button
                      @click="fitDoc"
                      class="px-2 h-7 rounded-md border border-slate-200 hover:bg-slate-100 flex items-center justify-center text-[11px] text-slate-600"
                      title="自适应宽度"
                    >
                      适配
                    </button>
                    <button
                      @click="zoomDocOut"
                      class="w-7 h-7 rounded-md border border-slate-200 hover:bg-slate-100 flex items-center justify-center"
                      title="缩小"
                    >
                      <i class="fa-solid fa-minus text-slate-500"></i>
                    </button>
                    <span class="w-12 text-center text-slate-600 font-semibold">{{ docZoom }}%</span>
                    <button
                      @click="zoomDocIn"
                      class="w-7 h-7 rounded-md border border-slate-200 hover:bg-slate-100 flex items-center justify-center"
                      title="放大"
                    >
                      <i class="fa-solid fa-plus text-slate-500"></i>
                    </button>
                  </div>
                </template>
                <template v-else-if="isVhdFile(currentPreviewFile.type)">
                  <div class="h-4 w-px bg-slate-200"></div>
                  <button
                    @click="copyVhdContent"
                    :class="[
                      'px-3 h-8 rounded-md border flex items-center gap-1 text-[11px] shadow-sm transition bg-white hover:bg-slate-50 text-slate-600',
                      vhdCopyState.copied ? 'border-emerald-200' : 'border-slate-200'
                    ]"
                    title="复制内容"
                  >
                    <i
                      :class="[
                        vhdCopyState.copied ? 'fa-solid fa-check text-emerald-500' : 'fa-regular fa-copy text-sky-500',
                        'transition'
                      ]"
                    ></i>
                    <span class="font-medium" :class="vhdCopyState.copied ? 'text-emerald-600' : 'text-slate-700'">
                      {{ vhdCopyState.copied ? '已复制' : '复制代码' }}
                    </span>
                  </button>
                </template>
                <div class="h-4 w-px bg-slate-200"></div>
                <button
                  @click="handleDeleteFile(currentPreviewFile)"
                  class="text-slate-400 hover:text-red-500"
                  title="删除"
                >
                  <i class="fa-solid fa-trash"></i>
                </button>
                <button
                  @click="downloadFile(currentPreviewFile)"
                  class="text-slate-400 hover:text-sky-600"
                  title="下载"
                >
                  <i class="fa-solid fa-download"></i>
                </button>
              </div>
            </div>

            <!-- 内容区：文件预览 -->
            <div
              class="flex-grow overflow-auto flex items-start justify-center p-4 custom-scrollbar"
            >
              <div v-if="!currentPreviewFile" class="text-center text-slate-400">
                <i class="fa-solid fa-eye text-4xl mb-3 text-slate-200"></i>
                <p class="text-sm">请在左侧选择文件进行预览</p>
              </div>
              <template v-else>
                <ImagePreview
                  v-if="isImage(currentPreviewFile.type)"
                  ref="viewerRef"
                  :key="currentPreviewFile.id"
                  :url="resolveFileUrl(currentPreviewFile.url)"
                  @zoom-change="updateDocZoom"
                />
                <PdfPreview
                  v-else-if="currentPreviewFile.type === 'pdf'"
                  ref="viewerRef"
                  :key="currentPreviewFile.id"
                  :url="resolveFileUrl(currentPreviewFile.url)"
                  @zoom-change="updateDocZoom"
                />
                <DocPreview
                  v-else-if="isDocFile(currentPreviewFile.type)"
                  ref="viewerRef"
                  :key="currentPreviewFile.id"
                  :url="resolveFileUrl(currentPreviewFile.url)"
                  :file-name="currentPreviewFile.name"
                  :file-type="currentPreviewFile.type"
                  :html-url="isLegacyDocFile(currentPreviewFile.type) ? fileApi.getDocHtmlUrl(currentPreviewFile.id) : undefined"
                  @zoom-change="updateDocZoom"
                />
                <CodePreview
                  v-else-if="isVhdFile(currentPreviewFile.type)"
                  :key="currentPreviewFile.id"
                  :url="resolveFileUrl(currentPreviewFile.url)"
                  :file-name="currentPreviewFile.name"
                />
                <div v-else class="text-center">
                  <div class="bg-white p-8 rounded-xl shadow-sm border border-slate-100 inline-block">
                    <i
                      :class="getFileIcon(currentPreviewFile.type)"
                      class="text-6xl text-slate-200 mb-4 block"
                    ></i>
                    <p class="text-slate-600 mb-3 font-medium">此格式暂不支持在线预览</p>
                    <button
                      @click="downloadFile(currentPreviewFile)"
                      class="text-xs bg-sky-50 text-sky-600 px-4 py-2 rounded hover:bg-sky-100 transition"
                    >
                      下载文件查看
                    </button>
                  </div>
                </div>
              </template>
            </div>

          </div>

          <!-- 移动端覆盖层 -->
          <div
            class="sm:hidden flex-grow bg-white flex flex-col items-center justify-center p-6 text-center"
            v-if="!currentPreviewFile"
          >
            <p class="text-slate-400 text-sm">点击左侧目录查看文件</p>
            <p class="text-slate-300 text-xs mt-2">移动端暂不支持 AI 侧边栏</p>
          </div>
        </div>
      </div>
      <button
        v-if="!activeResource && currentTab !== 'home'"
        @click="openNewResourceModal(currentTab as 'course' | 'tech' | 'info')"
        class="fixed bottom-8 right-8 w-14 h-14 rounded-full bg-gradient-to-r from-sky-500 to-blue-600 text-white shadow-lg shadow-sky-200 hover:shadow-sky-300 hover:-translate-y-0.5 transition flex items-center justify-center text-2xl"
        title="新增卡片"
      >
        <i class="fa-solid fa-plus"></i>
      </button>
    </main>

    <!-- 上传弹窗 -->
    <UploadModal
      ref="uploadModalRef"
      :visible="uploadModalVisible"
      :target-id="uploadTargetId"
      :target-title="uploadTargetTitle"
      :resources="resources"
      @update:visible="uploadModalVisible = $event"
      @confirm="handleUploadConfirm"
    />

    <!-- 新建文件夹弹窗 -->
    <Teleport to="body">
      <div
        v-if="folderModal.visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
        @click.self="folderModal.visible = false"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
          <h3 class="text-lg font-bold text-slate-800 mb-4">
            <i class="fa-solid fa-folder-plus text-emerald-500 mr-2"></i>
            新建文件夹
          </h3>
          <input
            v-model="folderModal.folderName"
            type="text"
            placeholder="请输入文件夹名称"
            class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none"
            @keyup.enter="handleCreateFolder"
          />
          <div class="flex justify-end gap-3 mt-6">
            <button
              @click="folderModal.visible = false"
              class="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              取消
            </button>
            <button
              @click="handleCreateFolder"
              :disabled="!folderModal.folderName.trim() || folderModal.loading"
              class="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ folderModal.loading ? '创建中...' : '创建' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 新建资源弹窗 -->
    <Teleport to="body">
      <div
        v-if="newResourceModal.visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
        @click.self="newResourceModal.visible = false"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-slate-800 flex items-center gap-2">
              <i class="fa-solid fa-plus text-sky-500"></i>
              新建{{ tabs.find(t => t.id === newResourceModal.type)?.name || '资源' }}
            </h3>
            <button
              @click="newResourceModal.visible = false"
              class="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
            >
              <i class="fa-solid fa-xmark text-lg"></i>
            </button>
          </div>
          <div class="space-y-3">
            <div>
              <label class="block text-xs text-slate-500 mb-1">{{ newResourceLabels(newResourceModal.type).title }}</label>
              <input
                v-model="newResourceModal.title"
                type="text"
                :placeholder="newResourceLabels(newResourceModal.type).title"
                class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none"
              />
            </div>
            <div>
              <label class="block text-xs text-slate-500 mb-1">{{ newResourceLabels(newResourceModal.type).description }}</label>
              <textarea
                v-model="newResourceModal.description"
                rows="3"
                :placeholder="newResourceLabels(newResourceModal.type).description"
                class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none resize-none"
              ></textarea>
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button
              @click="newResourceModal.visible = false"
              class="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
            >
              取消
            </button>
            <button
              @click="handleCreateResource"
              :disabled="newResourceModal.loading || !newResourceModal.title.trim()"
              class="px-4 py-2 text-sm bg-sky-600 text-white rounded-lg hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 transition"
            >
              <i class="fa-solid fa-check"></i>
              {{ newResourceModal.loading ? '创建中...' : '创建' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 重命名弹窗 -->
    <Teleport to="body">
      <div
        v-if="renameModal.visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
        @click.self="renameModal.visible = false"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-slate-800 flex items-center gap-2">
              <i class="fa-solid fa-pen text-sky-500"></i>
              重命名
            </h3>
            <button
              @click="renameModal.visible = false"
              class="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
            >
              <i class="fa-solid fa-xmark text-lg"></i>
            </button>
          </div>
          <label class="block text-xs text-slate-500 mb-1">新名称</label>
          <div class="flex items-center gap-2">
            <input
              v-model="renameModal.baseName"
              type="text"
              placeholder="输入名称"
              class="flex-1 px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none"
              @keyup.enter="handleRenameSubmit"
            />
            <span
              v-if="renameModal.extension"
              class="px-3 py-2 bg-slate-100 border border-slate-200 rounded-md text-sm text-slate-500"
            >
              .{{ renameModal.extension }}
            </span>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button
              @click="renameModal.visible = false"
              class="px-5 py-2.5 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium transition"
            >
              取消
            </button>
            <button
              @click="handleRenameSubmit"
              :disabled="renameModal.loading || !renameModal.baseName.trim()"
              class="px-5 py-2.5 bg-sky-600 text-white rounded-lg text-sm font-medium hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 transition shadow-sm"
            >
              <i class="fa-solid fa-check"></i>
              {{ renameModal.loading ? '提交中...' : '确认' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 下载进度浮层 -->
    <Teleport to="body">
      <div
        v-if="downloadStatus !== 'idle'"
        class="fixed bottom-24 right-4 z-[12000] w-72"
      >
        <div class="bg-white border border-slate-200 rounded-2xl shadow-2xl p-4">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <i
                class="fa-solid"
                :class="{
                  'fa-spinner fa-spin text-sky-500': downloadStatus === 'downloading',
                  'fa-circle-check text-emerald-500': downloadStatus === 'success',
                  'fa-circle-exclamation text-red-500': downloadStatus === 'error'
                }"
              ></i>
              <span class="text-sm font-semibold text-slate-800">
                {{ downloadStatus === 'downloading' ? '正在下载' : downloadStatus === 'success' ? '下载完成' : '下载失败' }}
              </span>
            </div>
            <button
              @click="resetDownloadState"
              class="text-slate-400 hover:text-slate-600 transition"
            >
              <i class="fa-solid fa-xmark"></i>
            </button>
          </div>
          <p class="text-xs text-slate-500 truncate mb-3" v-if="downloadingFile">
            {{ downloadingFile.name }}
          </p>
          <div class="w-full bg-slate-100 rounded-full h-2 overflow-hidden mb-2">
            <div
              class="h-full rounded-full transition-all duration-300"
              :class="downloadStatus === 'error' ? 'bg-red-400' : 'bg-sky-500'"
              :style="{ width: `${Math.min(downloadProgress, 100)}%` }"
            ></div>
          </div>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <span>{{ Math.round(downloadProgress) }}%</span>
            <span v-if="downloadStatus === 'error'" class="text-red-500 truncate max-w-[140px]">{{ downloadMessage }}</span>
            <span v-else-if="downloadStatus === 'downloading' && !downloadTotalKnown">大小未知，已获取数据…</span>
            <span v-else-if="downloadStatus === 'success'" class="text-emerald-500">准备保存</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Toast Notification -->
    <Toast ref="toastRef" />

    <!-- Confirm Dialog -->
    <ConfirmDialog ref="confirmRef" />
  </div>
</template>
