const KEY = 'codearena.accessToken'

type Listener = () => void
const listeners = new Set<Listener>()

/**
 * Access Token 저장소. localStorage는 XSS에 노출될 수 있다는 단점이 있어
 * 토큰 수명을 1시간으로 짧게 두었다 (docs/06-frontend.md 참고).
 */
export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(KEY)
    } catch {
      return null
    }
  },
  set(token: string) {
    try {
      localStorage.setItem(KEY, token)
    } catch {
      /* 시크릿 모드 등에서 저장 실패해도 동작은 계속한다 */
    }
    listeners.forEach((l) => l())
  },
  clear() {
    try {
      localStorage.removeItem(KEY)
    } catch {
      /* noop */
    }
    listeners.forEach((l) => l())
  },
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

export type TokenClaims = {
  memberId: number
  nickname: string
  expiresAt: number // epoch ms
}

/** 서명 검증은 서버가 한다. 클라이언트는 화면 표시와 만료 확인에만 쓴다. */
export function readClaims(token: string | null): TokenClaims | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)))
    const payload = JSON.parse(json)
    const memberId = Number(payload.sub)
    if (!Number.isFinite(memberId) || typeof payload.exp !== 'number') return null
    return { memberId, nickname: String(payload.nickname ?? ''), expiresAt: payload.exp * 1000 }
  } catch {
    return null
  }
}
