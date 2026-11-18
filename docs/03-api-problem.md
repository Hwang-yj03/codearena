# 3단계-1: Spring Boot 4 업그레이드와 문제 API

## 1. Spring Boot 3.5 → 4.1 마이그레이션

3.5는 오픈소스 지원 기간이 끝나가는 버전이라, API를 본격적으로 만들기 전에 올렸다. 코드가 적을 때 올리는 게 비용이 가장 싸다.

| 변경 | 3.5 | 4.1 |
|---|---|---|
| 웹 스타터 | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| 슬라이스 테스트 | `spring-boot-starter-test`에 포함 | `spring-boot-starter-webmvc-test` 별도 추가 |
| `@WebMvcTest` 패키지 | `o.s.b.test.autoconfigure.web.servlet` | `o.s.b.webmvc.test.autoconfigure` |
| Flyway | 의존성만 추가하면 자동 설정 | `spring-boot-starter-flyway` 필요 |
| Gradle | 7.6.4+ / 8.4+ | 8.14+ / 9.x |

Spring Boot 4는 자동 설정을 기술별 모듈로 쪼갰다(modularization). 그래서 "의존성은 있는데 자동 설정이 안 되는" 문제가 생기기 쉽고, 위 표가 그 대응이다.

## 2. 스키마는 Flyway가, 검증은 Hibernate가

- `ddl-auto: validate`: Hibernate는 테이블을 만들지 않고, 엔티티와 실제 테이블이 맞는지만 확인한다.
- 테이블 생성과 초기 데이터는 `db/migration/V*.sql`로 버전 관리한다. 운영 DB에서 `ddl-auto: update`를 쓰면 의도치 않은 스키마 변경이 생길 수 있어서 처음부터 이 방식을 택했다.
- `created_at`은 `Instant` 대신 `LocalDateTime`으로 매핑했다. Hibernate 6+는 `Instant`를 타임존 포함 타입으로 보기 때문에 MySQL `DATETIME` 컬럼과 검증 단계에서 충돌할 수 있다. 서버 타임존은 JDBC URL의 `serverTimezone=UTC`로 고정했다.

## 3. API 응답 설계

- **`Page`를 그대로 반환하지 않는다:** Spring Data의 `PageImpl`을 JSON으로 내보내면 내부 구조가 그대로 API 스펙이 된다. `PageResponse` record로 필요한 필드만 고정했다.
- **에러는 RFC 9457 Problem Details:** `{"type", "title", "status", "detail", "instance"}` 형식. Spring이 기본 지원하는 표준 형식이라 프론트와 에러 처리 규칙을 따로 정할 필요가 없다.
- **목록과 상세 DTO 분리:** 목록에는 지문과 예제를 빼서 응답 크기를 줄였다.
- **`open-in-view: false`:** 컨트롤러에서 지연 로딩이 일어나지 않게 막고, 서비스 트랜잭션 안에서 DTO로 변환한다.

## 4. 테스트 전략

| 테스트 | 범위 | DB 필요 |
|---|---|---|
| `ProblemServiceTest` | Mockito 단위 테스트 (페이지 크기 제한, 404, 예제 매핑) | X |
| `ProblemControllerTest` | `@WebMvcTest` 슬라이스 (JSON 형식, 404 Problem Details) | X |
| 실제 MySQL 연동 | `docker compose` + `bootRun` 수동 확인 | O |

DB 연동 자동 테스트는 Testcontainers로 이후 단계에서 추가한다.

## API

```
GET /api/problems?page=0&size=20
GET /api/problems/{id}
```
