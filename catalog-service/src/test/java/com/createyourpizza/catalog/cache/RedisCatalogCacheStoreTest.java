package com.createyourpizza.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisCatalogCacheStoreTest {

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ValueOperations<String, String> values;

	private RedisCatalogCacheStore store;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(values);
		store = new RedisCatalogCacheStore(redis);
	}

	@Test
	void getReturnsEmptyOnRedisMiss() {
		when(values.get("create-your-pizza/catalog:product:1")).thenReturn(null);

		assertThat(store.get("create-your-pizza/catalog:product:1")).isEmpty();
	}

	@Test
	void getAndPutDelegateToRedisWithTtl() {
		when(values.get("create-your-pizza/catalog:list:|| |1|10")).thenReturn("{\"items\":[]}");

		Optional<String> hit = store.get("create-your-pizza/catalog:list:|| |1|10");
		store.put("create-your-pizza/catalog:list:|| |1|10", "{\"items\":[]}", Duration.ofMinutes(3));

		assertThat(hit).contains("{\"items\":[]}");
		verify(values).set("create-your-pizza/catalog:list:|| |1|10", "{\"items\":[]}", Duration.ofMinutes(3));
	}
}
