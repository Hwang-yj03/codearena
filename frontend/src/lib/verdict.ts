import type { Submission, Verdict } from '../api/types'

export type VerdictTone = 'accepted' | 'rejected' | 'limit' | 'error' | 'pending'

type VerdictInfo = { label: string; tone: VerdictTone; hint?: string }

export const VERDICTS: Record<Verdict, VerdictInfo> = {
  AC: { label: '맞았습니다', tone: 'accepted' },
  WA: { label: '틀렸습니다', tone: 'rejected', hint: '출력이 정답과 다른 테스트케이스가 있습니다.' },
  TLE: { label: '시간 초과', tone: 'limit', hint: '시간 제한 안에 끝나지 않았습니다. 더 빠른 알고리즘이 필요합니다.' },
  MLE: { label: '메모리 초과', tone: 'limit', hint: '메모리 제한보다 많이 사용했습니다.' },
  RE: { label: '런타임 에러', tone: 'error', hint: '실행 중 프로그램이 비정상 종료했습니다. 배열 범위나 0으로 나누기를 확인하세요.' },
  CE: { label: '컴파일 에러', tone: 'error', hint: '아래 컴파일 메시지를 확인하세요.' },
  SE: { label: '채점 오류', tone: 'error', hint: '채점 시스템에 문제가 생겼습니다. 잠시 후 다시 제출하세요.' },
}

export function describe(s: Pick<Submission, 'status' | 'verdict'>): VerdictInfo {
  if (s.status === 'PENDING' || !s.verdict) return { label: '채점 중', tone: 'pending' }
  return VERDICTS[s.verdict] ?? { label: s.verdict, tone: 'error' }
}

/** 컴파일 에러는 실행하지 않았으므로 시간·메모리가 의미 없다 */
const notRun = (s: Pick<Submission, 'status' | 'verdict'>) => s.status !== 'DONE' || s.verdict === 'CE'

export const timeOf = (s: Submission) => (notRun(s) ? '-' : formatTime(s.timeMs))
export const memoryOf = (s: Submission) => (notRun(s) ? '-' : formatMemory(s.memoryKb))

export function formatMemory(kb: number | null): string {
  if (kb == null) return '-'
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`
}

export function formatTime(ms: number | null): string {
  return ms == null ? '-' : `${ms} ms`
}
