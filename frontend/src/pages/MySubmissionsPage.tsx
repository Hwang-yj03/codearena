import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { endpoints } from '../api/endpoints'
import { VerdictLabel } from '../components/VerdictLabel'
import { languageOf } from '../lib/languages'
import { memoryOf, timeOf } from '../lib/verdict'
import { ErrorMessage } from './shared'

const dateFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' })

export function MySubmissionsPage() {
  const [page, setPage] = useState(0)
  const { data, error, isPending } = useQuery({
    queryKey: ['me', 'submissions', page],
    queryFn: () => endpoints.mySubmissions(page),
    // 채점 중인 제출이 있으면 목록도 갱신한다
    refetchInterval: (q) => (q.state.data?.content.some((s) => s.status === 'PENDING') ? 2000 : false),
  })

  return (
    <div className="narrow wide">
      <h1 className="page-title">내 제출</h1>
      {error && <ErrorMessage error={error} />}
      {isPending && <p className="muted">제출 기록을 불러오는 중</p>}

      {data && data.content.length === 0 && (
        <p className="empty">
          아직 제출한 코드가 없습니다. <Link to="/">문제 목록</Link>에서 첫 문제를 골라보세요.
        </p>
      )}

      {data && data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th scope="col" className="col-num">제출</th>
                <th scope="col" className="col-num">문제</th>
                <th scope="col">결과</th>
                <th scope="col" className="col-limit">시간</th>
                <th scope="col" className="col-limit">메모리</th>
                <th scope="col" className="col-limit">언어</th>
                <th scope="col" className="col-date">제출한 시각</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((s) => (
                <tr key={s.id}>
                  <td className="col-num mono">{s.id}</td>
                  <td className="col-num mono">
                    <Link to={`/problems/${s.problemId}`}>{s.problemId}</Link>
                  </td>
                  <td>
                    <VerdictLabel submission={s} />
                  </td>
                  <td className="col-limit mono">{timeOf(s)}</td>
                  <td className="col-limit mono">{memoryOf(s)}</td>
                  <td className="col-limit">{languageOf(s.language).label}</td>
                  <td className="col-date muted">{dateFormat.format(new Date(s.createdAt))}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalPages > 1 && (
            <nav className="pager" aria-label="페이지">
              <button type="button" className="btn-text" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                이전
              </button>
              <span className="mono">
                {page + 1} / {data.totalPages}
              </span>
              <button type="button" className="btn-text" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                다음
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
