import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
  dismissReports,
  fetchOpenReports,
  hideReported,
  REPORT_REASONS,
  suspendUser,
  unsuspendUser,
  type ReportGroup,
  type ReportTargetType,
} from '../api.ts'
import ConfirmButton from '../components/ConfirmButton.tsx'
import { formatRelative } from '../format.ts'

const TARGET_LABEL: Record<ReportTargetType, string> = {
  BROADCAST: '방송',
  VIDEO: '동영상',
  COMMENT: '댓글',
}

const REASON_LABEL = Object.fromEntries(REPORT_REASONS.map((r) => [r.value, r.label]))

/**
 * 운영자 화면. 처리 전 신고를 대상별로 묶어 오래된 순으로 보여 준다.
 * 대상을 내리거나(숨김) 문제없음으로 닫고, 작성자를 정지할 수 있다. 서버가 운영자인지 확인한다.
 */
export default function AdminReportsPage() {
  const [groups, setGroups] = useState<ReportGroup[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchOpenReports()
      .then(setGroups)
      .catch((e: Error) => setError(e.message))
  }, [])

  function removeGroup(group: ReportGroup) {
    setGroups((current) => current?.filter((g) => g !== group) ?? null)
  }

  /** 같은 사람이 여러 대상에 걸려 있을 수 있으므로 모든 묶음의 정지 표시를 바꾼다 */
  function markSuspended(userId: string, suspended: boolean) {
    setGroups(
      (current) =>
        current?.map((g) => (g.owner?.id === userId ? { ...g, owner: { ...g.owner, suspended } } : g)) ?? null,
    )
  }

  if (error) return <p className="notice error">{error}</p>
  if (!groups) return <p className="notice">불러오는 중…</p>

  return (
    <div className="admin-page">
      <h1 className="page-title">신고 관리</h1>
      {groups.length === 0 ? (
        <p className="notice">처리할 신고가 없습니다.</p>
      ) : (
        <ul className="report-groups">
          {groups.map((group) => (
            <ReportGroupCard
              key={`${group.targetType}/${group.targetId}`}
              group={group}
              onClosed={() => removeGroup(group)}
              onSuspendChanged={markSuspended}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

interface CardProps {
  group: ReportGroup
  onClosed(): void
  onSuspendChanged(userId: string, suspended: boolean): void
}

function ReportGroupCard({ group, onClosed, onSuspendChanged }: CardProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const gone = group.summary === null
  const link = group.parentId && !group.hidden ? contentPath(group.parentType, group.parentId) : null

  async function run(action: () => Promise<void>, after: () => void) {
    setBusy(true)
    setError(null)
    try {
      await action()
      after()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const reasons = Object.entries(group.reasons)
    .map(([reason, count]) => `${REASON_LABEL[reason]} ${count}`)
    .join(' · ')

  return (
    <li className="report-group">
      <div className="report-group-head">
        <span className="badge">{TARGET_LABEL[group.targetType]}</span>
        <strong>신고 {group.reportCount}건</strong>
        <span className="meta">{reasons}</span>
      </div>

      {gone ? (
        <p className="meta">작성자가 이미 지운 {TARGET_LABEL[group.targetType]}입니다.</p>
      ) : (
        <>
          <p className="report-summary">{group.summary}</p>
          <p className="meta">
            {group.targetType === 'COMMENT' ? '작성자' : '올린 사람'} {group.owner?.nickname}
            {group.owner?.suspended && <span className="badge danger inline">정지됨</span>}
            {group.hidden && <span className="badge inline">숨김</span>}
            {link && (
              <>
                {' · '}
                <Link to={link}>{group.targetType === 'COMMENT' ? '댓글이 달린 곳 보기' : '보기'}</Link>
              </>
            )}
          </p>
        </>
      )}

      <ul className="report-items">
        {group.reports.map((r, i) => (
          <li key={i}>
            <span className="comment-author">{r.reporterNickname}</span>{' '}
            <span className="meta">
              {REASON_LABEL[r.reason]} · {formatRelative(r.createdAt)}
            </span>
            {r.detail && <p className="report-detail">{r.detail}</p>}
          </li>
        ))}
      </ul>

      {error && <p className="hint error">{error}</p>}
      <div className="report-group-actions">
        {!gone && !group.hidden && (
          <ConfirmButton
            className="button small danger"
            disabled={busy}
            // 방송·동영상·댓글 모두 받침으로 끝나 '을'을 쓴다
            message={`이 ${TARGET_LABEL[group.targetType]}을 내릴까요? 누구에게도 보이지 않게 됩니다.`}
            confirmLabel="내리기"
            onConfirm={() => run(() => hideReported(group.targetType, group.targetId), onClosed)}
          >
            내리기
          </ConfirmButton>
        )}
        <button
          type="button"
          className="button small"
          disabled={busy}
          onClick={() => run(() => dismissReports(group.targetType, group.targetId), onClosed)}
        >
          문제없음
        </button>
        {group.owner &&
          (group.owner.suspended ? (
            <button
              type="button"
              className="button small"
              disabled={busy}
              onClick={() => {
                const id = group.owner!.id
                run(() => unsuspendUser(id), () => onSuspendChanged(id, false))
              }}
            >
              정지 해제
            </button>
          ) : (
            <ConfirmButton
              className="button small danger"
              disabled={busy}
              message={`${group.owner.nickname}님을 정지할까요? 바로 로그아웃되고 진행 중인 방송이 끝납니다.`}
              confirmLabel="정지"
              onConfirm={() => {
                const id = group.owner!.id
                run(() => suspendUser(id), () => onSuspendChanged(id, true))
              }}
            >
              작성자 정지
            </ConfirmButton>
          ))}
      </div>
    </li>
  )
}

function contentPath(type: ReportGroup['parentType'], id: string): string | null {
  if (type === 'BROADCAST') return `/live/${id}`
  if (type === 'VIDEO') return `/videos/${id}`
  return null
}
