// Spring API 응답과 1:1로 맞춘 타입

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ProblemSummary = {
  id: number
  title: string
  timeLimitMs: number
  memoryLimitMb: number
}

export type ProblemDetail = ProblemSummary & {
  description: string
  inputDescription: string
  outputDescription: string
  samples: { seq: number; input: string; output: string }[]
}

export type LanguageKey = 'cpp' | 'python' | 'java'

export type Verdict = 'AC' | 'WA' | 'TLE' | 'MLE' | 'RE' | 'CE' | 'SE'

export type Submission = {
  id: number
  memberId: number | null
  problemId: number
  language: LanguageKey
  status: 'PENDING' | 'DONE'
  verdict: Verdict | null
  timeMs: number | null
  memoryKb: number | null
  passed: number | null
  total: number | null
  compileLog: string | null
  createdAt: string
  judgedAt: string | null
}

export type Member = {
  id: number
  email: string
  nickname: string
  role: string
  createdAt: string
}

export type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}
