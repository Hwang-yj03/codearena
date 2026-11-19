# frontend

React 19 + TypeScript + Vite로 만든 CodeArena 웹 화면입니다.

| 화면 | 주소 | 로그인 |
|---|---|---|
| 문제 목록 | `/` | |
| 문제 풀기 (지문 + 코드 에디터 + 채점 결과) | `/problems/:id` | 제출할 때만 |
| 로그인 / 회원가입 | `/login`, `/signup` | |
| 내 제출 기록 | `/me/submissions` | 필요 |

## 실행

```bash
npm install
npm run dev        # http://localhost:5173  (/api 요청은 localhost:8080으로 프록시)
npm test           # Vitest + Testing Library
npm run build      # 타입 검사 후 프로덕션 빌드 (dist/)
```

API 서버(`api/`)와 MySQL·Redis·Judge Worker(`docker compose up -d`)가 켜져 있어야 실제로 채점됩니다.

## 구조

```
src/
├── api/          fetch 래퍼(Problem Details 에러 처리), 엔드포인트, 응답 타입
├── auth/         토큰 저장소, 로그인 상태(AuthContext)
├── components/   레이아웃, 코드 에디터(Monaco), 채점 결과 패널
├── lib/          판정 표시 규칙, 언어별 기본 코드, 작성 중인 코드 임시 저장
├── pages/        화면 단위 컴포넌트
└── __tests__/    단위·화면 테스트
```

| 라이브러리 | 용도 |
|---|---|
| TanStack Query | 서버 상태 캐시, 채점 결과 폴링 (`refetchInterval`) |
| React Router 7 | 라우팅, 로그인 후 원래 페이지로 이동 |
| Monaco Editor | VS Code와 같은 코드 에디터 (CDN 대신 번들에 포함) |
| Pretendard, JetBrains Mono | 한글 본문, 코드·숫자 (npm 패키지로 번들에 포함) |
