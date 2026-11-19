import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { LoginPage, SignupPage } from '../pages/AuthPages'
import { ProblemListPage } from '../pages/ProblemListPage'
import { fakeJwt, mockApi, renderAt } from './helpers'

afterEach(() => vi.unstubAllGlobals())

describe('ProblemListPage', () => {
  it('lists problems as table rows linking to each problem', async () => {
    mockApi([
      {
        path: /\/api\/problems\?/,
        body: {
          content: [
            { id: 1000, title: 'A+B', timeLimitMs: 1000, memoryLimitMb: 128 },
            { id: 1001, title: 'N개의 수의 합', timeLimitMs: 1000, memoryLimitMb: 128 },
          ],
          page: 0,
          size: 50,
          totalElements: 2,
          totalPages: 1,
        },
      },
    ])

    renderAt('/', [{ path: '/', element: <ProblemListPage /> }])

    const link = await screen.findByRole('link', { name: 'A+B' })
    expect(link).toHaveAttribute('href', '/problems/1000')
    expect(screen.getByRole('link', { name: 'N개의 수의 합' })).toBeInTheDocument()
  })
})

describe('LoginPage', () => {
  it('stores the token and returns to the page the user came from', async () => {
    const token = fakeJwt({ sub: '1', nickname: 'tester', exp: Math.floor(Date.now() / 1000) + 3600 })
    mockApi([{ method: 'POST', path: '/api/auth/login', body: { accessToken: token, tokenType: 'Bearer', expiresIn: 3600 } }])

    renderAt('/login?next=/problems/1000', [
      { path: '/login', element: <LoginPage /> },
      { path: '/problems/:id', element: <p>problem page</p> },
    ])
    await userEvent.type(screen.getByLabelText('이메일'), 'me@example.com')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('problem page')).toBeInTheDocument()
    expect(tokenStore.get()).toBe(token)
  })

  it('does not redirect to another site', async () => {
    const token = fakeJwt({ sub: '1', nickname: 'tester', exp: Math.floor(Date.now() / 1000) + 3600 })
    mockApi([{ method: 'POST', path: '/api/auth/login', body: { accessToken: token, tokenType: 'Bearer', expiresIn: 3600 } }])

    renderAt('/login?next=//evil.example.com', [
      { path: '/login', element: <LoginPage /> },
      { path: '/', element: <p>home</p> },
    ])
    await userEvent.type(screen.getByLabelText('이메일'), 'me@example.com')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('home')).toBeInTheDocument()
  })

  it('shows the server message on wrong credentials', async () => {
    mockApi([
      { method: 'POST', path: '/api/auth/login', status: 401, body: { status: 401, detail: '이메일 또는 비밀번호가 올바르지 않습니다' } },
    ])

    renderAt('/login', [{ path: '/login', element: <LoginPage /> }])
    await userEvent.type(screen.getByLabelText('이메일'), 'me@example.com')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다')
    expect(tokenStore.get()).toBeNull()
  })
})

describe('SignupPage', () => {
  it('shows field errors next to each input', async () => {
    mockApi([
      {
        method: 'POST',
        path: '/api/auth/signup',
        status: 400,
        body: { status: 400, detail: 'invalid request', errors: { password: '비밀번호는 8~64자여야 합니다' } },
      },
    ])

    renderAt('/signup', [{ path: '/signup', element: <SignupPage /> }])
    await userEvent.type(screen.getByLabelText('이메일'), 'me@example.com')
    await userEvent.type(screen.getByLabelText('닉네임'), 'tester')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'short')
    await userEvent.click(screen.getByRole('button', { name: '회원가입' }))

    await waitFor(() => expect(screen.getByText('비밀번호는 8~64자여야 합니다')).toBeInTheDocument())
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('aria-invalid', 'true')
  })
})
