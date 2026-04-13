# Point Wallet Service (DDD + Redis + Kafka)

## 📌 프로젝트 배경
현재 회사에서 **선불 서비스**를 운영 중이며, 신규로 **적립 포인트** 기능이 필요하여 요구사항을 구현해보고자 합니다.

포인트 시스템은 **대용량 트래픽**, **포인트 사용 시 동시성 문제**, **정산/원장 이력 관리**, **만료 처리** 등 높은 수준의 안정성과 확장성을 요구합니다.

따라서, 이번 프로젝트는 이러한 요구사항을 대비하여 **도메인 주도 설계(DDD)** 를 기반으로 포인트 기능을 구현하고,  
대용량 환경에서도 안정적으로 동작할 수 있도록 **Redis(분산 락 + 캐싱)**, **Kafka(이벤트 기반 비동기 처리)** 를 적용한 **실전형 아키텍처 구축**을 목표로 합니다.

---

# 🚀 기술 스택

## ✔ Backend
- **Java 21**
- **Spring Boot 3.x**
- Spring Data JPA
- Spring Web
- Spring Validation

## ✔ Database
- **H2 Database (개발/테스트 환경)**
    - 인메모리 DB로 빠르고 편리함
    - Redis와 함께 사용 가능 (Redis는 캐시/락 역할)
- 이후 실서비스 고려 시 PostgreSQL/MySQL 확장 가능

## ✔ Messaging / Cache
- **Redis**
    - 회원 단위 분산 락(Redis Lock)
    - 포인트 잔액/만료 요약 캐싱
    - 이벤트 스냅샷 캐싱

- **Apache Kafka**
    - 포인트 적립/사용/사용취소/만료 이벤트 발행
    - 이벤트 기반 정산/원장 서비스 구축 가능
    - 알림 서비스 및 배치 시스템 확장 가능

## ✔ Build / Test
- Gradle 8.x
- JUnit 5


---
# 🔗 전체 아키텍처

```
[Controller]
     ↓
[Application Service]
     ↓ (Redis Distributed Lock)
[Domain Layer – PointWallet Aggregate]
     ↓
[RDB(H2) 저장]
     ↓
[Kafka Producer] → point-events
     ↓
-----------------------------------------------------
   LedgerConsumer → 원장 DB 저장
   ExpireConsumer → 만료 처리 자동화
   NotificationConsumer → 문자/푸시 알림
-----------------------------------------------------
[Redis Cache] → balance, snapshot 저장
```

---

# 🧩 주요 구현 범위

## ✔ Point Wallet
- 회원 단위 포인트 지갑 Aggregate
- 포인트 적립/사용/적립취소/사용취소 처리
- 만료일, 잔여 금액, 적립 유형, 상태를 기준으로 사용 가능 포인트 계산

## ✔ 동시성 및 멱등성
- Redis 분산 락을 통한 회원 단위 포인트 변경 동시성 제어
- 주문번호 기준 포인트 사용 멱등성 처리
- Redis 캐시를 통한 지갑/정책 조회 성능 보완

## ✔ Kafka 이벤트 기반 확장
- 포인트 변경 이벤트를 `point-events` 토픽으로 발행
- `LedgerConsumer`가 이벤트를 소비해 원장 테이블(`point_ledger`)에 이력 저장
- `eventId`와 DB 유니크 제약을 이용해 원장 중복 저장 방지

## ✔ Point Ledger
- `PointWallet`은 현재 포인트 상태를 책임지고, `PointLedger`는 변경 이력을 책임지도록 분리
- 적립/사용/적립취소/사용취소 이벤트를 정산·감사 가능한 append-only 이력으로 보존
- 향후 정산, CS 추적, 통계성 조회로 확장 가능

---

# 🧭 향후 확장 방향

- Kafka 발행 신뢰성 보강: Outbox 패턴을 도입해 DB 변경과 이벤트 발행 사이의 유실 가능성 완화
- 만료 처리 자동화: 만료 이벤트 타입 추가, 만료 대상 조회/처리 서비스 구현, 만료 결과 원장 기록
- 알림/배치 연동: 포인트 변경 이벤트를 기반으로 알림 서비스와 배치 시스템 확장
- 운영 DB 전환: H2 기반 개발 환경에서 PostgreSQL/MySQL 등 실서비스 DB로 확장

---

# 🎯 프로젝트 목적 요약

> “실제 서비스 수준의 정확성 & 확장성을 갖춘 포인트 지갑 시스템을 DDD 기반으로 설계하고,  
> Redis(락/캐싱) + Kafka(이벤트 기반) 아키텍처를 접목하여 안정적인 대규모 트래픽 처리 환경을 구축하는 것”.  
