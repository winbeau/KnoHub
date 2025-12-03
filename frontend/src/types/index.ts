export interface FileItem {
  id: number
  name: string
  isFolder: boolean
  type?: string
  size?: string
  url?: string
  children?: FileItem[]
}

export interface Resource {
  id: number
  type: 'course' | 'tech' | 'info'
  title: string
  description: string
  tag?: 'New' | 'Hot' | 'Rec'
  updateDate: string
  createDate?: string
  files: FileItem[]
}

export interface Tab {
  id: string
  name: string
  icon: string
}

export interface ChatMessage {
  role: 'user' | 'model'
  text: string
}

export interface UploadModalState {
  visible: boolean
  targetId: number | null
  targetTitle: string
  targetFolderId: number | null
  selectedFile: File | null
}
