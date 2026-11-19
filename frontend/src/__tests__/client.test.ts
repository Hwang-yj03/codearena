import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from '../api/client'
import { tokenStore } from '../auth/tokenStore'
import { mockApi } from './helpers'

afterEach(() => vi.unstubAllGlobals())

describe('api client', () => {
  it('turns Problem Details into ApiError with field errors', async () => {
    mockApi([
      {
        method: 'POST',
        path: '/api/auth/signup',
        status: 400,
        body: { status: 400, detail: 'invalid request', errors: { email: '이메일 형식이 아닙니다' } },
      },
    ])

    const err = (await api('/api/auth/signup', { method: 'POST', body: {} }).catch((e) => e)) as ApiError

    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.fieldErrors.email).toBe('이메일 형식이 아닙니다')
  })

  it('sends the bearer token only when auth is requested', async () => {
    tokenStore.set('abc')
    const fetchMock = mockApi([{ path: '/api/members/me', body: { id: 1 } }, { path: '/api/problems', body: [] }])

    await api('/api/members/me', { auth: true })
    await api('/api/problems')

    const headersOf = (i: number) => (fetchMock.mock.calls[i][1] as RequestInit).headers as Record<string, string>
    expect(headersOf(0).Authorization).toBe('Bearer abc')
    expect(headersOf(1).Authorization).toBeUndefined()
  })

  it('clears the token when an authenticated request gets 401', async () => {
    tokenStore.set('expired')
    mockApi([{ path: '/api/members/me', status: 401 }])

    await expect(api('/api/members/me', { auth: true })).rejects.toBeInstanceOf(ApiError)
    expect(tokenStore.get()).toBeNull()
  })

  it('explains a network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const err = (await api('/api/problems').catch((e) => e)) as ApiError

    expect(err.status).toBe(0)
    expect(err.message).toContain('서버에 연결할 수 없습니다')
  })
})
