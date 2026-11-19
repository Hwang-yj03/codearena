import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Submission } from '../api/types'
import { ResultPanel } from '../components/ResultPanel'

const base: Submission = {
  id: 12,
  memberId: 1,
  problemId: 1000,
  language: 'cpp',
  status: 'DONE',
  verdict: 'AC',
  timeMs: 4,
  memoryKb: 3584,
  passed: 3,
  total: 3,
  compileLog: null,
  createdAt: '2026-11-19T15:00:00',
  judgedAt: '2026-11-19T15:00:01',
}

describe('ResultPanel', () => {
  it('shows pending state while judging', () => {
    render(<ResultPanel submission={{ ...base, status: 'PENDING', verdict: null }} />)
    expect(screen.getByText('채점 중')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('shows accepted with stats', () => {
    render(<ResultPanel submission={base} />)
    expect(screen.getByText('맞았습니다')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '3')
    expect(screen.getByText('3.5 MB')).toBeInTheDocument()
  })

  it('shows how many testcases passed on wrong answer', () => {
    render(<ResultPanel submission={{ ...base, verdict: 'WA', passed: 2 }} />)
    expect(screen.getByText('틀렸습니다')).toBeInTheDocument()
    expect(screen.getByText('2 / 3')).toBeInTheDocument()
  })

  it('shows compiler output on compile error', () => {
    render(<ResultPanel submission={{ ...base, verdict: 'CE', passed: 0, compileLog: "main.cpp:1: error: 'x' was not declared" }} />)
    expect(screen.getByText('컴파일 에러')).toBeInTheDocument()
    expect(screen.getByText(/was not declared/)).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})
