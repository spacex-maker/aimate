type Props = {
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
  label?: string
  loading?: boolean
}

/** 运营台列表统一分页条：页码从 0 起。 */
export function AdminPager({
  page,
  size,
  total,
  onPageChange,
  label = '条',
  loading,
}: Props) {
  const totalPages = Math.max(1, Math.ceil(total / Math.max(1, size)))
  if (total <= 0) return null

  return (
    <div className="admin-pager">
      <span>
        共 {total} {label} · 第 {page + 1}/{totalPages} 页
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          className="admin-pager__btn"
          disabled={page <= 0 || loading}
          onClick={() => onPageChange(Math.max(0, page - 1))}
        >
          上一页
        </button>
        <button
          type="button"
          className="admin-pager__btn"
          disabled={page + 1 >= totalPages || loading}
          onClick={() => onPageChange(page + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  )
}
