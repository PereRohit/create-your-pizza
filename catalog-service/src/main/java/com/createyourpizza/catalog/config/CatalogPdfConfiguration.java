package com.createyourpizza.catalog.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	@ConditionalOnMissingBean(MenuPdfRenderer.class)
	MenuPdfRenderer openPdfMenuPdfRenderer() {
		return new OpenPdfMenuPdfRenderer();
	}

	@Bean
	@ConditionalOnBean(StringRedisTemplate.class)
	LatestMenuStore redisLatestMenuStore(StringRedisTemplate redis) {
		return new RedisLatestMenuStore(redis);
	}

	@Bean
	@ConditionalOnMissingBean(LatestMenuStore.class)
	LatestMenuStore inMemoryLatestMenuStore() {
		return new InMemoryLatestMenuStore();
	}
}
