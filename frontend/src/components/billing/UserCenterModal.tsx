import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { billingApi } from '../../api/billing'
import type { PaymentOrderView, PointLedgerView, PointPackageView } from '../../api/billing'

type Tab = 'overview' | 'recharge' | 'consume' | 'orders'

interface UserCenterModalProps {
  onClose: () => void
}

export function UserCenterModal({ onClose }: UserCenterModalProps) {
  const [tab, setTab] = useState<Tab>('overview')
  const queryClient = useQueryClient()

  const { data: me, isLoading: meLoading } = useQuery({
    queryKey: ['billing-me'],
    queryFn: () => billingApi.me(),
  })

  const { data: packages = [], isLoading: packagesLoading } = useQuery({
    queryKey: ['billing-packages'],
    queryFn: () => billingApi.packages(),
    enabled: tab === 'recharge' || tab === 'overview',
  })

  const [ledgerPage, setLedgerPage] = useState(0)
  const { data: ledger, isLoading: ledgerLoading } = useQuery({
    queryKey: ['billing-ledger', ledgerPage],
    queryFn: () => billingApi.ledger({ reason: 'CHAT_DEDUCT', page: ledgerPage, size: 20 }),
    enabled: tab === 'consume',
  })

  const [orderPage, setOrderPage] = useState(0)
  const { data: orders, isLoading: ordersLoading } = useQuery({
    queryKey: ['billing-orders', orderPage],
    queryFn: () => billingApi.orders(orderPage, 20),
    enabled: tab === 'orders',
  })

  const buyMutation = useMutation({
    mutationFn: async (pack: PointPackageView) => {
      const order = await billingApi.createPointPackOrder(pack.id)
      const checkout = await billingApi.checkout(order.orderNo)
      return checkout
    },
    onSuccess: (checkout) => {
      queryClient.invalidateQueries({ queryKey: ['billing-orders'] })
      const url = checkout.url || checkout.urlQrcode
      if (url) {
        window.open(url, '_blank', 'noopener,noreferrer')
        toast.success('已打开微信支付，支付完成后积分将自动到账')
      } else {
        toast.error('未获取到支付链接，请稍后重试')
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const tabs: { id: Tab; label: string }[] = [
    { id: 'overview', label: '概览' },
    { id: 'recharge', label: '充值' },
    { id: 'consume', label: '消费记录' },
    { id: 'orders', label: '订单' },
  ]

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div
        className="w-full max-w-lg max-h-[85vh] flex flex-col rounded-xl border border-white/15 bg-[#12141a] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <h2 className="text-sm font-semibold text-white">用户中心</h2>
          <button type="button" onClick={onClose} className="text-white/40 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex gap-1 px-3 pt-3">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={clsx(
                'px-2.5 py-1.5 rounded text-xs transition-colors',
                tab === t.id ? 'bg-white/10 text-white' : 'text-white/45 hover:text-white/80',
              )}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {tab === 'overview' && (
            <div className="space-y-4">
              <div className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-5 text-center">
                <div className="text-[11px] text-white/40">积分余额</div>
                <div className="mt-1 text-3xl font-mono text-white">
                  {meLoading ? '…' : (me?.pointsBalance ?? 0).toLocaleString()}
                </div>
              </div>
              <p className="text-xs text-white/40 leading-relaxed">
                对话按模型积分倍率扣费。余额不足时无法发送消息，请先充值。
              </p>
              <button
                type="button"
                onClick={() => setTab('recharge')}
                className="w-full py-2 rounded-lg bg-blue-600 text-sm text-white hover:bg-blue-500"
              >
                去充值
              </button>
            </div>
          )}

          {tab === 'recharge' && (
            <div className="space-y-3">
              {packagesLoading ? (
                <div className="text-center py-8 text-xs text-white/40">加载套餐…</div>
              ) : packages.length === 0 ? (
                <div className="text-center py-8 text-xs text-white/30">暂无可用套餐</div>
              ) : (
                packages.map((pack) => (
                  <div
                    key={pack.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-white/10 px-3 py-3"
                  >
                    <div className="min-w-0">
                      <div className="text-sm text-white font-medium">{pack.name}</div>
                      <div className="text-[11px] text-white/45 mt-0.5">
                        {pack.totalPoints.toLocaleString()} 积分
                        {pack.bonusPoints > 0 ? `（含赠送 ${pack.bonusPoints}）` : ''}
                        {pack.description ? ` · ${pack.description}` : ''}
                      </div>
                    </div>
                    <button
                      type="button"
                      disabled={buyMutation.isPending}
                      onClick={() => buyMutation.mutate(pack)}
                      className="shrink-0 px-3 py-1.5 rounded bg-blue-600 text-xs text-white hover:bg-blue-500 disabled:opacity-50"
                    >
                      ¥{Number(pack.priceYuan).toFixed(2)}
                    </button>
                  </div>
                ))
              )}
            </div>
          )}

          {tab === 'consume' && (
            <LedgerList
              loading={ledgerLoading}
              items={ledger?.items ?? []}
              page={ledgerPage}
              totalPages={ledger?.totalPages ?? 0}
              onPage={setLedgerPage}
            />
          )}

          {tab === 'orders' && (
            <OrderList
              loading={ordersLoading}
              items={orders?.items ?? []}
              page={orderPage}
              totalPages={orders?.totalPages ?? 0}
              onPage={setOrderPage}
            />
          )}
        </div>
      </div>
    </div>
  )
}

function LedgerList({
  loading,
  items,
  page,
  totalPages,
  onPage,
}: {
  loading: boolean
  items: PointLedgerView[]
  page: number
  totalPages: number
  onPage: (p: number) => void
}) {
  if (loading) return <div className="text-center py-8 text-xs text-white/40">加载中…</div>
  if (!items.length) return <div className="text-center py-8 text-xs text-white/30">暂无消费记录</div>
  return (
    <div className="space-y-2">
      {items.map((row) => (
        <div key={row.id} className="rounded-lg border border-white/10 px-3 py-2 text-xs">
          <div className="flex justify-between text-white/80">
            <span>{row.remark || '对话扣费'}</span>
            <span className="font-mono text-rose-300">{row.delta}</span>
          </div>
          <div className="mt-1 text-[10px] text-white/35 flex justify-between">
            <span>{row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '—'}</span>
            <span>余额 {row.balanceAfter.toLocaleString()}</span>
          </div>
        </div>
      ))}
      <Pager page={page} totalPages={totalPages} onPage={onPage} />
    </div>
  )
}

function OrderList({
  loading,
  items,
  page,
  totalPages,
  onPage,
}: {
  loading: boolean
  items: PaymentOrderView[]
  page: number
  totalPages: number
  onPage: (p: number) => void
}) {
  if (loading) return <div className="text-center py-8 text-xs text-white/40">加载中…</div>
  if (!items.length) return <div className="text-center py-8 text-xs text-white/30">暂无订单</div>
  return (
    <div className="space-y-2">
      {items.map((o) => (
        <div key={o.id} className="rounded-lg border border-white/10 px-3 py-2 text-xs">
          <div className="flex justify-between text-white/80">
            <span className="font-mono">{o.orderNo}</span>
            <span>¥{Number(o.amountYuan).toFixed(2)}</span>
          </div>
          <div className="mt-1 text-[10px] text-white/35 flex justify-between">
            <span>{o.createTime ? new Date(o.createTime).toLocaleString('zh-CN') : '—'}</span>
            <span>{statusLabel(o.status)}</span>
          </div>
        </div>
      ))}
      <Pager page={page} totalPages={totalPages} onPage={onPage} />
    </div>
  )
}

function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="flex justify-center gap-2 pt-2">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        className="px-2 py-1 text-[11px] rounded border border-white/15 text-white/60 disabled:opacity-30"
      >
        上一页
      </button>
      <span className="text-[11px] text-white/40 self-center">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
        className="px-2 py-1 text-[11px] rounded border border-white/15 text-white/60 disabled:opacity-30"
      >
        下一页
      </button>
    </div>
  )
}

function statusLabel(s: string) {
  if (s === 'SUCCESS') return '已支付'
  if (s === 'PENDING') return '待支付'
  if (s === 'CLOSED') return '已关闭'
  return s
}
