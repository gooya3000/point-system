package com.example.pointsystem.application.policy;

import com.example.pointsystem.domain.policy.PointPolicy;
import com.example.pointsystem.infrastructure.jpa.policy.PointPolicyEntity;
import com.example.pointsystem.infrastructure.jpa.policy.PointPolicyJpaRepository;
import com.example.pointsystem.infrastructure.redis.PointPolicyCacheReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointPolicyServiceTest {

    @Mock
    private PointPolicyJpaRepository pointPolicyJpaRepository;
    @Mock
    private PointPolicyCacheReader pointPolicyCacheReader;

    private PointPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PointPolicyService(pointPolicyJpaRepository, pointPolicyCacheReader);
    }

    @Test
    // 의도: 정책 조회는 캐시 reader를 통해 수행한다.
    void getCurrentPolicy_delegates_to_cache_reader() {
        PointPolicy expected = new PointPolicy(100000, 1000000);
        when(pointPolicyCacheReader.getCurrentPolicy(any())).thenReturn(expected);

        PointPolicy result = service.getCurrentPolicy();

        assertEquals(expected, result);
        verify(pointPolicyCacheReader).getCurrentPolicy(any());
    }

    @Test
    // 의도: 캐시 reader가 넘겨받은 loader는 DB 정책을 도메인 모델로 변환한다.
    @SuppressWarnings("unchecked")
    void getCurrentPolicy_loader_reads_current_policy_from_repository() {
        when(pointPolicyCacheReader.getCurrentPolicy(any())).thenAnswer(invocation -> {
            Supplier<PointPolicy> loader = invocation.getArgument(0);
            return loader.get();
        });
        when(pointPolicyJpaRepository.findById(1L))
                .thenReturn(Optional.of(pointPolicyEntity()));

        PointPolicy result = service.getCurrentPolicy();

        assertEquals(100000, result.getMaxEarnPerTxn());
        assertEquals(1000000, result.getMaxBalance());
    }

    private PointPolicyEntity pointPolicyEntity() {
        PointPolicyEntity entity = new PointPolicyEntity();
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity, "maxEarnPerTxn", 100000);
        ReflectionTestUtils.setField(entity, "maxBalance", 1000000);
        ReflectionTestUtils.setField(entity, "updatedAt", LocalDateTime.now());
        return entity;
    }
}
