import { api } from './client'
import type { LanguageKey, Member, Page, ProblemDetail, ProblemSummary, Submission, TokenResponse } from './types'

export const endpoints = {
  problems: (page = 0) => api<Page<ProblemSummary>>(`/api/problems?page=${page}&size=50`),
  problem: (id: number) => api<ProblemDetail>(`/api/problems/${id}`),

  submit: (problemId: number, language: LanguageKey, source: string) =>
    api<Submission>('/api/submissions', { method: 'POST', body: { problemId, language, source }, auth: true }),
  submission: (id: number) => api<Submission>(`/api/submissions/${id}`),

  signup: (email: string, password: string, nickname: string) =>
    api<Member>('/api/auth/signup', { method: 'POST', body: { email, password, nickname } }),
  login: (email: string, password: string) =>
    api<TokenResponse>('/api/auth/login', { method: 'POST', body: { email, password } }),

  me: () => api<Member>('/api/members/me', { auth: true }),
  mySubmissions: (page = 0) => api<Page<Submission>>(`/api/members/me/submissions?page=${page}&size=20`, { auth: true }),
}
