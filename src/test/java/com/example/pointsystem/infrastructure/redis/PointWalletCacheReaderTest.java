package com.example.pointsystem.infrastructure.redis;

import com.example.pointsystem.domain.wallet.PointWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointWalletCacheReaderTest {

    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private PointWalletCacheReader reader;

    @BeforeEach
    void setUp() {
        reader = new PointWalletCacheReader(cacheManager, redissonClient);
        when(cacheManager.getCache(RedisCacheConfig.POINT_WALLET_CACHE)).thenReturn(cache);
    }

    @Test
    // 의도: 캐시 hit이면 DB loader를 호출하지 않는다.
    void getOrLoad_returns_cached_wallet_without_loading() {
        PointWallet cached = PointWallet.createWallet(1L);
        when(cache.get(1L, PointWallet.class)).thenReturn(cached);

        PointWallet result = reader.getOrLoad(1L, () -> {
            throw new AssertionError("loader should not be called");
        });

        assertSame(cached, result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    // 의도: 캐시 miss에서 락을 획득한 한 요청만 DB를 조회하고 캐시를 채운다.
    void getOrLoad_loads_and_populates_cache_when_lock_is_acquired() throws Exception {
        PointWallet loaded = PointWallet.createWallet(1L);
        when(cache.get(1L, PointWallet.class)).thenReturn(null, (PointWallet) null);
        when(redissonClient.getLock("lock:cache:pointWallet:1")).thenReturn(lock);
        when(lock.tryLock(150L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PointWallet result = reader.getOrLoad(1L, () -> loaded);

        assertSame(loaded, result);
        verify(cache).put(1L, loaded);
        verify(lock).unlock();
    }

    @Test
    // 의도: 락을 잡지 못한 요청은 짧게 재조회한 뒤 채워진 캐시 값을 반환한다.
    void getOrLoad_retries_cache_when_lock_is_held_by_another_instance() throws Exception {
        PointWallet rebuilt = PointWallet.createWallet(1L);
        when(cache.get(1L, PointWallet.class)).thenReturn(null, null, rebuilt);
        when(redissonClient.getLock("lock:cache:pointWallet:1")).thenReturn(lock);
        when(lock.tryLock(150L, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(false);

        AtomicInteger loadCount = new AtomicInteger();
        PointWallet result = reader.getOrLoad(1L, () -> {
            loadCount.incrementAndGet();
            return PointWallet.createWallet(99L);
        });

        assertSame(rebuilt, result);
        verify(cache, never()).put(eq(1L), any(PointWallet.class));
        verify(lock, never()).unlock();
        verifyNoMoreInteractions(lock);
        org.junit.jupiter.api.Assertions.assertEquals(0, loadCount.get());
    }
}
