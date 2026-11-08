import { useState, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** 두 번째 단계에서 보여 줄 질문 */
  message: string
  /** 실행 버튼 문구 */
  confirmLabel: string
  onConfirm(): void
  className?: string
  disabled?: boolean
}

/**
 * 되돌릴 수 없는 동작(종료·삭제)을 한 번 더 확인받는 버튼.
 * window.confirm은 앱 안 브라우저(웹뷰)나 일부 환경에서 창 없이 바로 취소되므로 화면 안에서 묻는다.
 */
export default function ConfirmButton({
  children,
  message,
  confirmLabel,
  onConfirm,
  className = 'button',
  disabled,
}: Props) {
  const [asking, setAsking] = useState(false)

  if (!asking) {
    return (
      <button type="button" className={className} disabled={disabled} onClick={() => setAsking(true)}>
        {children}
      </button>
    )
  }
  return (
    <span className="confirm-inline" role="group" aria-label={message}>
      <span className="confirm-message">{message}</span>
      <button
        type="button"
        className="button small danger"
        disabled={disabled}
        onClick={() => {
          setAsking(false)
          onConfirm()
        }}
      >
        {confirmLabel}
      </button>
      <button type="button" className="button small" onClick={() => setAsking(false)} autoFocus>
        취소
      </button>
    </span>
  )
}
