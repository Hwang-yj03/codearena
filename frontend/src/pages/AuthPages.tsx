import { useState, type FormEvent, type InputHTMLAttributes } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'name' | 'id'>

function safeNext(next: string | null) {
  // 외부 주소로 튀는 오픈 리다이렉트 방지
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setPending(true)
    setError(null)
    try {
      await login(String(form.get('email')), String(form.get('password')))
      navigate(safeNext(params.get('next')), { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="auth">
      <h1 className="page-title">로그인</h1>
      {params.get('joined') && <p className="notice">가입이 끝났습니다. 로그인하세요.</p>}
      <form onSubmit={onSubmit} className="form" noValidate>
        <div className="field">
          <label htmlFor="login-email">이메일</label>
          <input id="login-email" name="email" type="email" autoComplete="email" required />
        </div>
        <div className="field">
          <label htmlFor="login-password">비밀번호</label>
          <input id="login-password" name="password" type="password" autoComplete="current-password" required />
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button className="btn btn-block" disabled={pending}>
          {pending ? '로그인하는 중' : '로그인'}
        </button>
      </form>
      <p className="muted auth-switch">
        계정이 없다면 <Link to="/signup">회원가입</Link>
      </p>
    </div>
  )
}

type FieldProps = { name: string; label: string; help?: string; error?: string } & InputProps

/**
 * 라벨에는 이름만 두고, 도움말과 에러는 aria-describedby로 연결한다.
 * (컴포넌트를 렌더 함수 안에서 정의하면 매 렌더마다 입력창이 새로 만들어져 값이 사라지므로 바깥에 둔다)
 */
function Field({ name, label, help, error, ...input }: FieldProps) {
  const describedBy = [help && `${name}-help`, error && `${name}-error`].filter(Boolean).join(' ')
  return (
    <div className="field">
      <label htmlFor={`signup-${name}`}>{label}</label>
      <input
        id={`signup-${name}`}
        name={name}
        required
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        {...input}
      />
      {help && (
        <span className="field-help" id={`${name}-help`}>
          {help}
        </span>
      )}
      {error && (
        <span className="field-error" id={`${name}-error`}>
          {error}
        </span>
      )}
    </div>
  )
}

export function SignupPage() {
  const navigate = useNavigate()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setPending(true)
    setError(null)
    setFieldErrors({})
    try {
      await endpoints.signup(String(form.get('email')), String(form.get('password')), String(form.get('nickname')))
      navigate('/login?joined=1', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        if (Object.keys(err.fieldErrors).length === 0) setError(err.message)
      } else {
        setError('회원가입에 실패했습니다')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="auth">
      <h1 className="page-title">회원가입</h1>
      <form onSubmit={onSubmit} className="form" noValidate>
        <Field name="email" label="이메일" error={fieldErrors.email} type="email" autoComplete="email" />
        <Field
          name="nickname"
          label="닉네임"
          help="2~20자. 제출 기록에 표시됩니다."
          error={fieldErrors.nickname}
          autoComplete="nickname"
        />
        <Field
          name="password"
          label="비밀번호"
          help="8자 이상"
          error={fieldErrors.password}
          type="password"
          autoComplete="new-password"
        />
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button className="btn btn-block" disabled={pending}>
          {pending ? '가입하는 중' : '회원가입'}
        </button>
      </form>
      <p className="muted auth-switch">
        이미 계정이 있다면 <Link to="/login">로그인</Link>
      </p>
    </div>
  )
}
