package com.example.pointsystem.infrastructure.jpa.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PointLedgerEntity 에 대한 Spring Data JPA 저장소입니다.
 */
public interface PointLedgerJpaRepository extends JpaRepository<PointLedgerEntity, Long> {

    boolean existsByEventId(String eventId);
}
