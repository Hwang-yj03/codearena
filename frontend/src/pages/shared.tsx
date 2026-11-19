import { ApiError } from '../api/client'

export function ErrorMessage({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : '알 수 없는 오류가 발생했습니다'
  return (
    <p className="error" role="alert">
      {message}
    </p>
  )
}
