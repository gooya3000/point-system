package com.example.pointsystem.infrastructure.kafka;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.event.PointEventType;
import com.example.pointsystem.application.ledger.PointLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointLedgerConsumerTest {

    @Mock
    private PointLedgerService pointLedgerService;

    private PointLedgerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PointLedgerConsumer(pointLedgerService);
    }

    @Test
    // 의도: 소비한 Kafka 이벤트를 원장 기록 서비스로 위임한다.
    void consume_records_point_changed_event() {
        PointChangedEvent event = new PointChangedEvent(
                "event-1",
                PointEventType.EARN,
                1L,
                null,
                10L,
                1000,
                null,
                LocalDateTime.now()
        );

        consumer.consume(event);

        verify(pointLedgerService).record(event);
    }
}
