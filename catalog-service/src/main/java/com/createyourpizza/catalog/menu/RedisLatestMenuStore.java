package com.createyourpizza.catalog.menu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

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

	@Override
	public Optional<LatestMenu> get() {
		String json = redis.opsForValue().get(LatestMenuKeys.LATEST);
		if (json == null || json.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, LatestMenu.class));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read latest menu Redis key", ex);
		}
	}

	private static ObjectMapper menuObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
