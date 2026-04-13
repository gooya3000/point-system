package com.example.pointsystem.infrastructure.kafka;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.ledger.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 포인트 변경 이벤트를 소비해 원장에 기록합니다.
 */
@Component
@RequiredArgsConstructor
public class PointLedgerConsumer {

    private final PointLedgerService pointLedgerService;

    @KafkaListener(
            topics = "${app.kafka.point-topic:point-events}",
            groupId = "${app.kafka.ledger-consumer-group:point-ledger}"
    )
    public void consume(PointChangedEvent event) {
        pointLedgerService.record(event);
    }
}
