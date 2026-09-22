package com.createyourpizza.catalog.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.menu.RedisLatestMenuStore;
import com.createyourpizza.catalog.pdf.MenuPdfRenderer;
import com.createyourpizza.catalog.pdf.OpenPdfMenuPdfRenderer;

@Configuration
@EnableScheduling
public class CatalogPdfConfiguration {

	@Bean
	@ConfigurationProperties(prefix = "app.pdf")
	PdfProperties pdfProperties() {
		return new PdfProperties();
	}

	@Bean
	@Fallback
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	@Fallback
	MenuPdfRenderer openPdfMenuPdfRenderer() {
		return new OpenPdfMenuPdfRenderer();
	}

	/**
	 * Resolved at bean-creation time: {@code StringRedisTemplate} is contributed by
	 * auto-configuration, which runs after this application {@code @Configuration}.
	 */
	@Bean
	LatestMenuStore latestMenuStore(ObjectProvider<StringRedisTemplate> redis) {
		return redis.stream().findFirst()
				.<LatestMenuStore>map(RedisLatestMenuStore::new)
				.orElseGet(InMemoryLatestMenuStore::new);
	}
}
