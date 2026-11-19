import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="narrow">
      <h1 className="page-title">페이지를 찾을 수 없습니다</h1>
      <p>
        주소를 다시 확인하거나 <Link to="/">문제 목록</Link>으로 이동하세요.
      </p>
    </div>
  )
}
