# 3단계-3: 회원과 JWT 인증

## 구조

```
POST /api/auth/signup  → 회원가입 (BCrypt 해시 저장)
POST /api/auth/login   → Access Token(JWT, HS256) 발급
Authorization: Bearer <token>
  → Spring Security OAuth2 Resource Server가 서명·만료·typ 검증 후 SecurityContext에 Jwt 저장
  → 컨트롤러는 @AuthenticationPrincipal Jwt 로 회원 id(sub)를 꺼냄
```

| API | 인증 |
|---|---|
| `GET /api/problems`, `/api/problems/{id}` | 불필요 |
| `GET /api/submissions/{id}` | 불필요 (소스 코드는 응답에 없음) |
| `POST /api/submissions` | **필요** |
| `GET /api/members/me`, `/api/members/me/submissions` | **필요** |

## 1. 직접 만든 JWT 필터 대신 Resource Server

흔히 `OncePerRequestFilter`를 상속해 토큰 파싱 필터를 직접 만들지만, Spring Security에는 이미 표준 구현이 있다.
`oauth2ResourceServer().jwt()`를 켜면 `BearerTokenAuthenticationFilter`가 헤더 추출, 서명 검증, 만료 검증, 401 응답(`WWW-Authenticate`)까지 처리한다.
직접 짠 필터에서 자주 나오는 실수(만료 검증 누락, 예외 시 500 응답 등)를 피할 수 있고, 나중에 외부 인증 서버(RS256 + JWKS)로 바꿀 때도 `JwtDecoder` 빈만 교체하면 된다.

## 2. Spring Security 7의 typ 헤더 검증

Spring Security 7의 `NimbusJwtDecoder`는 기본으로 JWT 헤더의 `typ` 값을 검증한다.
인코더 생성 방식에 따라 `typ`이 잘못 들어가는 버그가 보고된 적이 있어서, 발급할 때 `JwsHeader.with(HS256).type("JWT")`로 직접 지정했다.
`JwtTokenProviderTest`는 스프링 컨텍스트 없이 **실제 인코더로 발급하고 실제 디코더로 검증**하는 왕복 테스트로 이를 보장한다.

## 3. 비밀번호와 계정 정보 보호

- **DelegatingPasswordEncoder(BCrypt):** 저장값이 `{bcrypt}$2a$...` 형식이라, 나중에 Argon2로 바꿔도 기존 회원의 비밀번호를 그대로 검증할 수 있다.
- **로그인 실패 메시지 통일:** 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 준다. 메시지가 다르면 가입된 이메일 목록을 알아낼 수 있다.
- **이메일 정규화:** 가입과 로그인 모두 `trim().toLowerCase()`로 `User@Example.com`과 `user@example.com`을 같은 계정으로 취급한다.
- **중복 가입 경쟁 조건:** 사전 중복 검사(`existsByEmail`)를 동시에 통과해도 DB 유니크 제약이 막고, `DataIntegrityViolationException`을 409로 변환한다.

## 4. 비밀키 관리

- HS256은 256비트(32바이트) 이상의 키가 필요하다. 더 짧으면 **애플리케이션 시작 시점에 실패**하게 해서 약한 키로 운영되는 일을 막았다.
- 기본값은 로컬 개발용이고, 운영에서는 `JWT_SECRET` 환경 변수로 주입한다.

## 5. 테스트에서의 인증

`@WebMvcTest`에 `@Import(SecurityConfig.class)`로 실제 보안 규칙을 적용하고, `JwtDecoder`만 목으로 바꾼다.
요청에는 `spring-security-test`의 `jwt()` 후처리기로 인증된 사용자를 흉내낸다. 덕분에 "토큰 없으면 401", "토큰의 sub로 내 기록 조회" 같은 **인가 규칙 자체를 테스트**할 수 있다.

## 6. 남은 과제

| 항목 | 계획 |
|---|---|
| Refresh Token | Access Token 만료(1시간) 후 재로그인 필요. Redis에 Refresh Token 저장 + 회전(rotation) |
| 로그아웃 | JWT는 서버가 무효화할 수 없음. 짧은 만료 + Refresh Token 폐기로 대응 |
| 로그인 시도 제한 | Redis로 IP/계정별 실패 횟수 제한 (무차별 대입 방지) |
| 관리자 기능 | `ADMIN` 역할로 문제 등록 API |
