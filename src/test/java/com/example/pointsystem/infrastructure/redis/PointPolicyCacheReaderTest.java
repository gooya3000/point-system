package com.example.pointsystem.infrastructure.redis;

import com.example.pointsystem.domain.policy.PointPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointPolicyCacheReaderTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<PointPolicyCacheReader.CachedPointPolicy> bucket;
    @Mock
    private RLock lock;

    private PointPolicyCacheReader reader;

    @BeforeEach
    void setUp() {
        reader = new PointPolicyCacheReader(redissonClient);
        when(redissonClient.<PointPolicyCacheReader.CachedPointPolicy>getBucket("cache:pointPolicy:current")).thenReturn(bucket);
    }

    @Test
    // 의도: fresh 정책 캐시는 DB loader 없이 바로 반환한다.
    void getCurrentPolicy_returns_fresh_cached_policy() {
        PointPolicy cached = new PointPolicy(100, 1000);
        when(bucket.get()).thenReturn(cachedPolicy(cached, 1, 4));

        PointPolicy result = reader.getCurrentPolicy(() -> {
            throw new AssertionError("loader should not be called");
        });

        assertSame(cached, result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    // 의도: hard miss에서 락을 획득한 요청이 정책을 DB에서 읽어 캐시에 저장한다.
    void getCurrentPolicy_rebuilds_cache_on_miss_when_lock_is_acquired() throws Exception {
        PointPolicy loaded = new PointPolicy(100, 1000);
        when(bucket.get()).thenReturn(null, (PointPolicyCacheReader.CachedPointPolicy) null);
        when(redissonClient.getLock("lock:cache:pointPolicy:current")).thenReturn(lock);
        when(lock.tryLock(150L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PointPolicy result = reader.getCurrentPolicy(() -> loaded);

        assertSame(loaded, result);
        verify(bucket).set(any(PointPolicyCacheReader.CachedPointPolicy.class), any(java.time.Duration.class));
        verify(lock).unlock();
    }

    @Test
    // 의도: stale 정책은 즉시 반환하고, 배경 갱신은 한 번만 트리거한다.
    void getCurrentPolicy_returns_stale_policy_while_triggering_refresh() throws Exception {
        PointPolicy stale = new PointPolicy(100, 1000);
        PointPolicy refreshed = new PointPolicy(200, 2000);
        AtomicInteger loadCount = new AtomicInteger();

        when(bucket.get()).thenReturn(
                cachedPolicy(stale, -1, 2),
                cachedPolicy(stale, -1, 2)
        );
        when(redissonClient.getLock("lock:cache:pointPolicy:current")).thenReturn(lock);
        when(lock.tryLock(0L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PointPolicy result = reader.getCurrentPolicy(() -> {
            loadCount.incrementAndGet();
            return refreshed;
        });

        assertSame(stale, result);
        verify(bucket, timeout(1000)).set(any(PointPolicyCacheReader.CachedPointPolicy.class), any(java.time.Duration.class));
        assertEquals(1, loadCount.get());
    }

    private PointPolicyCacheReader.CachedPointPolicy cachedPolicy(PointPolicy pointPolicy, long freshOffsetMinutes, long staleOffsetMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return new PointPolicyCacheReader.CachedPointPolicy(
                pointPolicy,
                now.plusMinutes(freshOffsetMinutes),
                now.plusMinutes(staleOffsetMinutes)
        );
    }
}
