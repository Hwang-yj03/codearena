import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { endpoints } from '../api/endpoints'
import { ErrorMessage } from './shared'

export function ProblemListPage() {
  const { data, isPending, error } = useQuery({ queryKey: ['problems'], queryFn: () => endpoints.problems() })

  return (
    <div className="narrow">
      <h1 className="page-title">문제</h1>
      <p className="page-lede">코드를 제출하면 격리된 환경에서 실행해 바로 채점합니다. C++17, Python 3, Java 21을 지원합니다.</p>

      {error && <ErrorMessage error={error} />}
      {isPending && <p className="muted">문제 목록을 불러오는 중</p>}

      {data && (
        <table className="table">
          <thead>
            <tr>
              <th scope="col" className="col-num">번호</th>
              <th scope="col">제목</th>
              <th scope="col" className="col-limit">시간 제한</th>
              <th scope="col" className="col-limit">메모리 제한</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((p) => (
              <tr key={p.id}>
                <td className="col-num mono">{p.id}</td>
                <td>
                  <Link to={`/problems/${p.id}`} className="row-link">
                    {p.title}
                  </Link>
                </td>
                <td className="col-limit mono">{p.timeLimitMs / 1000}초</td>
                <td className="col-limit mono">{p.memoryLimitMb} MB</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && data.content.length === 0 && <p className="muted">등록된 문제가 아직 없습니다.</p>}
    </div>
  )
}
