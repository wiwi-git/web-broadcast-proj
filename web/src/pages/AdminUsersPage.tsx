import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { fetchAdminUsers, suspendUser, unsuspendUser, type AdminUser, type AdminUserPage } from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'
import AdminTabs from '../components/AdminTabs.tsx'
import ConfirmButton from '../components/ConfirmButton.tsx'
import { formatDate } from '../format.ts'

type StatusFilter = 'all' | 'suspended' | 'active'

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'active', label: '이용 중' },
  { value: 'suspended', label: '정지됨' },
]

/**
 * 운영자 회원 목록. 이메일이나 닉네임으로 찾고 상태로 거를 수 있으며, 회원을 정지하거나 정지를 푼다.
 * 검색어·상태·쪽은 주소(?q=&status=&page=)에 담아 새로고침과 뒤로 가기에도 유지한다.
 */
export default function AdminUsersPage() {
  const { me } = useAuth()
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const status = (params.get('status') as StatusFilter | null) ?? 'all'
  const page = Math.max(Number(params.get('page')) || 0, 0)

  const [draft, setDraft] = useState(query)
  const [result, setResult] = useState<AdminUserPage | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchAdminUsers({ query, suspended: status === 'all' ? undefined : status === 'suspended', page })
      .then((found) => {
        if (cancelled) return
        setError(null)
        setResult(found)
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [query, status, page])

  function update(next: { q?: string; status?: StatusFilter; page?: number }) {
    const merged = { q: next.q ?? query, status: next.status ?? status, page: next.page ?? 0 }
    const search = new URLSearchParams()
    if (merged.q) search.set('q', merged.q)
    if (merged.status !== 'all') search.set('status', merged.status)
    if (merged.page > 0) search.set('page', String(merged.page))
    setParams(search)
  }

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    update({ q: draft.trim() })
  }

  function replaceUser(user: AdminUser) {
    setResult((current) =>
      current ? { ...current, items: current.items.map((u) => (u.id === user.id ? user : u)) } : current,
    )
  }

  return (
    <div className="admin-page">
      <AdminTabs />
      <form className="admin-search" onSubmit={handleSearch} role="search">
        <input
          className="text-input"
          type="search"
          placeholder="이메일이나 닉네임"
          aria-label="회원 검색"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <button type="submit" className="button">
          검색
        </button>
      </form>
      <div className="admin-filters" role="radiogroup" aria-label="상태">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            role="radio"
            aria-checked={status === f.value}
            className={status === f.value ? 'button small primary' : 'button small'}
            onClick={() => update({ status: f.value })}
          >
            {f.label}
          </button>
        ))}
        {result && <span className="meta">{result.totalItems}명</span>}
      </div>

      {error && <p className="notice error">{error}</p>}
      {!error && !result && <p className="notice">불러오는 중…</p>}
      {result && result.items.length === 0 && <p className="notice">조건에 맞는 회원이 없습니다.</p>}
      {result && result.items.length > 0 && (
        <ul className="admin-users">
          {result.items.map((user) => (
            <UserRow key={user.id} user={user} isMe={user.id === me?.id} onChanged={replaceUser} />
          ))}
        </ul>
      )}

      {result && result.totalPages > 1 && (
        <nav className="pagination">
          <button className="button" disabled={page === 0} onClick={() => update({ page: page - 1 })}>
            이전
          </button>
          <span>
            {page + 1} / {result.totalPages}
          </span>
          <button
            className="button"
            disabled={page + 1 >= result.totalPages}
            onClick={() => update({ page: page + 1 })}
          >
            다음
          </button>
        </nav>
      )}
    </div>
  )
}

interface RowProps {
  user: AdminUser
  isMe: boolean
  onChanged(user: AdminUser): void
}

/** 운영자는 정지할 수 없으므로(서버도 거절한다) 버튼을 보이지 않는다 */
function UserRow({ user, isMe, onChanged }: RowProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function change(suspend: boolean) {
    setBusy(true)
    setError(null)
    try {
      await (suspend ? suspendUser(user.id) : unsuspendUser(user.id))
      onChanged({ ...user, suspended: suspend, suspendedAt: suspend ? new Date().toISOString() : null })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="admin-user">
      <div className="admin-user-info">
        <span className="comment-author">{user.nickname}</span>
        <span className="meta">{user.email}</span>
        {user.admin && <span className="badge inline">운영자</span>}
        {isMe && <span className="badge inline">나</span>}
        {user.suspended && <span className="badge danger inline">정지됨</span>}
        <p className="meta admin-user-dates">
          {formatDate(user.createdAt)} 가입
          {user.suspendedAt && ` · ${formatDate(user.suspendedAt)} 정지`}
        </p>
        {error && <p className="hint error">{error}</p>}
      </div>
      {!user.admin && (
        <div className="admin-user-actions">
          {user.suspended ? (
            <ConfirmButton
              className="button small"
              disabled={busy}
              message={`${user.nickname}님의 정지를 풀까요?`}
              confirmLabel="해제"
              onConfirm={() => change(false)}
            >
              정지 해제
            </ConfirmButton>
          ) : (
            <ConfirmButton
              className="button small danger"
              disabled={busy}
              message={`${user.nickname}님을 정지할까요? 바로 로그아웃되고 진행 중인 방송이 끝납니다.`}
              confirmLabel="정지"
              onConfirm={() => change(true)}
            >
              정지
            </ConfirmButton>
          )}
        </div>
      )}
    </li>
  )
}
