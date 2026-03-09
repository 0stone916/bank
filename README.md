# Financial CMS: 3-MSA 기반 실시간 결제 관리 및 자산 동기화 시스템

본 프로젝트는 은행의 핵심 기능을 수행하는 Core-Banking(원장 시스템)과 사용자의 금융 데이터를 관리하는 Financial CMS(자산 관리 시스템) 사이의 데이터를 실시간으로 안전하게 동기화하는 분산 시스템 아키텍처를 설계하고 구현했습니다.

## 🛠 Tech Stack
- **Back-end**: Java 17, Spring Boot, Spring Batch 5, Redis, MySQL, MyBatis, Kafka
- **Front-end**: React, SockJS, STOMP
- **Infrastructure**: Docker, GitHub Actions

## [아키텍처] 3-MSA 설계
<img width="887" height="774" alt="Untitled-2026-02-28-152w0" src="https://github.com/user-attachments/assets/898d0e60-0ac9-43eb-bf31-60733f7041ee" />



`Payment-App`(결제 요청) -> `Core-Banking`(원장 처리/Kafka 메시지 발행) -> `Financial CMS`(알림 수신 및 분석)로 이어지는 Event-Driven Architecture(EDA)를 통해 시스템 간 결합도를 낮추고 가용성을 극대화했습니다.

## 🚀 핵심 기술적 해결 과제 (Engineering Challenge)

### 1. 분산 환경의 데이터 정합성 보장 (Transactional Outbox Pattern)
- **Problem**: Core-Banking 로직 내에서 **Kafka 메시지를 직접 발행**할 경우, DB 롤백 시에도 메시지는 취소되지 않아 데이터 불일치 발생.
- **Solution**: 
    - **Spring Event & @TransactionalEventListener**: 트랜잭션 커밋 완료 후 메시지를 발행하도록 격리.
    - **Transactional Outbox 패턴**: 발행할 메시지를 DB(Outbox 테이블)에 먼저 저장하여, 메시지 브로커(Kafka) 장애 시에도 데이터 유실 없이 재전송 가능한 구조 구축.
- **Result**: 결제 원장 커밋과 알림 발행의 원자성을 확보하여 데이터 유실 및 부정합 0% 달성.

### 2. 장애 복구 자동화 및 자원 고착화 해결 (Distributed Lock & Retry)
일시적 오류 발생 시 시스템이 스스로 복구될 수 있는 메커니즘을 설계하고 자원 점유 문제를 해결했습니다.

- **Problem**: Kafka 재시도 동작 중 트랜잭션 종료 시점과 락 해제 시점의 불일치로 인해 리소스 반납이 지연되고, 다음 재시도 스레드의 진입을 방해하는 잠재적 락 고착화 위험성 포착.
- **Solution**: 
    - **물리적 레이어 분리**: 락 획득/해제를 담당하는 외부 메서드(비트랜잭션)와 실제 DB 작업을 수행하는 @Transactional 내부 메서드를 완전히 격리.
    - **Kafka 전용 ErrorHandler 도입**: 웹 스레드와 독립적인 컨슈머 스레드 특성을 반영하여, 기존 예외 처리기의 사각지대를 제거하고 정밀한 모니터링 체계 구축.
- **Result**: 인프라 설정에 요행으로 의존하지 않고, 어떤 예외 상황에서도 자원을 확실히 반납하는 구조적 안정성 확보.



### 3. 대용량 지출 내역 조회 성능 최적화
- **Problem**: 400만 건 규모의 데이터 조회 시 인덱스 부재 및 OFFSET 방식의 한계로 응답 지연 23초 발생.
- **Solution**: 
    - **복합 인덱스 전략**: (user_id, year, month, day DESC, id DESC) 인덱스를 설계하여 Filesort 오버헤드 물리적 제거.
    - **No-Offset 커서 페이징**: 마지막 조회 식별자를 활용해 데이터 규모와 무관한 O(1) 수준의 탐색 성능 확보.
- **Result**: 응답 시간을 23s에서 22ms로 단축(약 1,000배 개선)하여 실시간 가용성 확보.

## ⚙️ 운영 고도화 (Spring Batch 5)
단순 자동화를 넘어 운영 가시성과 장애 복구를 고려한 아키텍처를 설계했습니다.

- **배치 결과 요약 및 감사(Audit) 체계**: StepExecution을 활용하여 배치 단위별 성공/스킵/실패 건수를 집계하는 요약 테이블 및 전용 로그 도입.
- **장애 허용(Fault Tolerance) 및 재처리**: 실패 이력을 Reader로 사용하는 별도의 Job을 설계하여 실패 건만 선택적 재처리 가능하도록 구현.
- **멱등성(Idempotency) 확보**: 애플리케이션(exists 체크)과 DB(Composite Unique Key) 수준의 이중 방어 설계.

## 🔐 인증 및 보안 (Security)
- **Redis 기반 단일 세션 관리**: Redis에 userId를 Key로 토큰을 저장하여, 재로그인 시 기존 세션을 즉시 무효화하는 서버 주도 통제권 확보.
- **Double Token 구조**: Access/Refresh Token 및 Axios 인터셉터를 통한 자동 재발급 로직 구현.
- **로그아웃 실효성**: 로그아웃 시 Redis 내 토큰 데이터를 즉시 삭제하여 실시간 세션 차단 기능 확보.

## 🧪 테스트 및 안정성 검증
- **동시성 실증**: 멀티스레드 환경에서 분산 락 작동 시 1건만 성공하고 나머지는 예외 발생함을 검증.
- **배치 시나리오 테스트**: 운영 환경과 동일한 DB 환경에서 재실행 및 실패 건 재처리 로직 검증.
- **실시간 통지 검증**: SockJS Fallback 메커니즘을 통한 웹소켓 차단 환경에서의 알림 수신 실증.

## 📘 상세 설계 문서
- Financial CMS 노션 상세 페이지: [바로가기](https://www.notion.so/2cc2c24577cc80638969fa8cf6d240d5)
- bank 깃: [바로가기](https://github.com/0stone916/bank)

## 📋 Product Overview
<img width="861" height="907" alt="스크린샷 2026-03-03 000038" src="https://github.com/user-attachments/assets/0fc5ad57-f010-444d-ae46-7f27da3695e6" />
