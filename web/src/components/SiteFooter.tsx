import { Link } from 'react-router'
import { CONTACT_EMAIL } from '../legal.ts'

const GITHUB_URL = 'https://github.com/wiwi-git/web-broadcast-proj'

/** 모든 화면 아래쪽. 이용약관·개인정보처리방침·문의 연락처를 언제든 열 수 있게 둔다 (스토어 심사 요건) */
export default function SiteFooter() {
  return (
    <footer className="site-footer">
      <span>© Web Broadcast</span>
      <Link to="/terms">이용약관</Link>
      {/* 개인정보처리방침은 눈에 띄게 둔다 (관례) */}
      <Link to="/privacy" className="footer-strong">
        개인정보처리방침
      </Link>
      <a href={`mailto:${CONTACT_EMAIL}`}>문의 {CONTACT_EMAIL}</a>
      <a href={GITHUB_URL} target="_blank" rel="noreferrer">
        GitHub 저장소
      </a>
    </footer>
  )
}
