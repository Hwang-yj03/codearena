import { tokenStore } from '../auth/tokenStore'

/** 서버의 RFC 9457 Problem Details 에러를 담는다 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST'
  body?: unknown
  /** true면 Authorization 헤더를 붙인다 */
  auth?: boolean
}

const FALLBACK_MESSAGE: Record<number, string> = {
  401: '로그인이 필요합니다',
  403: '권한이 없습니다',
  404: '찾을 수 없습니다',
  500: '서버에서 문제가 생겼습니다. 잠시 후 다시 시도하세요',
}

export async function api<T>(path: string, { method = 'GET', body, auth = false }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let res: Response
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없습니다. API 서버가 켜져 있는지 확인하세요')
  }

  if (res.status === 401 && auth) {
    // 토큰 만료 또는 위조: 저장된 토큰을 지워 로그아웃 상태로 돌린다
    tokenStore.clear()
  }

  if (!res.ok) {
    const problem = await res.json().catch(() => null)
    const message = problem?.detail ?? FALLBACK_MESSAGE[res.status] ?? `요청에 실패했습니다 (${res.status})`
    throw new ApiError(res.status, message, problem?.errors ?? {})
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
