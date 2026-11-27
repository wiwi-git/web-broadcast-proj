import { NavLink } from 'react-router'

/** 운영자 화면 위쪽의 탭. 신고 관리, 내린 콘텐츠, 회원 관리를 오간다 */
export default function AdminTabs() {
  return (
    <>
      <h1 className="page-title">운영자</h1>
      <nav className="tabs" aria-label="운영자 메뉴">
        <NavLink to="/admin/reports" className={({ isActive }) => (isActive ? 'tab active' : 'tab')}>
          신고 관리
        </NavLink>
        <NavLink to="/admin/hidden" className={({ isActive }) => (isActive ? 'tab active' : 'tab')}>
          내린 콘텐츠
        </NavLink>
        <NavLink to="/admin/users" className={({ isActive }) => (isActive ? 'tab active' : 'tab')}>
          회원 관리
        </NavLink>
      </nav>
    </>
  )
}
