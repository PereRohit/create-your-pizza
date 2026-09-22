package com.createyourpizza.catalog.menu;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class RedisLatestMenuStore implements LatestMenuStore {

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper = menuObjectMapper();

	public RedisLatestMenuStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public void put(LatestMenu menu) {
		try {
			redis.opsForValue().set(LatestMenuKeys.LATEST, objectMapper.writeValueAsString(menu));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to write latest menu Redis key", ex);
		}
	}

	private static ObjectMapper menuObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
