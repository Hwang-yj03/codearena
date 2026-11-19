import type { Submission } from '../api/types'
import { describe } from '../lib/verdict'

/** 표 안에서 쓰는 작은 판정 표시 */
export function VerdictLabel({ submission }: { submission: Pick<Submission, 'status' | 'verdict' | 'passed' | 'total'> }) {
  const info = describe(submission)
  const showProgress = submission.verdict && submission.verdict !== 'AC' && submission.verdict !== 'CE' && submission.total
  return (
    <span className={`verdict verdict-${info.tone}`}>
      {info.tone === 'pending' && <span className="spinner" aria-hidden="true" />}
      {info.label}
      {showProgress ? <span className="verdict-progress"> ({submission.passed}/{submission.total})</span> : null}
    </span>
  )
}
