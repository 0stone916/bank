# Financial CMS: 3-MSA 기반 실시간 결제 관리 및 자산 동기화 시스템

본 프로젝트는 은행의 핵심 기능을 수행하는 Core-Banking(원장 시스템)과 사용자의 금융 데이터를 관리하는 Financial CMS(자산 관리 시스템) 사이의 데이터를 실시간으로 안전하게 동기화하는 분산 시스템 아키텍처를 설계하고 구현했습니다.

---

## 🛠 Tech Stack
- **Back-end**: Java 17, Spring Boot, Spring Batch 5, Redis, MySQL, MyBatis, Kafka
- **Front-end**: React, SockJS, STOMP
- **Infrastructure**: Docker, GitHub Actions

---

## 🏛 [아키텍처] 3-MSA 설계
<img width="887" height="774" alt="Untitled-2026-02-28-152w0" src="https://github.com/user-attachments/assets/898d0e60-0ac9-43eb-bf31-60733f7041ee" />

`Payment-App`(결제 요청) -> `Core-Banking`(원장 처리/Kafka 메시지 발행) -> `Financial CMS`(알림 수신 및 분석)로 이어지는 **Event-Driven Architecture(EDA)**를 통해 시스템 간 결합도를 낮추고 가용성을 극대화했습니다.

---

## 🚀 핵심 기술적 해결 과제 (Engineering Challenge)

### 1. 분산 환경의 데이터 정합성 보장 (Transactional Outbox Pattern)
- **Problem**: Core-Banking 로직 내에서 **Kafka 메시지를 직접 발행**할 경우, DB 롤백 시에도 메시지는 취소되지 않아 데이터 불일치 발생.
- **Solution**: 
    - **Spring Event & @TransactionalEventListener**: 트랜잭션 커밋 완료 후 메시지를 발행하도록 격리.
    - **Transactional Outbox 패턴**: 발행할 메시지를 DB(Outbox 테이블)에 먼저 저장하여, 메시지 브로커(Kafka) 장애 시에도 데이터 유실 없이 재전송 가능한 구조 구축.
- **Result**: 결제 원장 커밋과 알림 발행의 원자성을 확보하여 데이터 유실 및 부정합 0% 달성.

### 2. 장애 복구 자동화 및 자원 고착화 해결 (Distributed Lock & Retry)
- **Problem**: Kafka 재시도 동작 중 트랜잭션 종료 시점과 락 해제 시점의 불일치로 인해 리소스 반납이 지연되고, 다음 재시도 스레드의 진입을 방해하는 잠재적 락 고착화 위험성 포착.
- **Solution**: 
    - **물리적 레이어 분리**: 락 획득/해제를 담당하는 외부 메서드(비트랜잭션)와 실제 DB 작업을 수행하는 @Transactional 내부 메서드를 완전히 격리.
    - **Kafka 전용 ErrorHandler 도입**: 웹 스레드와 독립적인 컨슈머 스레드 특성을 반영하여, 기존 예외 처리기의 사각지대를 제거하고 정밀한 모니터링 체계 구축.
- **Result**: 인프라 설정에 요행으로 의존하지 않고, 어떤 예외 상황에서도 자원을 확실히 반납하는 구조적 안정성 확보.
---

## ⚙️ 운영 고도화 및 장애 복구 전략 (Spring Batch 5)

금융 도메인의 특성을 고려하여 대용량 데이터의 안정적 처리와 장애 발생 시 복구 탄력성 확보를 목표로 설계했습니다.

### 1. 장애 허용(Fault Tolerance) 및 이력 관리
- **Strategy**: **faultTolerant** 및 **skip** 정책을 적용하여 단일 데이터 오류로 인한 전체 배치 중단 리스크 해소.
- **Implementation**: **BatchSkipListener**를 통해 실패 데이터와 사유를 별도 테이블에 기록하여 사후 대응 기반 마련.

### 2. 중복 처리 방지 및 멱등성(Idempotency) 확보
- **Strategy**: 배치가 중간에 재실행되더라도 데이터가 중복 생성되지 않도록 설계.
- **Implementation**: 애플리케이션 수준의 **exists 체크**와 DB 수준의 **Composite Unique Key** 제약을 결합하여 이중 방어.

### 3. 유연한 재처리(Reprocess) 구조
- **Strategy**: 실패한 데이터의 효율적인 복구를 위해 인스턴스 관리 전략 수립.
- **Implementation**: 실패 이력 테이블(`reconciliation_error_log`)을 Reader로 사용하는 별도 Job 구성 및 동일 파라미터 기반의 **Restart** 메커니즘 검증.

---

## 🧪 장애 대응 테스트 시나리오 (Verification)

실제 운영 환경의 장애 상황을 가정한 3단계 검증을 통해 시스템 안정성을 확인했습니다.

### 1. 외부 API 장애 시 자동 복구 검증
- **상황**: Bank API 호출 시 타임아웃 예외 강제 발생.
- **결과**: **Retry** 설정(3회)에 따라 자동 재시도 후 API 정상 응답 시 배치가 중단 없이 완료됨을 확인.

### 2. 비즈니스 로직 오류 데이터 스킵 검증
- **상황**: 승인번호 형식 오류 또는 금액 불일치 데이터 투입.
- **결과**: **SkipException** 발생 시 배치는 계속 진행되며, **SkipListener**가 실패 사유를 DB에 정확히 기록함.

### 3. 시스템 셧다운 후 재시작 기능 검증
- **상황**: 처리 중 런타임 에러 발생으로 Job 상태를 **FAILED**로 강제 종료.
- **결과**: 장애 원인 제거 후 동일한 **JobParameter**로 재요청 시 **JobRepository**를 통해 중단 지점부터 재개됨을 확인.

---

## 🔐 인증 및 보안 (Security)
- **Redis 기반 단일 세션 관리**: Redis에 userId를 Key로 토큰을 저장하여, 재로그인 시 기존 세션을 즉시 무효화하는 서버 주도 통제권 확보.
- **Double Token 구조**: Access/Refresh Token 및 Axios 인터셉터를 통한 자동 재발급 로직 구현.
- **로그아웃 실효성**: 로그아웃 시 Redis 내 토큰 데이터를 즉시 삭제하여 실시간 세션 차단 기능 확보.

---

## 🧪 테스트 및 안정성 검증
- **동시성 실증**: 멀티스레드 환경에서 분산 락 작동 시 1건만 성공하고 나머지는 예외 발생함을 검증.
- **배치 시나리오 테스트**: 운영 환경과 동일한 DB 환경에서 재실행 및 실패 건 재처리 로직 검증.
- **실시간 통지 검증**: SockJS Fallback 메커니즘을 통한 웹소켓 차단 환경에서의 알림 수신 실증.
- **시스템 과부하 시 자원 격리**:kafka전송 시 핵심 로직 보호를 위해 세마포어를 활용한 Bulkhead 패턴을 추가 적용하여 쓰레드 고갈을 방지 검증.

---

## 📘 상세 설계 문서
- Financial CMS 노션 상세 페이지: [바로가기](https://www.notion.so/2cc2c24577cc80638969fa8cf6d240d5)
- bank 깃: [바로가기](https://github.com/0stone916/bank)

---

## 📋 Product Overview
<img width="861" height="907" alt="스크린샷 2026-03-03 000038" src="https://github.com/user-attachments/assets/0fc5ad57-f010-444d-ae46-7f27da3695e6" />
