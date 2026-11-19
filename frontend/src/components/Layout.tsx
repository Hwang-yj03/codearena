import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="wordmark" aria-label="CodeArena 홈">
          <span className="wordmark-bracket">&lt;</span>CodeArena<span className="wordmark-bracket">/&gt;</span>
        </Link>
        <nav className="topnav" aria-label="주 메뉴">
          <NavLink to="/" end>문제</NavLink>
          {user && <NavLink to="/me/submissions">내 제출</NavLink>}
        </nav>
        <div className="account">
          {user ? (
            <>
              <span className="account-name">{user.nickname}</span>
              <button
                type="button"
                className="btn-text"
                onClick={() => {
                  logout()
                  navigate('/')
                }}
              >
                로그아웃
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">로그인</NavLink>
              <Link to="/signup" className="btn btn-small">회원가입</Link>
            </>
          )}
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}
