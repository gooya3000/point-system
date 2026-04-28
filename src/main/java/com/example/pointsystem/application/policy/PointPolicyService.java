package com.example.pointsystem.application.policy;

import com.example.pointsystem.domain.policy.PointPolicy;
import com.example.pointsystem.infrastructure.jpa.policy.PointPolicyEntity;
import com.example.pointsystem.infrastructure.jpa.policy.PointPolicyJpaRepository;
import com.example.pointsystem.infrastructure.redis.PointPolicyCacheReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 포인트 적립 및 보유 한도 정책을 조회하는 응용 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private static final long DEFAULT_POLICY_ID = 1L;

    private final PointPolicyJpaRepository jpaRepository;
    private final PointPolicyCacheReader pointPolicyCacheReader;

    /**
     * 포인트 정책을 조회합니다.
     * @return PointPolicy
     */
    public PointPolicy getCurrentPolicy() {
        return pointPolicyCacheReader.getCurrentPolicy(this::loadCurrentPolicy);
    }

    private PointPolicy loadCurrentPolicy() {
        PointPolicyEntity entity = jpaRepository.findById(DEFAULT_POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("포인트 정책이 설정되어 있지 않습니다."));

        return new PointPolicy(
                entity.getMaxEarnPerTxn(),
                entity.getMaxBalance()
        );
    }

}
