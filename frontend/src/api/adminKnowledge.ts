import { http } from './httpClient'

export interface KbCategoryNode {
  id: number
  parentId?: number | null
  code: string
  name: string
  description?: string | null
  sortOrder: number
  enabled: boolean
  depth?: number
  children?: KbCategoryNode[]
}

export interface KnowledgeBaseBrief {
  id: number
  publicId: string
  categoryId: number
  code?: string | null
  name: string
  summary?: string | null
  tags?: string | null
  enabled?: boolean
}

export interface KnowledgeBaseDetail extends KnowledgeBaseBrief {
  content?: string | null
  sortOrder?: number
  categoryName?: string
  categoryCode?: string
  linkedCatalogCount?: number
  createTime?: string
  updateTime?: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export const adminKnowledgeApi = {
  categoryTree: (enabledOnly = false) =>
    http<KbCategoryNode[]>(`/api/admin/knowledge/categories/tree?enabledOnly=${enabledOnly}`),

  saveCategory: (
    body: {
      parentId?: number | null
      code: string
      name: string
      description?: string
      sortOrder?: number
      enabled?: boolean
    },
    id?: number,
  ) =>
    http<KbCategoryNode>(
      id ? `/api/admin/knowledge/categories/${id}` : '/api/admin/knowledge/categories',
      { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) },
    ),

  pageBases: (params: {
    categoryId?: number | null
    enabled?: boolean | null
    q?: string
    page?: number
    size?: number
  }) => {
    const sp = new URLSearchParams()
    if (params.categoryId != null) sp.set('categoryId', String(params.categoryId))
    if (params.enabled != null) sp.set('enabled', String(params.enabled))
    if (params.q) sp.set('q', params.q)
    sp.set('page', String(params.page ?? 0))
    sp.set('size', String(params.size ?? 20))
    return http<PageResult<KnowledgeBaseDetail>>(`/api/admin/knowledge/bases?${sp}`)
  },

  getBase: (id: number) => http<KnowledgeBaseDetail>(`/api/admin/knowledge/bases/${id}`),

  saveBase: (
    body: {
      categoryId: number
      code?: string
      name: string
      summary?: string
      content?: string
      tags?: string
      sortOrder?: number
      enabled?: boolean
    },
    id?: number,
  ) =>
    http<KnowledgeBaseDetail>(
      id ? `/api/admin/knowledge/bases/${id}` : '/api/admin/knowledge/bases',
      { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) },
    ),

  catalogBases: (catalogId: number) =>
    http<KnowledgeBaseBrief[]>(`/api/admin/knowledge/catalog/${catalogId}/bases`),

  replaceCatalogBases: (catalogId: number, knowledgeBaseIds: number[]) =>
    http<KnowledgeBaseBrief[]>(`/api/admin/knowledge/catalog/${catalogId}/bases`, {
      method: 'PUT',
      body: JSON.stringify({ knowledgeBaseIds }),
    }),
}
