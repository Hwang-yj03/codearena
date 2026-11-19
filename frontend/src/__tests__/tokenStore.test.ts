import { describe, expect, it } from 'vitest'
import { readClaims } from '../auth/tokenStore'
import { fakeJwt } from './helpers'

describe('readClaims', () => {
  it('reads member id, nickname and expiry from the payload', () => {
    const token = fakeJwt({ sub: '7', nickname: '테스터', exp: 2_000_000_000 })
    expect(readClaims(token)).toEqual({ memberId: 7, nickname: '테스터', expiresAt: 2_000_000_000_000 })
  })

  it('returns null for malformed tokens', () => {
    expect(readClaims(null)).toBeNull()
    expect(readClaims('not-a-jwt')).toBeNull()
    expect(readClaims('a.b.c')).toBeNull()
    expect(readClaims(fakeJwt({ nickname: 'no-sub' }))).toBeNull()
  })
})
