import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'

/** base64url 인코딩된 가짜 JWT (서명은 검증하지 않으므로 아무 값) */
export function fakeJwt(payload: Record<string, unknown>) {
  const enc = (o: unknown) => {
    // 실제 JWT처럼 UTF-8 바이트를 base64url로 인코딩한다 (한글 닉네임 대응)
    const bytes = new TextEncoder().encode(JSON.stringify(o))
    return btoa(String.fromCharCode(...bytes)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  }
  return `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc(payload)}.signature`
}

type Route_ = { method?: string; path: string | RegExp; status?: number; body?: unknown }

/** fetch를 가로채 경로별로 정해진 응답을 돌려준다 */
export function mockApi(routes: Route_[]) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const hit = routes.find(
      (r) => (r.method ?? 'GET') === method && (typeof r.path === 'string' ? url === r.path : r.path.test(url)),
    )
    if (!hit) return new Response(JSON.stringify({ status: 404, detail: `no mock for ${method} ${url}` }), { status: 404 })
    return new Response(JSON.stringify(hit.body ?? {}), {
      status: hit.status ?? 200,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

export function renderAt(path: string, routes: { path: string; element: ReactElement }[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            {routes.map((r) => (
              <Route key={r.path} path={r.path} element={r.element} />
            ))}
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}
