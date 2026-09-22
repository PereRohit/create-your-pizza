package com.createyourpizza.catalog.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisCatalogLockStoreTest {

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ValueOperations<String, String> values;

	@Test
	void tryAcquireSetsTheKeyOnlyIfAbsentAndWithTheGivenTtl() {
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(CatalogLockKeys.PDF_GENERATION, "job-1", Duration.ofSeconds(120)))
				.thenReturn(Boolean.TRUE);

		RedisCatalogLockStore store = new RedisCatalogLockStore(redis);

		assertThat(store.tryAcquire(CatalogLockKeys.PDF_GENERATION, "job-1", Duration.ofSeconds(120))).isTrue();
	}

	@Test
	void tryAcquireIsFalseWhenTheKeyIsAlreadyHeldOrTheReplyIsNull() {
		when(redis.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(CatalogLockKeys.CATALOG_WRITE, "writer", Duration.ofSeconds(30)))
				.thenReturn(Boolean.FALSE, (Boolean) null);

		RedisCatalogLockStore store = new RedisCatalogLockStore(redis);

		assertThat(store.tryAcquire(CatalogLockKeys.CATALOG_WRITE, "writer", Duration.ofSeconds(30))).isFalse();
		assertThat(store.tryAcquire(CatalogLockKeys.CATALOG_WRITE, "writer", Duration.ofSeconds(30))).isFalse();
	}

	@Test
	void isHeldReflectsKeyPresenceAndTreatsNullAsNotHeld() {
		when(redis.hasKey(CatalogLockKeys.PDF_GENERATION)).thenReturn(Boolean.TRUE, Boolean.FALSE, (Boolean) null);

		RedisCatalogLockStore store = new RedisCatalogLockStore(redis);

		assertThat(store.isHeld(CatalogLockKeys.PDF_GENERATION)).isTrue();
		assertThat(store.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
		assertThat(store.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
	}

	@Test
	void releaseDeletesTheKey() {
		RedisCatalogLockStore store = new RedisCatalogLockStore(redis);

		store.release(CatalogLockKeys.CATALOG_WRITE);

		verify(redis).delete(CatalogLockKeys.CATALOG_WRITE);
	}
}
