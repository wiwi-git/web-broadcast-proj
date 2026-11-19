import { useEffect, useState } from 'react'
import { deleteYouTubeKey, fetchSimulcast, saveYouTubeKey, type SimulcastSetting } from '../api.ts'
import ConfirmButton from './ConfirmButton.tsx'

/**
 * 방송을 시작하기 전에 고르는 유튜브 동시 송출 (ADR-0007).
 * YouTube Key가 없으면 등록 칸을, 있으면 끝 네 자리와 "유튜브에도 송출" 체크를 보여 준다.
 * 체크의 처음 값은 지난번 방송에서 고른 값이다. 서버에 동시 송출 설정이 없으면 아무것도 보이지 않는다.
 */
export default function SimulcastPicker({
  on,
  onChange,
  disabled,
}: {
  on: boolean
  onChange: (on: boolean) => void
  disabled: boolean
}) {
  const [setting, setSetting] = useState<SimulcastSetting | null>(null)
  const [editing, setEditing] = useState(false)
  const [key, setKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchSimulcast()
      .then((loaded) => {
        if (cancelled) return
        setSetting(loaded)
        onChange(loaded.youtubeKeyLast4 !== null && loaded.onByDefault)
      })
      .catch(() => {
        // 불러오지 못하면 동시 송출 없이 방송한다
      })
    return () => {
      cancelled = true
    }
    // 처음 한 번만 불러온다
  }, [])

  if (!setting?.available) return null
  const hasKey = setting.youtubeKeyLast4 !== null

  /** 바깥의 방송 시작 폼이 Enter로 제출되지 않게 막는다 */
  async function handleSave(event: { preventDefault(): void }) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const saved = await saveYouTubeKey(key.trim())
      setSetting(saved)
      setKey('')
      setEditing(false)
      onChange(true)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    try {
      await deleteYouTubeKey()
      setSetting({ ...setting!, youtubeKeyLast4: null, onByDefault: false })
      onChange(false)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  if (!hasKey || editing) {
    return (
      // 방송 제목 폼 안에 있으므로 form을 겹치지 않고 버튼으로 저장한다
      <div className="simulcast">
        <p className="simulcast-title">유튜브에도 송출</p>
        <input
          type="password"
          className="text-input"
          autoComplete="off"
          value={key}
          disabled={disabled || saving}
          placeholder="유튜브 스트림 키"
          onChange={(e) => setKey(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSave(e)
          }}
        />
        <p className="hint">유튜브 스튜디오 → 만들기 → 라이브 스트리밍 시작 → 스트림 설정의 "스트림 키"를 복사해 붙여넣으세요.</p>
        {error && <p className="hint error">{error}</p>}
        <div className="simulcast-actions">
          <button type="button" className="button" disabled={disabled || saving || !key.trim()} onClick={handleSave}>
            {saving ? '저장 중…' : '키 저장'}
          </button>
          {editing && (
            <button type="button" className="button" disabled={saving} onClick={() => setEditing(false)}>
              취소
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="simulcast">
      <label className="simulcast-check">
        <input type="checkbox" checked={on} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
        <span>유튜브에도 송출</span>
        <span className="hint">키 ····{setting.youtubeKeyLast4}</span>
      </label>
      {error && <p className="hint error">{error}</p>}
      <div className="simulcast-actions">
        <button type="button" className="button" disabled={disabled} onClick={() => setEditing(true)}>
          키 바꾸기
        </button>
        <ConfirmButton disabled={disabled} message="유튜브 키를 지울까요?" confirmLabel="지우기" onConfirm={handleDelete}>
          키 지우기
        </ConfirmButton>
      </div>
    </div>
  )
}

const STATUS_TEXT = { CONNECTING: '연결 중…', LIVE: '송출 중', FAILED: '연결 실패 (방송은 계속됩니다)' } as const

/** 방송 중 Broadcaster에게만 보이는 유튜브 상태 */
export function SimulcastStatusLine({ status }: { status: keyof typeof STATUS_TEXT }) {
  return (
    <p className={status === 'FAILED' ? 'hint error' : 'hint'}>
      유튜브: {STATUS_TEXT[status]}
    </p>
  )
}
