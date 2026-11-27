import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchHidden, REPORT_REASONS, type HiddenItem, type HiddenPage, type ReportTargetType } from '../api.ts'
import AdminTabs from '../components/AdminTabs.tsx'
import HlsPlayer from '../components/HlsPlayer.tsx'
import { formatDate } from '../format.ts'

const TYPES: { value: ReportTargetType; label: string }[] = [
  { value: 'VIDEO', label: '동영상' },
  { value: 'BROADCAST', label: '방송' },
  { value: 'COMMENT', label: '댓글' },
]

const REASON_LABEL = Object.fromEntries(REPORT_REASONS.map((r) => [r.value, r.label]))

const STATUS_LABEL: Record<string, string> = {
  PROCESSING: '변환 중',
  PUBLISHED: '변환 완료',
  FAILED: '변환 실패',
  PENDING: '준비',
  LIVE: '방송 중',
  ENDED: '종료',
}

/**
 * 운영자가 내린 동영상·방송·댓글. 누구에게도 보이지 않지만 기록과 파일은 남아 있어서
 * 운영자는 여기서 내용을 확인하고 동영상을 재생할 수 있다. 종류와 쪽은 주소(?type=&page=)에 담는다.
 */
export default function AdminHiddenPage() {
  const [params, setParams] = useSearchParams()
  const type = (TYPES.find((t) => t.value === params.get('type'))?.value ?? 'VIDEO') as ReportTargetType
  const page = Math.max(Number(params.get('page')) || 0, 0)
  const [result, setResult] = useState<HiddenPage | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchHidden(type, page)
      .then((found) => {
        if (cancelled) return
        setError(null)
        setResult(found)
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [type, page])

  function go(next: { type?: ReportTargetType; page?: number }) {
    const search = new URLSearchParams({ type: next.type ?? type })
    if ((next.page ?? 0) > 0) search.set('page', String(next.page))
    setParams(search)
  }

  // 종류를 바꾼 직후에는 이전 종류의 결과를 보이지 않는다
  const shown = result && result.items.every((item) => item.type === type) ? result : null

  return (
    <div className="admin-page">
      <AdminTabs />
      <div className="admin-filters" role="radiogroup" aria-label="종류">
        {TYPES.map((t) => (
          <button
            key={t.value}
            type="button"
            role="radio"
            aria-checked={type === t.value}
            className={type === t.value ? 'button small primary' : 'button small'}
            onClick={() => go({ type: t.value })}
          >
            {t.label}
          </button>
        ))}
        {shown && <span className="meta">{shown.totalItems}개</span>}
      </div>

      {error && <p className="notice error">{error}</p>}
      {!error && !shown && <p className="notice">불러오는 중…</p>}
      {shown && shown.items.length === 0 && <p className="notice">내린 {labelOf(type)}이 없습니다.</p>}
      {shown && shown.items.length > 0 && (
        <ul className="report-groups">
          {shown.items.map((item) => (
            <HiddenCard key={item.id} item={item} />
          ))}
        </ul>
      )}

      {shown && shown.totalPages > 1 && (
        <nav className="pagination">
          <button className="button" disabled={page === 0} onClick={() => go({ page: page - 1 })}>
            이전
          </button>
          <span>
            {page + 1} / {shown.totalPages}
          </span>
          <button className="button" disabled={page + 1 >= shown.totalPages} onClick={() => go({ page: page + 1 })}>
            다음
          </button>
        </nav>
      )}
    </div>
  )
}

function HiddenCard({ item }: { item: HiddenItem }) {
  const [playing, setPlaying] = useState(false)
  const reasons = Object.entries(item.reasons)
    .map(([reason, count]) => `${REASON_LABEL[reason]} ${count}`)
    .join(' · ')

  return (
    <li className="report-group">
      <div className="report-group-head">
        <span className="badge">{labelOf(item.type)}</span>
        <strong>{formatDate(item.hiddenAt)} 내림</strong>
        {item.status && <span className="meta">{STATUS_LABEL[item.status] ?? item.status}</span>}
      </div>

      {item.type === 'VIDEO' &&
        (playing && item.playbackUrl ? (
          <div className="hidden-player">
            <HlsPlayer src={item.playbackUrl} poster={item.thumbnailUrl ?? undefined} autoPlay />
          </div>
        ) : (
          item.thumbnailUrl && <img className="hidden-thumbnail" src={item.thumbnailUrl} alt="" />
        ))}

      <p className="report-summary">{item.summary}</p>
      <p className="meta">
        {item.type === 'COMMENT' ? '작성자' : '올린 사람'} {item.owner.nickname}
        {item.owner.suspended && <span className="badge danger inline">정지됨</span>}
        {' · '}
        {formatDate(item.createdAt)} {item.type === 'COMMENT' ? '작성' : '만듦'}
        {item.type === 'COMMENT' && (
          <>
            {' · '}
            {/* 달린 곳도 내려졌으면 그 화면은 404가 된다 */}
            <Link to={item.parentType === 'VIDEO' ? `/videos/${item.parentId}` : `/live/${item.parentId}`}>
              달린 {labelOf(item.parentType)} 보기
            </Link>
          </>
        )}
      </p>

      {item.reportCount > 0 ? (
        <details className="hidden-reports">
          <summary className="meta">
            내리게 한 신고 {item.reportCount}건 · {reasons}
          </summary>
          <ul className="report-items">
            {item.reports.map((r, i) => (
              <li key={i}>
                <span className="comment-author">{r.reporterNickname}</span>{' '}
                <span className="meta">
                  {REASON_LABEL[r.reason]} · {formatDate(r.createdAt)}
                </span>
                {r.detail && <p className="report-detail">{r.detail}</p>}
              </li>
            ))}
          </ul>
        </details>
      ) : (
        <p className="meta">남아 있는 신고 기록이 없습니다.</p>
      )}

      {item.type === 'VIDEO' && (
        <div className="report-group-actions">
          {item.playbackUrl ? (
            <button type="button" className="button small" onClick={() => setPlaying((p) => !p)}>
              {playing ? '재생 닫기' : '영상 재생'}
            </button>
          ) : (
            <span className="meta">변환이 끝나지 않아 재생할 수 없습니다.</span>
          )}
        </div>
      )}
    </li>
  )
}

function labelOf(type: ReportTargetType): string {
  return TYPES.find((t) => t.value === type)?.label ?? type
}
