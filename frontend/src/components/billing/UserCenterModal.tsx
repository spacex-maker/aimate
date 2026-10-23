import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ChevronLeft,
  ChevronRight,
  Coins,
  CreditCard,
  Receipt,
  Sparkles,
  UserRound,
  Wallet,
  X,
  Zap,
} from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { billingApi } from '../../api/billing'
import type { PaymentOrderView, PointLedgerView, PointPackageView } from '../../api/billing'
import { multiAgentApi } from '../../api/multiAgent'
import { useAuth } from '../../hooks/useAuth'

type Tab = 'overview' | 'profile' | 'recharge' | 'consume' | 'orders'

interface PaySession {
  orderNo: string
  productName: string
  priceYuan: number
  pointsLabel: string
  qrImageUrl: string
}

interface UserCenterModalProps {
  onClose: () => void
}

/** 优先用虎皮椒返回的二维码图；若只有支付链接则编码成二维码图（不跳转页面） */
function resolveQrImageUrl(checkout: { url: string | null; urlQrcode: string | null }): string | null {
  if (checkout.urlQrcode) return checkout.urlQrcode
  if (checkout.url) {
    return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&margin=8&data=${encodeURIComponent(checkout.url)}`
  }
  return null
}

export function UserCenterModal({ onClose }: UserCenterModalProps) {
  const [tab, setTab] = useState<Tab>('overview')
  const [paySession, setPaySession] = useState<PaySession | null>(null)
  const queryClient = useQueryClient()
  const { user } = useAuth()

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
      const qrImageUrl = resolveQrImageUrl(checkout)
      if (!qrImageUrl) throw new Error('未获取到支付二维码，请稍后重试')
      return {
        orderNo: order.orderNo,
        productName: pack.name,
        priceYuan: Number(pack.priceYuan),
        pointsLabel: `${pack.totalPoints.toLocaleString()} 积分${
          pack.bonusPoints > 0 ? `（含赠送 ${pack.bonusPoints.toLocaleString()}）` : ''
        }`,
        qrImageUrl,
      } satisfies PaySession
    },
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ['billing-orders'] })
      setPaySession(session)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  useEffect(() => {
    if (!paySession) return
    let stopped = false
    const tick = window.setInterval(() => {
      void (async () => {
        try {
          const o = await billingApi.getOrder(paySession.orderNo)
          if (stopped) return
          if (o.status === 'SUCCESS') {
            window.clearInterval(tick)
            setPaySession(null)
            queryClient.invalidateQueries({ queryKey: ['billing-me'] })
            queryClient.invalidateQueries({ queryKey: ['billing-orders'] })
            toast.success('支付成功，积分已到账')
            setTab('overview')
          }
        } catch {
          /* ignore poll errors */
        }
      })()
    }, 2500)
    return () => {
      stopped = true
      window.clearInterval(tick)
    }
  }, [paySession, queryClient])

  const balance = me?.pointsBalance ?? 0
  const displayName = user?.displayName || user?.username || '用户'
  const initial = displayName.charAt(0).toUpperCase()

  const tabs: { id: Tab; label: string; icon: typeof Wallet }[] = [
    { id: 'overview', label: '概览', icon: Wallet },
    { id: 'profile', label: '画像', icon: UserRound },
    { id: 'recharge', label: '充值', icon: Coins },
    { id: 'consume', label: '消费记录', icon: Zap },
    { id: 'orders', label: '订单', icon: Receipt },
  ]

  return (
    <div
      className="fixed inset-0 z-[80] flex items-center justify-center p-4 sm:p-6"
      style={{ background: 'radial-gradient(ellipse at center, rgba(8,12,24,0.72), rgba(0,0,0,0.82))' }}
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden rounded-2xl border border-white/[0.12] shadow-[0_40px_100px_-20px_rgba(0,0,0,0.85)]"
        style={{
          background:
            'linear-gradient(165deg, rgba(18,24,40,0.98) 0%, rgba(10,12,20,0.99) 45%, rgba(8,10,16,1) 100%)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ambient glow */}
        <div
          className="pointer-events-none absolute -top-24 left-1/2 h-56 w-[28rem] -translate-x-1/2 rounded-full opacity-40 blur-3xl"
          style={{ background: 'radial-gradient(circle, rgba(56,189,248,0.35), transparent 70%)' }}
        />
        <div
          className="pointer-events-none absolute top-20 -right-16 h-40 w-40 rounded-full opacity-25 blur-3xl"
          style={{ background: 'radial-gradient(circle, rgba(245,165,36,0.4), transparent 70%)' }}
        />

        {/* Header */}
        <div className="relative px-5 pt-5 pb-4 sm:px-7 sm:pt-6">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3.5 min-w-0">
              <div
                className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-lg font-bold text-white overflow-hidden"
                style={{
                  background: 'linear-gradient(145deg, #38bdf8, #6366f1 55%, #f5a524)',
                  boxShadow: '0 8px 28px -8px rgba(56,189,248,0.55)',
                }}
              >
                {user?.avatarUrl ? (
                  <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                ) : (
                  initial
                )}
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h2
                    className="truncate text-[1.15rem] font-bold tracking-tight text-white"
                    style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
                  >
                    {displayName}
                  </h2>
                  <span className="hidden sm:inline-flex items-center gap-1 rounded-full border border-cyan-400/25 bg-cyan-400/10 px-2 py-0.5 text-[10px] font-medium text-cyan-200/90">
                    <Sparkles className="h-2.5 w-2.5" />
                    AIMATEX
                  </span>
                </div>
                <p className="mt-0.5 truncate text-xs text-white/40">
                  @{user?.username ?? '—'} · 用户中心
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-white/10 bg-white/[0.03] p-2 text-white/45 transition-colors hover:border-white/20 hover:bg-white/[0.06] hover:text-white"
              title="关闭"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {/* Tabs */}
          <div className="mt-5 flex gap-1 rounded-xl border border-white/[0.08] bg-black/25 p-1">
            {tabs.map((t) => {
              const Icon = t.icon
              const active = tab === t.id
              return (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setTab(t.id)}
                  className={clsx(
                    'relative flex flex-1 items-center justify-center gap-1.5 rounded-lg px-2 py-2 text-[11px] sm:text-xs font-medium transition-all',
                    active
                      ? 'bg-white/[0.1] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]'
                      : 'text-white/40 hover:text-white/70',
                  )}
                >
                  <Icon className={clsx('h-3.5 w-3.5 shrink-0', active ? 'text-cyan-300' : '')} />
                  <span className="truncate">{t.label}</span>
                </button>
              )
            })}
          </div>
        </div>

        {/* Body */}
        <div className="relative flex-1 overflow-y-auto px-5 pb-6 sm:px-7">
          {tab === 'overview' && (
            <OverviewPane
              balance={balance}
              loading={meLoading}
              packCount={packages.length}
              onRecharge={() => setTab('recharge')}
              onConsume={() => setTab('consume')}
            />
          )}

          {tab === 'profile' && <ProfilePane />}

          {tab === 'recharge' && (
            <RechargePane
              packages={packages}
              loading={packagesLoading}
              buying={buyMutation.isPending}
              onBuy={(p) => buyMutation.mutate(p)}
            />
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

        {paySession && (
          <PayQrModal
            session={paySession}
            onClose={() => setPaySession(null)}
          />
        )}
      </div>
    </div>
  )
}

function PayQrModal({ session, onClose }: { session: PaySession; onClose: () => void }) {
  return createPortal(
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-black/65 p-4 backdrop-blur-md"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="w-full max-w-sm overflow-hidden rounded-2xl border border-white/12 shadow-2xl"
        style={{
          background: 'linear-gradient(165deg, rgba(22,28,44,0.98), rgba(10,12,18,0.99))',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b border-white/10 px-5 py-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-[0.14em] text-cyan-200/70">
              微信扫码支付
            </div>
            <h3
              className="mt-1 text-lg font-bold text-white"
              style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
            >
              {session.productName}
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-white/10 p-1.5 text-white/45 hover:bg-white/5 hover:text-white"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="px-5 py-5 text-center">
          <div
            className="text-3xl font-bold tabular-nums text-white"
            style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
          >
            ¥{session.priceYuan.toFixed(session.priceYuan % 1 === 0 ? 0 : 2)}
          </div>
          <p className="mt-1.5 text-xs text-white/45">{session.pointsLabel}</p>

          <div className="mx-auto mt-5 inline-flex rounded-2xl border border-white/10 bg-white p-3 shadow-lg">
            <img
              src={session.qrImageUrl}
              alt="微信支付二维码"
              className="h-52 w-52 object-contain"
            />
          </div>

          <p className="mt-4 text-[11px] leading-relaxed text-white/40">
            请使用微信扫一扫完成支付。到账后本页将自动刷新，无需跳转。
          </p>
          <p className="mt-2 font-mono text-[10px] text-white/25">订单 {session.orderNo}</p>

          <button
            type="button"
            onClick={onClose}
            className="mt-5 w-full rounded-xl border border-white/12 bg-white/[0.04] py-2.5 text-xs text-white/70 hover:bg-white/[0.08]"
          >
            关闭
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

function ProfilePane() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({
    queryKey: ['user-profile'],
    queryFn: () => multiAgentApi.getProfile(),
  })
  const [occupation, setOccupation] = useState('')
  const [age, setAge] = useState('')
  const [gender, setGender] = useState('UNSPECIFIED')
  const [bio, setBio] = useState('')

  useEffect(() => {
    if (!data) return
    setOccupation(data.occupation || '')
    setAge(data.age != null ? String(data.age) : '')
    setGender(data.gender || 'UNSPECIFIED')
    setBio(data.bio || '')
  }, [data])

  const saveMut = useMutation({
    mutationFn: () =>
      multiAgentApi.saveProfile({
        occupation,
        age: age ? Number(age) : undefined,
        gender,
        bio,
      }),
    onSuccess: () => {
      toast.success(data?.completed ? '画像已更新' : '画像已保存，正在生成秘书…')
      qc.invalidateQueries({ queryKey: ['user-profile'] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  if (isLoading) return <LoadingBlock />

  return (
    <div className="space-y-4 pt-2">
      <p className="text-xs text-white/40">
        完善职业、年龄、性别后，系统将生成贴合你的默认秘书 Agent（含独立 Soul）。
        {data?.completed ? ' 画像已完成。' : ' 尚未完成。'}
      </p>
      <label className="block text-xs text-white/50">
        职业
        <input
          value={occupation}
          onChange={(e) => setOccupation(e.target.value)}
          className="uc-profile-field mt-1 w-full rounded-lg border border-white/[0.06] bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/25 outline-none transition-colors focus:border-cyan-400/35 focus:bg-white/[0.055]"
          placeholder="如：产品经理"
        />
      </label>
      <label className="block text-xs text-white/50">
        年龄
        <input
          value={age}
          onChange={(e) => setAge(e.target.value.replace(/\D/g, ''))}
          className="uc-profile-field mt-1 w-full rounded-lg border border-white/[0.06] bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/25 outline-none transition-colors focus:border-cyan-400/35 focus:bg-white/[0.055]"
          placeholder="如：28"
        />
      </label>
      <div className="text-xs text-white/50">
        性别
        <div className="mt-1 flex gap-2">
          {[
            { id: 'MALE', label: '男' },
            { id: 'FEMALE', label: '女' },
            { id: 'OTHER', label: '其他' },
            { id: 'UNSPECIFIED', label: '不透露' },
          ].map((g) => (
            <button
              key={g.id}
              type="button"
              onClick={() => setGender(g.id)}
              className={clsx(
                'rounded-lg border px-3 py-1.5 text-xs transition-colors',
                gender === g.id
                  ? 'border-cyan-400/35 bg-cyan-400/10 text-cyan-100'
                  : 'border-white/[0.06] bg-white/[0.03] text-white/45 hover:border-white/10 hover:text-white/65'
              )}
            >
              {g.label}
            </button>
          ))}
        </div>
      </div>
      <label className="block text-xs text-white/50">
        简介（可选）
        <textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          rows={3}
          className="uc-profile-field mt-1 w-full rounded-lg border border-white/[0.06] bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/25 outline-none transition-colors focus:border-cyan-400/35 focus:bg-white/[0.055]"
          placeholder="工作习惯、沟通偏好等"
        />
      </label>
      <button
        type="button"
        disabled={saveMut.isPending}
        onClick={() => saveMut.mutate()}
        className="w-full rounded-xl bg-cyan-500/90 py-2.5 text-sm font-medium text-black hover:bg-cyan-400 disabled:opacity-40"
      >
        {saveMut.isPending ? '保存中…' : '保存画像'}
      </button>
    </div>
  )
}

function OverviewPane({
  balance,
  loading,
  packCount,
  onRecharge,
  onConsume,
}: {
  balance: number
  loading: boolean
  packCount: number
  onRecharge: () => void
  onConsume: () => void
}) {
  return (
    <div className="space-y-5">
      <div
        className="relative overflow-hidden rounded-2xl border border-white/[0.1] px-5 py-6 sm:px-7 sm:py-8"
        style={{
          background:
            'linear-gradient(135deg, rgba(56,189,248,0.12) 0%, rgba(99,102,241,0.08) 40%, rgba(245,165,36,0.06) 100%)',
        }}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'linear-gradient(rgba(255,255,255,0.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.5) 1px, transparent 1px)',
            backgroundSize: '28px 28px',
          }}
        />
        <div className="relative">
          <div className="flex items-center gap-2 text-[11px] font-medium uppercase tracking-[0.14em] text-cyan-200/70">
            <Coins className="h-3.5 w-3.5" />
            积分余额
          </div>
          <div
            className="mt-2 text-4xl sm:text-5xl font-bold tracking-tight text-white tabular-nums"
            style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
          >
            {loading ? (
              <span className="inline-block h-10 w-36 animate-pulse rounded-lg bg-white/10" />
            ) : (
              balance.toLocaleString()
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={onRecharge}
          className="group rounded-xl border border-cyan-400/20 bg-cyan-400/[0.06] px-4 py-3.5 text-left transition-all hover:border-cyan-400/35 hover:bg-cyan-400/[0.1]"
        >
          <CreditCard className="h-4 w-4 text-cyan-300" />
          <div className="mt-2 text-sm font-semibold text-white">立即充值</div>
          <div className="mt-0.5 text-[11px] text-white/40">
            {packCount > 0 ? `${packCount} 个套餐可选` : '微信支付到账'}
          </div>
        </button>
        <button
          type="button"
          onClick={onConsume}
          className="group rounded-xl border border-white/10 bg-white/[0.03] px-4 py-3.5 text-left transition-all hover:border-white/20 hover:bg-white/[0.06]"
        >
          <Zap className="h-4 w-4 text-amber-300/90" />
          <div className="mt-2 text-sm font-semibold text-white">消费明细</div>
          <div className="mt-0.5 text-[11px] text-white/40">查看扣费流水</div>
        </button>
      </div>
    </div>
  )
}

function RechargePane({
  packages,
  loading,
  buying,
  onBuy,
}: {
  packages: PointPackageView[]
  loading: boolean
  buying: boolean
  onBuy: (p: PointPackageView) => void
}) {
  if (loading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-36 animate-pulse rounded-2xl bg-white/[0.04]" />
        ))}
      </div>
    )
  }
  if (!packages.length) {
    return <EmptyState title="暂无可用套餐" hint="请稍后再试，或联系管理员上架充值套餐" />
  }

  const mid = Math.floor((packages.length - 1) / 2)

  return (
    <div className="space-y-4">
      <p className="text-xs text-white/40">选择套餐后弹出微信收款码，扫码支付成功后积分自动到账。</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {packages.map((pack, idx) => {
          const featured = idx === mid || (packages.length === 1 && idx === 0)
          return (
            <div
              key={pack.id}
              className={clsx(
                'relative flex flex-col overflow-hidden rounded-2xl border p-4 transition-all',
                featured
                  ? 'border-cyan-400/30 bg-gradient-to-br from-cyan-400/[0.1] via-indigo-500/[0.06] to-amber-400/[0.05]'
                  : 'border-white/[0.1] bg-white/[0.025] hover:border-white/20',
              )}
            >
              {featured && (
                <span className="absolute right-3 top-3 rounded-full border border-amber-400/30 bg-amber-400/15 px-2 py-0.5 text-[10px] font-semibold text-amber-200">
                  推荐
                </span>
              )}
              <div className="text-sm font-semibold text-white pr-12">{pack.name}</div>
              <div
                className="mt-3 text-3xl font-bold tracking-tight text-white"
                style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
              >
                ¥{Number(pack.priceYuan).toFixed(pack.priceYuan % 1 === 0 ? 0 : 2)}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[11px]">
                <span className="rounded-md border border-white/10 bg-black/20 px-1.5 py-0.5 font-mono text-cyan-200/90">
                  {pack.totalPoints.toLocaleString()} 积分
                </span>
                {pack.bonusPoints > 0 && (
                  <span className="rounded-md border border-emerald-400/20 bg-emerald-400/10 px-1.5 py-0.5 text-emerald-300/90">
                    赠 {pack.bonusPoints.toLocaleString()}
                  </span>
                )}
              </div>
              {pack.description ? (
                <p className="mt-2 line-clamp-2 text-[11px] leading-relaxed text-white/35">{pack.description}</p>
              ) : (
                <div className="mt-2 flex-1" />
              )}
              <button
                type="button"
                disabled={buying}
                onClick={() => onBuy(pack)}
                className={clsx(
                  'mt-4 w-full rounded-xl py-2.5 text-xs font-semibold transition-all disabled:opacity-50',
                  featured
                    ? 'bg-gradient-to-r from-cyan-500 to-indigo-500 text-white shadow-[0_8px_24px_-8px_rgba(56,189,248,0.55)] hover:brightness-110'
                    : 'border border-white/15 bg-white/[0.06] text-white hover:bg-white/[0.1]',
                )}
              >
                {buying ? '处理中…' : '微信支付'}
              </button>
            </div>
          )
        })}
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
  if (loading) return <LoadingBlock />
  if (!items.length) return <EmptyState title="暂无消费记录" hint="发起对话并产生扣费后，明细会出现在这里" />

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        {items.map((row) => (
          <div
            key={row.id}
            className="group flex items-start gap-3 rounded-xl border border-white/[0.08] bg-white/[0.02] px-3.5 py-3 transition-colors hover:border-white/15 hover:bg-white/[0.04]"
          >
            <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-rose-400/20 bg-rose-400/10">
              <Zap className="h-3.5 w-3.5 text-rose-300" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 text-[13px] font-medium text-white/85 truncate">
                  {userFacingLedgerRemark(row.remark)}
                </div>
                <div className="shrink-0 font-mono text-sm font-semibold text-rose-300 tabular-nums">
                  {row.delta}
                </div>
              </div>
              <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] text-white/35">
                <span>{row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '—'}</span>
                <span className="font-mono">余额 {row.balanceAfter.toLocaleString()}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
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
  if (loading) return <LoadingBlock />
  if (!items.length) return <EmptyState title="暂无订单" hint="完成充值后，支付订单会显示在此" />

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        {items.map((o) => (
          <div
            key={o.id}
            className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-3.5 py-3 transition-colors hover:border-white/15 hover:bg-white/[0.04]"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <CreditCard className="h-3.5 w-3.5 shrink-0 text-cyan-300/80" />
                  <span className="truncate font-mono text-[12px] text-white/80">{o.orderNo}</span>
                </div>
                <div className="mt-1.5 text-[10px] text-white/35">
                  {o.paymentMethod === 'system_gift' ? '系统赠送' : o.paymentMethod === 'wechat_pay_xunhu' ? '微信支付' : (o.paymentMethod || '订单')}
                  {' · '}
                  {o.createTime ? new Date(o.createTime).toLocaleString('zh-CN') : '—'}
                  {o.paidAt ? ` · 支付于 ${new Date(o.paidAt).toLocaleString('zh-CN')}` : ''}
                </div>
              </div>
              <div className="shrink-0 text-right">
                <div
                  className="text-base font-bold text-white tabular-nums"
                  style={{ fontFamily: 'Syne, Outfit, sans-serif' }}
                >
                  {o.paymentMethod === 'system_gift' ? '系统赠送' : `¥${Number(o.amountYuan).toFixed(2)}`}
                </div>
                <StatusPill status={o.status} />
              </div>
            </div>
          </div>
        ))}
      </div>
      <Pager page={page} totalPages={totalPages} onPage={onPage} />
    </div>
  )
}

function StatusPill({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    SUCCESS: { label: '已支付', cls: 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300' },
    PENDING: { label: '待支付', cls: 'border-amber-400/25 bg-amber-400/10 text-amber-200' },
    CLOSED: { label: '已关闭', cls: 'border-white/15 bg-white/5 text-white/45' },
  }
  const m = map[status] ?? { label: status, cls: 'border-white/15 bg-white/5 text-white/50' }
  return (
    <span className={clsx('mt-1 inline-flex rounded-full border px-2 py-0.5 text-[10px] font-medium', m.cls)}>
      {m.label}
    </span>
  )
}

/** 用户可见标题：不展示成本/倍率等内部计费细节（兼容历史 remark）。 */
function userFacingLedgerRemark(remark?: string | null): string {
  const raw = (remark ?? '').trim()
  if (!raw) return '对话扣费'
  const costIdx = raw.indexOf(' · 成本')
  if (costIdx > 0) return raw.slice(0, costIdx).trim() || '对话扣费'
  if (raw.includes('成本¥') || raw.includes('成本￥')) return '对话扣费'
  return raw
}

function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="flex items-center justify-center gap-3 pt-2">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        className="inline-flex items-center gap-1 rounded-lg border border-white/12 bg-white/[0.03] px-2.5 py-1.5 text-[11px] text-white/60 transition-colors hover:bg-white/[0.06] disabled:opacity-30"
      >
        <ChevronLeft className="h-3.5 w-3.5" />
        上一页
      </button>
      <span className="min-w-[3.5rem] text-center text-[11px] tabular-nums text-white/40">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
        className="inline-flex items-center gap-1 rounded-lg border border-white/12 bg-white/[0.03] px-2.5 py-1.5 text-[11px] text-white/60 transition-colors hover:bg-white/[0.06] disabled:opacity-30"
      >
        下一页
        <ChevronRight className="h-3.5 w-3.5" />
      </button>
    </div>
  )
}

function EmptyState({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-white/10 bg-white/[0.02] px-6 py-14 text-center">
      <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/[0.04]">
        <Sparkles className="h-5 w-5 text-white/25" />
      </div>
      <div className="mt-4 text-sm font-medium text-white/70">{title}</div>
      <p className="mt-1.5 max-w-xs text-[11px] leading-relaxed text-white/35">{hint}</p>
    </div>
  )
}

function LoadingBlock() {
  return (
    <div className="space-y-2">
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-[4.25rem] animate-pulse rounded-xl bg-white/[0.04]" />
      ))}
    </div>
  )
}
