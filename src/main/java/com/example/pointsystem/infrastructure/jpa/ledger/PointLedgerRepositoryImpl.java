package com.example.pointsystem.infrastructure.jpa.ledger;

import com.example.pointsystem.domain.ledger.PointLedger;
import com.example.pointsystem.domain.ledger.PointLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 포인트 원장 저장소의 JPA 구현체입니다.
 */
@Repository
@RequiredArgsConstructor
public class PointLedgerRepositoryImpl implements PointLedgerRepository {

    private final PointLedgerJpaRepository pointLedgerJpaRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return pointLedgerJpaRepository.existsByEventId(eventId);
    }

    @Override
    public PointLedger save(PointLedger pointLedger) {
        PointLedgerEntity saved = pointLedgerJpaRepository.save(PointLedgerMapper.toEntity(pointLedger));
        return PointLedgerMapper.toDomain(saved);
    }
}
