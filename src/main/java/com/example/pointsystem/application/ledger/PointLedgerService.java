package com.example.pointsystem.application.ledger;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.domain.ledger.PointLedger;
import com.example.pointsystem.domain.ledger.PointLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 변경 이벤트를 원장에 멱등하게 기록합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointLedgerService {

    private final PointLedgerRepository pointLedgerRepository;

    @Transactional
    public void record(PointChangedEvent event) {
        if (pointLedgerRepository.existsByEventId(event.eventId())) {
            return;
        }

        try {
            pointLedgerRepository.save(PointLedger.from(event));
        } catch (DataIntegrityViolationException e) {
            log.info("이미 기록된 포인트 원장 이벤트입니다. eventId={}", event.eventId());
        }
    }
}
