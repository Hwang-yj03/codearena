import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'
import { endpoints } from '../api/endpoints'
import type { LanguageKey } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ResultPanel } from '../components/ResultPanel'
import { drafts, LANGUAGES, languageOf } from '../lib/languages'
import { ErrorMessage } from './shared'

// Monaco는 수 MB라 문제 화면에 들어올 때만 받는다
const CodeEditor = lazy(() => import('../components/CodeEditor'))

const LANG_KEY = 'codearena.language'
const POLL_MS = 1000

function initialLanguage(): LanguageKey {
  const saved = localStorage.getItem(LANG_KEY)
  return LANGUAGES.some((l) => l.key === saved) ? (saved as LanguageKey) : 'cpp'
}

export function ProblemPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()

  const problem = useQuery({ queryKey: ['problem', id], queryFn: () => endpoints.problem(id), enabled: Number.isFinite(id) })

  const [language, setLanguage] = useState<LanguageKey>(initialLanguage)
  const [code, setCode] = useState(() => drafts.load(id, language))
  const [submissionId, setSubmissionId] = useState<number | null>(null)

  // 다른 문제로 이동하면 이전 채점 결과를 지운다
  useEffect(() => {
    setSubmissionId(null)
  }, [id])

  useEffect(() => {
    setCode(drafts.load(id, language))
    localStorage.setItem(LANG_KEY, language)
  }, [id, language])

  const onCodeChange = (value: string) => {
    setCode(value)
    drafts.save(id, language, value)
  }

  const submit = useMutation({
    // 제출 시점의 언어·코드를 인자로 넘긴다 (mutationFn이 오래된 값을 잡지 않도록)
    mutationFn: (v: { language: LanguageKey; code: string }) => endpoints.submit(id, v.language, v.code),
    onSuccess: (created) => {
      queryClient.setQueryData(['submission', created.id], created)
      setSubmissionId(created.id)
    },
  })

  // 채점은 비동기다. 결과가 나올 때까지 1초마다 조회하고, DONE이 되면 멈춘다.
  const result = useQuery({
    queryKey: ['submission', submissionId],
    queryFn: () => endpoints.submission(submissionId!),
    enabled: submissionId !== null,
    refetchInterval: (query) => (query.state.data?.status === 'DONE' ? false : POLL_MS),
  })

  const canSubmit = user !== null && code.trim().length > 0 && !submit.isPending && result.data?.status !== 'PENDING'
  const doSubmit = useCallback(() => {
    if (canSubmit) submit.mutate({ language, code })
  }, [canSubmit, submit, language, code])

  if (problem.error) {
    return (
      <div className="narrow">
        <ErrorMessage error={problem.error} />
        <Link to="/">문제 목록으로</Link>
      </div>
    )
  }
  if (!problem.data) return <p className="muted narrow">문제를 불러오는 중</p>
  const p = problem.data

  return (
    <div className="workspace">
      <article className="statement" aria-labelledby="problem-title">
        <header className="statement-head">
          <p className="problem-number mono">{p.id}</p>
          <h1 id="problem-title" className="problem-title">
            {p.title}
          </h1>
          <p className="limits">
            <span>시간 제한 {p.timeLimitMs / 1000}초</span>
            <span>메모리 제한 {p.memoryLimitMb} MB</span>
          </p>
        </header>

        <section>
          <h2>문제</h2>
          <p className="prose">{p.description}</p>
        </section>
        <section>
          <h2>입력</h2>
          <p className="prose">{p.inputDescription}</p>
        </section>
        <section>
          <h2>출력</h2>
          <p className="prose">{p.outputDescription}</p>
        </section>

        {p.samples.map((s) => (
          <section key={s.seq} className="sample">
            <div>
              <h3>예제 입력 {s.seq}</h3>
              <pre className="sample-io">{s.input}</pre>
            </div>
            <div>
              <h3>예제 출력 {s.seq}</h3>
              <pre className="sample-io">{s.output}</pre>
            </div>
          </section>
        ))}
      </article>

      <section className="solver" aria-label="코드 작성">
        <div className="solver-bar">
          <label className="select-label">
            <span className="visually-hidden">언어</span>
            <select value={language} onChange={(e) => setLanguage(e.target.value as LanguageKey)}>
              {LANGUAGES.map((l) => (
                <option key={l.key} value={l.key}>
                  {l.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="btn-text"
            onClick={() => onCodeChange(languageOf(language).template)}
          >
            기본 코드로 되돌리기
          </button>
        </div>

        <div className="editor-frame">
          <Suspense fallback={<div className="editor-loading">에디터를 불러오는 중</div>}>
            <CodeEditor language={languageOf(language).monaco} value={code} onChange={onCodeChange} onSubmitShortcut={doSubmit} />
          </Suspense>
        </div>

        <div className="submit-bar">
          {user ? (
            <>
              <span className="muted shortcut">Ctrl + Enter</span>
              <button type="button" className="btn" onClick={doSubmit} disabled={!canSubmit}>
                {submit.isPending ? '제출하는 중' : '제출'}
              </button>
            </>
          ) : (
            <Link className="btn" to={`/login?next=${encodeURIComponent(location.pathname)}`}>
              로그인하고 제출
            </Link>
          )}
        </div>

        {submit.error && <ErrorMessage error={submit.error} />}
        {result.data && <ResultPanel submission={result.data} />}
      </section>
    </div>
  )
}
