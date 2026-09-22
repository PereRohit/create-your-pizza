package com.createyourpizza.catalog.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisLatestMenuStoreTest {

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ValueOperations<String, String> values;

	private RedisLatestMenuStore store;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(values);
		store = new RedisLatestMenuStore(redis);
	}

	@Test
	void putWritesJsonWithoutTtl() {
		byte[] pdf = new byte[] { 1, 2, 3 };
		store.put(new LatestMenu(pdf, 2, Instant.parse("2026-01-01T00:00:00Z")));

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(values).set(org.mockito.ArgumentMatchers.eq(LatestMenuKeys.LATEST), json.capture());
		assertThat(json.getValue()).contains("\"version\":2");
		assertThat(json.getValue()).contains("\"updatedAt\":\"2026-01-01T00:00:00Z\"");
		assertThat(json.getValue()).contains("\"pdf\":");
	}
}
