import type { Submission } from '../api/types'
import { describe, formatMemory, formatTime } from '../lib/verdict'

/** 제출 직후 보여주는 채점 결과. 이 화면에서 가장 크게 보여야 하는 정보다. */
export function ResultPanel({ submission }: { submission: Submission }) {
  const info = describe(submission)
  const done = submission.status === 'DONE'
  const total = submission.total ?? 0
  const passed = submission.passed ?? 0
  const ratio = total > 0 ? passed / total : 0

  return (
    <section className={`result result-${info.tone}`} aria-live="polite" aria-label="채점 결과">
      <div className="result-head">
        <p className="result-verdict">
          {!done && <span className="spinner spinner-large" aria-hidden="true" />}
          {info.label}
        </p>
        <p className="result-id">제출 #{submission.id}</p>
      </div>

      {done && submission.verdict !== 'CE' && total > 0 && (
        <>
          <div
            className="cases"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={passed}
            aria-label="통과한 테스트케이스"
          >
            <span className="cases-fill" style={{ ['--ratio' as string]: ratio }} />
          </div>
          <dl className="result-stats">
            <div>
              <dt>테스트케이스</dt>
              <dd>
                {passed} / {total}
              </dd>
            </div>
            <div>
              <dt>실행 시간</dt>
              <dd>{formatTime(submission.timeMs)}</dd>
            </div>
            <div>
              <dt>메모리</dt>
              <dd>{formatMemory(submission.memoryKb)}</dd>
            </div>
          </dl>
        </>
      )}

      {done && info.hint && <p className="result-hint">{info.hint}</p>}
      {done && submission.compileLog && <pre className="compile-log">{submission.compileLog}</pre>}
    </section>
  )
}
