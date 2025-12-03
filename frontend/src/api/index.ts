/**
 * API service for KnoHub backend communication
 */

const normalizeBaseUrl = (url: string) => url.replace(/\/$/, '')

const buildDefaultApiBaseUrl = () => {
  const isLocalHost = (hostname: string) =>
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '0.0.0.0' ||
    hostname.startsWith('192.168.') ||
    hostname.endsWith('.local')

  // In dev we use a relative path so Vite proxy can forward to backend.
  if (import.meta.env.DEV) return '/api'

  const { protocol, hostname, port } = window.location
  const useLocalBackend = isLocalHost(hostname)

  // When preview/static served on a Vite port (e.g. 4173/517x), default to backend 8080.
  const apiPort =
    useLocalBackend && port && port !== '8080'
      ? '8080'
      : useLocalBackend && !port
        ? '8080'
        : port

  const hostPort = apiPort ? `${hostname}:${apiPort}` : hostname
  return `${protocol}//${hostPort}/api`
}

export const API_BASE_URL = normalizeBaseUrl(
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() || buildDefaultApiBaseUrl()
)

export const API_ORIGIN = API_BASE_URL.replace(/\/api$/, '')

/**
 * Metrics APIs
 */
export const metricsApi = {
  /**
   * Get unique active visitor count
   */
  async getActiveUsers(): Promise<number> {
    const response = await fetch(`${API_BASE_URL}/metrics/active-users`)
    const result: ApiResponse<number> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  }
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface FileItemDTO {
  id: number
  name: string
  isFolder: boolean
  type?: string
  size?: string
  url?: string
  children?: FileItemDTO[]
}

export interface ResourceDTO {
  id: number
  type: string
  title: string
  description: string
  tag?: string
  updateDate: string
  createDate?: string
  files: FileItemDTO[]
}

export interface CreateFolderRequest {
  name: string
  parentFolderId?: number | null
}

/**
 * Resource APIs
 */
export const resourceApi = {
  /**
   * Get all resources
   */
  async getAll(): Promise<ResourceDTO[]> {
    const response = await fetch(`${API_BASE_URL}/resources`)
    const result: ApiResponse<ResourceDTO[]> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Get resource by ID
   */
  async getById(id: number): Promise<ResourceDTO> {
    const response = await fetch(`${API_BASE_URL}/resources/${id}`)
    const result: ApiResponse<ResourceDTO> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Get resources by type
   */
  async getByType(type: string): Promise<ResourceDTO[]> {
    const response = await fetch(`${API_BASE_URL}/resources/type/${type}`)
    const result: ApiResponse<ResourceDTO[]> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Search resources
   */
  async search(keyword: string): Promise<ResourceDTO[]> {
    const response = await fetch(`${API_BASE_URL}/resources/search?keyword=${encodeURIComponent(keyword)}`)
    const result: ApiResponse<ResourceDTO[]> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Create a new resource
   */
  async create(resource: Partial<ResourceDTO>): Promise<ResourceDTO> {
    const response = await fetch(`${API_BASE_URL}/resources`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(resource)
    })
    const result: ApiResponse<ResourceDTO> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Update a resource
   */
  async update(id: number, resource: Partial<ResourceDTO>): Promise<ResourceDTO> {
    const response = await fetch(`${API_BASE_URL}/resources/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(resource)
    })
    const result: ApiResponse<ResourceDTO> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Delete a resource
   */
  async delete(id: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/resources/${id}`, {
      method: 'DELETE'
    })
    const result: ApiResponse<void> = await response.json()
    if (!result.success) throw new Error(result.message)
  }
}

/**
 * File APIs
 */
export const fileApi = {
  /**
   * Upload a file to a resource
   */
  async upload(resourceId: number, file: File, folderId?: number | null): Promise<FileItemDTO> {
    const formData = new FormData()
    formData.append('file', file)

    let url = `${API_BASE_URL}/files/${resourceId}/upload`
    if (folderId) {
      url += `?folderId=${folderId}`
    }

    const response = await fetch(url, {
      method: 'POST',
      body: formData
    })

    const rawText = await response.text()
    const contentType = response.headers.get('content-type') || ''
    let parsed: ApiResponse<FileItemDTO> | null = null

    // Try to parse JSON when possible; if not, fall back to raw text for error handling.
    if (contentType.includes('application/json')) {
      try {
        parsed = JSON.parse(rawText) as ApiResponse<FileItemDTO>
      } catch {}
    } else {
      try {
        parsed = JSON.parse(rawText) as ApiResponse<FileItemDTO>
      } catch {}
    }

    if (!response.ok) {
      if (response.status === 413) {
        const limitMsg = parsed?.message || rawText.trim()
        throw new Error(limitMsg || '上传失败：请求体过大 (413)。如经由 Nginx，请调整 client_max_body_size（默认 1M）。')
      }

      const fallbackMsg = parsed?.message || rawText.trim()
      throw new Error(fallbackMsg || `上传失败（HTTP ${response.status}）`)
    }

    if (!parsed) {
      throw new Error('上传失败：服务器返回异常响应')
    }

    if (!parsed.success) throw new Error(parsed.message || '上传失败')
    return parsed.data
  },

  /**
   * Upload multiple files to a resource
   */
  async uploadBatch(resourceId: number, files: File[], folderId?: number | null): Promise<FileItemDTO[]> {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))

    let url = `${API_BASE_URL}/files/${resourceId}/upload/batch`
    if (folderId) {
      url += `?folderId=${folderId}`
    }

    const response = await fetch(url, {
      method: 'POST',
      body: formData
    })

    const rawText = await response.text()
    const contentType = response.headers.get('content-type') || ''
    let parsed: ApiResponse<FileItemDTO[]> | null = null

    if (contentType.includes('application/json')) {
      try {
        parsed = JSON.parse(rawText) as ApiResponse<FileItemDTO[]>
      } catch {}
    } else {
      try {
        parsed = JSON.parse(rawText) as ApiResponse<FileItemDTO[]>
      } catch {}
    }

    if (!response.ok) {
      if (response.status === 413) {
        const limitMsg = parsed?.message || rawText.trim()
        throw new Error(limitMsg || '上传失败：请求体过大 (413)。如经由 Nginx，请调整 client_max_body_size（默认 1M）。')
      }

      const fallbackMsg = parsed?.message || rawText.trim()
      throw new Error(fallbackMsg || `上传失败（HTTP ${response.status}）`)
    }

    if (!parsed) {
      throw new Error('上传失败：服务器返回异常响应')
    }

    if (!parsed.success) throw new Error(parsed.message || '上传失败')
    return parsed.data || []
  },

  /**
   * Delete a file (soft delete)
   */
  async deleteFile(fileId: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/files/${fileId}`, {
      method: 'DELETE'
    })
    const result: ApiResponse<void> = await response.json()
    if (!result.success) throw new Error(result.message)
  },

  /**
   * Create a folder
   */
  async createFolder(resourceId: number, name: string, parentFolderId?: number | null): Promise<FileItemDTO> {
    const response = await fetch(`${API_BASE_URL}/files/${resourceId}/folders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, parentFolderId })
    })
    const result: ApiResponse<FileItemDTO> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Delete a folder (soft delete)
   */
  async deleteFolder(folderId: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/files/folders/${folderId}`, {
      method: 'DELETE'
    })
    const result: ApiResponse<void> = await response.json()
    if (!result.success) throw new Error(result.message)
  },

  /**
   * Rename a file or folder
   */
  async rename(fileId: number, newName: string): Promise<FileItemDTO> {
    const response = await fetch(`${API_BASE_URL}/files/${fileId}/rename`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ newName })
    })
    const result: ApiResponse<FileItemDTO> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Reorder files/folders
   */
  async reorder(dragId: number, dropId: number, position: 'before' | 'after' | 'inside'): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/files/reorder`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ dragId, dropId, position })
    })
    const result: ApiResponse<void> = await response.json()
    if (!result.success) throw new Error(result.message)
  },

  /**
   * Get all files for a resource
   */
  async getResourceFiles(resourceId: number): Promise<FileItemDTO[]> {
    const response = await fetch(`${API_BASE_URL}/files/${resourceId}`)
    const result: ApiResponse<FileItemDTO[]> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  },

  /**
   * Get download URL for a file
   */
  getDownloadUrl(resourceId: number, filename: string): string {
    return `${API_BASE_URL}/files/${resourceId}/download/${encodeURIComponent(filename)}`
  },

  /**
   * Get HTML preview for .doc files
   */
  getDocHtmlUrl(fileId: number): string {
    return `${API_BASE_URL}/files/${fileId}/html`
  },

  async getDocHtml(fileId: number): Promise<string> {
    const response = await fetch(`${API_BASE_URL}/files/${fileId}/html`)
    const result: ApiResponse<string> = await response.json()
    if (!result.success) throw new Error(result.message)
    return result.data
  }
}
