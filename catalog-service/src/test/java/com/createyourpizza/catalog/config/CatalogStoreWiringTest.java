package com.createyourpizza.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.createyourpizza.catalog.cache.CatalogCacheStore;
import com.createyourpizza.catalog.cache.InMemoryCatalogCacheStore;
import com.createyourpizza.catalog.cache.RedisCatalogCacheStore;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.lock.InMemoryCatalogLockStore;
import com.createyourpizza.catalog.lock.RedisCatalogLockStore;
import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.menu.RedisLatestMenuStore;
import com.createyourpizza.catalog.pdf.MenuPdfRenderer;
import com.createyourpizza.catalog.pdf.OpenPdfMenuPdfRenderer;

/**
 * Regression guard for BUG-01: the Redis-vs-in-memory selection must not depend on
 * whether {@code StringRedisTemplate} is registered before or after these configuration
 * classes. Redis auto-configuration is applied here on purpose — Lettuce connects
 * lazily, so asserting bean types needs no live Redis server.
 */
class CatalogStoreWiringTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(CatalogCacheConfiguration.class, CatalogLockConfiguration.class,
					CatalogPdfConfiguration.class);

	@Test
	void redisImplementationsAreSelectedWhenRedisAutoConfigurationContributesTheTemplate() {
		runner.withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
				.run(context -> {
					assertThat(context).hasSingleBean(StringRedisTemplate.class);
					assertThat(context).hasSingleBean(CatalogCacheStore.class);
					assertThat(context).hasSingleBean(CatalogLockStore.class);
					assertThat(context).hasSingleBean(LatestMenuStore.class);
					assertThat(context.getBean(CatalogCacheStore.class)).isInstanceOf(RedisCatalogCacheStore.class);
					assertThat(context.getBean(CatalogLockStore.class)).isInstanceOf(RedisCatalogLockStore.class);
					assertThat(context.getBean(LatestMenuStore.class)).isInstanceOf(RedisLatestMenuStore.class);
				});
	}

	@Test
	void inMemoryImplementationsAreSelectedWithoutAStringRedisTemplate() {
		runner.run(context -> {
			assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
			assertThat(context).hasSingleBean(CatalogCacheStore.class);
			assertThat(context).hasSingleBean(CatalogLockStore.class);
			assertThat(context).hasSingleBean(LatestMenuStore.class);
			assertThat(context.getBean(CatalogCacheStore.class)).isInstanceOf(InMemoryCatalogCacheStore.class);
			assertThat(context.getBean(CatalogLockStore.class)).isInstanceOf(InMemoryCatalogLockStore.class);
			assertThat(context.getBean(LatestMenuStore.class)).isInstanceOf(InMemoryLatestMenuStore.class);
		});
	}

	@Test
	void clockAndRendererDefaultsAreUsedWhenNothingElseContributesThem() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(Clock.class);
			assertThat(context.getBean(MenuPdfRenderer.class)).isInstanceOf(OpenPdfMenuPdfRenderer.class);
		});
	}

	@Test
	void clockAndRendererDefaultsLoseToLaterContributionsWhateverTheRegistrationOrder() {
		runner.withUserConfiguration(OverridingDefaultsConfiguration.class).run(context -> {
			Injected injected = context.getBean(Injected.class);
			assertThat(injected.clock()).isSameAs(OverridingDefaultsConfiguration.FIXED_CLOCK);
			assertThat(injected.renderer()).isSameAs(OverridingDefaultsConfiguration.RENDERER);
		});
	}

	record Injected(Clock clock, MenuPdfRenderer renderer) {
	}

	@Configuration(proxyBeanMethods = false)
	static class OverridingDefaultsConfiguration {

		static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

		static final MenuPdfRenderer RENDERER = model -> new byte[0];

		@Bean
		Clock fixedClock() {
			return FIXED_CLOCK;
		}

		@Bean
		MenuPdfRenderer stubMenuPdfRenderer() {
			return RENDERER;
		}

		@Bean
		Injected injected(Clock clock, MenuPdfRenderer renderer) {
			return new Injected(clock, renderer);
		}
	}
}
