# PR Template & Guide

이 문서는 PR 작성 방법과 각 단계별 PR 설명 예시를 제공합니다.
PR 설명은 단순한 변경 로그가 아니라 **기술적 사고 흐름의 기록**입니다.

---

## PR 작성 템플릿

GitHub에서 PR 생성 시 아래 템플릿을 사용합니다.
`.github/pull_request_template.md`에 저장하여 자동 적용하세요.

```markdown
## 변경 배경

> 왜 이 PR가 필요한가? 어떤 문제를 해결하는가?

(내용 작성)

## 해결 방법

> 어떤 기술적 선택을 했는가? 왜 이 방법을 선택했는가?

(내용 작성)

## 트레이드오프

> 이 선택의 한계는 무엇인가? 다음 개선 방향은?

(내용 작성)

## 테스트 결과

> 어떤 테스트로 검증했는가?

- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 동시성 테스트 통과 (해당 시)

## 관련 문서

- `docs/concurrency.md` Step N 참조
```

---

## PR 예시 — Step 1: 순진한 구현

**제목**: `feat: 재고 차감 기본 구현`

```markdown
## 변경 배경

주문 플로우의 첫 번째 구성 요소인 재고 차감 기능을 구현합니다.
이 PR는 의도적으로 동시성 처리를 제외한 단순 구현으로 시작합니다.
이후 PR에서 동시성 문제를 테스트로 증명하고 단계적으로 개선할 예정입니다.

## 해결 방법

단일 스레드 환경을 가정한 기본 구현입니다.
- 재고 부족 시 InsufficientStockException 발생
- 비즈니스 로직은 Stock 도메인 객체 내부에 위치 (SRP)

## 트레이드오프

동시 요청 시 race condition 발생 가능합니다.
다음 PR(feature/stock-concurrency-test)에서 문제를 테스트로 재현합니다.

## 테스트 결과

- [x] 단위 테스트 통과 (재고 차감, 부족 예외)
- [ ] 동시성 테스트 — 다음 PR에서 추가
```

---

## PR 예시 — Step 2: 동시성 문제 재현

**제목**: `test: 재고 동시성 문제 재현 (race condition 증명)`

```markdown
## 변경 배경

Step 1 구현에서 동시성 처리가 없음을 확인했습니다.
이 PR는 실제로 race condition이 발생함을 테스트로 증명합니다.

## 해결 방법

CountDownLatch + ExecutorService로 동시 100개 요청을 시뮬레이션했습니다.

race condition 발생 원리:
- 트랜잭션 A: SELECT quantity=100 → quantity-1=99
- 트랜잭션 B: SELECT quantity=100 → quantity-1=99  (같은 값을 읽음)
- 트랜잭션 A: UPDATE quantity=99 COMMIT
- 트랜잭션 B: UPDATE quantity=99 COMMIT  (A의 차감이 무시됨)

결과: 100번 차감했으나 실제 차감량은 그보다 적음.

## 트레이드오프

이 테스트는 현재 의도적으로 실패합니다.
Step 3(낙관적 락) 적용 후 통과하도록 설계했습니다.

## 테스트 결과

- [x] 동시성 문제 재현 확인 (테스트 실패가 곧 증명)
```

---

## PR 예시 — Step 3: 낙관적 락

**제목**: `refactor: 낙관적 락 적용으로 재고 동시성 처리`

```markdown
## 변경 배경

Step 2에서 race condition을 확인했습니다.
쓰기 충돌이 상대적으로 적은 일반 주문 환경에서는
낙관적 락이 비관적 락보다 성능상 유리합니다.

## 해결 방법

JPA @Version을 활용한 낙관적 락을 적용했습니다.

동작 원리:
  UPDATE stock SET quantity=?, version=version+1
  WHERE id=? AND version=?   ← version 불일치 시 0 rows → 예외

충돌 시 ObjectOptimisticLockingFailureException 발생.
@Retryable로 최대 3회 재시도, 100ms 간격으로 설정.

## 트레이드오프

선착순 이벤트처럼 충돌이 잦은 환경에서는 재시도가 폭발적으로 증가합니다.
(스레드 500개 테스트: 평균 2.8회 재시도 측정)
고트래픽 시나리오에서는 Redis 분산 락이 적합합니다 → Step 4에서 개선.

## 테스트 결과

- [x] 단위 테스트 통과
- [x] 동시성 테스트 통과 (100 스레드, 재고 정확히 차감)
- [x] Step 2에서 실패하던 테스트 이제 통과
```

---

## PR 예시 — Step 4: Redis 분산 락

**제목**: `perf: Redis 분산 락으로 고트래픽 재고 처리 개선`

```markdown
## 변경 배경

낙관적 락은 충돌 빈도가 높을 때 재시도 폭발 문제가 있습니다.
선착순 이벤트 시나리오(500 스레드)에서 실측한 결과:
- 낙관적 락: 평균 2.8회 재시도, 일부 요청 maxAttempts 초과 실패
- 분산 락 목표: 재시도 0회, 안정적 처리

## 해결 방법

Redisson tryLock을 활용한 분산 락을 적용했습니다.
- 락 key: "lock:stock:{productId}" (product 단위 granularity)
- 대기 시간: 3초 (초과 시 LockAcquisitionFailedException)
- 점유 시간: 1초 (데드락 방지 자동 해제)

StockLockFacade가 락 획득을 담당하고,
StockService는 락을 모름 (SRP, 관심사 분리).

## 트레이드오프

- Redis 장애 시 주문 불가 (SPOF) → 운영 환경에서는 Redis Sentinel 필요
- 락 granularity: 현재 product 단위 → SKU 단위로 세분화 가능 (향후 개선)
- tryLock timeout 3초: 부하 테스트로 적정값 검증 필요

## 테스트 결과

- [x] 동시 500 요청 → 재고 정확히 차감 확인
- [x] Redis 연결 끊김 시나리오 → 명확한 예외 메시지 확인
- [x] 락 timeout 초과 시 → 사용자 친화적 에러 응답 확인
```

---

## 커밋 메시지 규칙

Conventional Commits 형식을 사용합니다.

```
<type>: <description>

type 목록:
  feat     새로운 기능
  fix      버그 수정
  test     테스트 추가/수정
  refactor 리팩토링 (기능 변경 없음)
  perf     성능 개선
  docs     문서 수정
  chore    빌드, 설정 변경
```

**좋은 예시**:
```
feat: 재고 차감 기본 구현
test: 동시 100 요청 race condition 재현 테스트 추가
refactor: 낙관적 락 적용으로 Stock 동시성 처리
perf: Redisson 분산 락으로 고트래픽 재고 처리 개선
refactor: 할인 정책 전략 패턴으로 OCP 적용
```

**나쁜 예시**:
```
fix bug
update code
wip
```

---

## 브랜치 전략

GitHub Flow를 사용합니다. `main` 브랜치는 항상 배포 가능한 상태를 유지합니다.

```
main
 └── feature/stock-basic                  # Step 1
 └── feature/stock-concurrency-test       # Step 2
 └── feature/stock-optimistic-lock        # Step 3
 └── feature/stock-distributed-lock       # Step 4
 └── feature/coupon-issue                 # Step 5
 └── feature/point-transaction            # Step 6
 └── feature/settlement-batch             # Step 7
 └── refactor/discount-policy-strategy    # OCP 리팩토링
```

각 브랜치는 PR → Code Review (self-review라도 작성) → Merge 순서로 진행합니다.
