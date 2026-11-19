import { createContext, useCallback, useContext, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import { readClaims, tokenStore, type TokenClaims } from './tokenStore'

type AuthState = {
  user: TokenClaims | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const token = useSyncExternalStore(tokenStore.subscribe, tokenStore.get, () => null)
  const claims = useMemo(() => readClaims(token), [token])
  const expired = claims !== null && claims.expiresAt <= Date.now()
  const user = expired ? null : claims
  const queryClient = useQueryClient()

  useEffect(() => {
    if (token && !user) tokenStore.clear() // 형식이 깨졌거나 만료된 토큰 정리
  }, [token, user])

  // 만료 시각이 되면 자동 로그아웃
  useEffect(() => {
    if (!user) return
    const timer = window.setTimeout(() => tokenStore.clear(), user.expiresAt - Date.now())
    return () => window.clearTimeout(timer)
  }, [user])

  const login = useCallback(async (email: string, password: string) => {
    const res = await endpoints.login(email, password)
    tokenStore.set(res.accessToken)
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    queryClient.removeQueries({ queryKey: ['me'] })
  }, [queryClient])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
