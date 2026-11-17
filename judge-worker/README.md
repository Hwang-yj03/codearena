# judge-worker (2단계에서 구현)

Redis 큐에서 제출을 꺼내 아래 순서로 처리합니다.

1. 언어별 컴파일 (C++: g++, Java: javac, Python: 컴파일 생략)
2. 테스트케이스마다 `sandbox/runner` 실행
3. 출력 비교 → AC / WA / TLE / MLE / RE / CE 판정
4. 결과를 API 서버로 전달
