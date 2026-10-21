import { http } from './httpClient'

export interface PointPackageView {
  id: number
  name: string
  priceYuan: number
  points: number
  bonusPoints: number
  totalPoints: number
  description?: string | null
  sortOrder?: number
  enabled?: boolean
}

export interface PaymentOrderView {
  id: number
  orderNo: string
  userId?: number
  productType: string
  productId: number
  amountYuan: number
  paymentMethod: string
  status: string
  transactionId?: string | null
  metaJson?: string | null
  paidAt?: string | null
  createTime?: string | null
}

export interface PointLedgerView {
  id: number
  delta: number
  balanceAfter: number
  reason: string
  remark?: string | null
  sessionId?: string | null
  createTime?: string | null
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export const billingApi = {
  me: () => http<{ pointsBalance: number }>('/api/billing/me'),

  packages: () => http<PointPackageView[]>('/api/billing/packages'),

  createPointPackOrder: (id: number) =>
    http<PaymentOrderView>('/api/billing/orders/point-pack', {
      method: 'POST',
      body: JSON.stringify({ id }),
    }),

  checkout: (orderNo: string) =>
    http<{ url: string | null; urlQrcode: string | null }>('/api/billing/wechat/checkout', {
      method: 'POST',
      body: JSON.stringify({ orderNo }),
    }),

  orders: (page = 0, size = 20) => {
    const qs = new URLSearchParams({ page: String(page), size: String(size) })
    return http<PageResult<PaymentOrderView>>(`/api/billing/orders?${qs}`)
  },

  ledger: (params?: { reason?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams()
    if (params?.reason) qs.set('reason', params.reason)
    qs.set('page', String(params?.page ?? 0))
    qs.set('size', String(params?.size ?? 20))
    return http<PageResult<PointLedgerView>>(`/api/billing/ledger?${qs}`)
  },
}
