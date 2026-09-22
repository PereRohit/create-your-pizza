package com.createyourpizza.catalog.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import com.createyourpizza.catalog.web.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/menu.pdf")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/**")
						.hasAuthority("SCOPE_catalog:read")
						.requestMatchers(HttpMethod.POST, "/api/**")
						.hasAuthority("SCOPE_catalog:write")
						.requestMatchers(HttpMethod.PUT, "/api/**")
						.hasAuthority("SCOPE_catalog:write")
						.requestMatchers(HttpMethod.PATCH, "/api/**")
						.hasAuthority("SCOPE_catalog:write")
						.requestMatchers(HttpMethod.DELETE, "/api/**")
						.hasAuthority("SCOPE_catalog:write")
						.anyRequest()
						.permitAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint((request, response, authException) ->
								writeEnvelope(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeEnvelope(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden")))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) ->
								writeEnvelope(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeEnvelope(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden")));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
			JwtProperties jwtProperties) {
		CachingRemoteJwkSource jwkSource = new CachingRemoteJwkSource(jwkSetUri, RestClient.create());
		if (jwtProperties.isJwksWarmup()) {
			jwkSource.load();
		}
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
		OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer());
		OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
				JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(jwtProperties.getAudience()));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
		return decoder;
	}

	private static void writeEnvelope(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		OBJECT_MAPPER.writeValue(response.getOutputStream(), ApiEnvelope.error(status, message, message));
	}
}
