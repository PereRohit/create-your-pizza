package com.createyourpizza.auth.security;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.createyourpizza.auth.config.JwtProperties;
import com.createyourpizza.auth.jwt.SigningKeyService;
import com.createyourpizza.auth.web.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final SigningKeyService signingKeyService;
	private final JwtProperties jwtProperties;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/auth/admins") && !path.startsWith("/auth/users");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			writeUnauthorized(response, "Missing or invalid Authorization header");
			return;
		}

		String token = header.substring("Bearer ".length()).trim();
		if (token.isEmpty()) {
			writeUnauthorized(response, "Missing or invalid Authorization header");
			return;
		}

		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!jwt.verify(new RSASSAVerifier(signingKeyService.getSigningKey().toRSAPublicKey()))) {
				writeUnauthorized(response, "Invalid token");
				return;
			}

			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
				writeUnauthorized(response, "Invalid token");
				return;
			}
			if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.getAudience())) {
				writeUnauthorized(response, "Invalid token");
				return;
			}
			Date exp = claims.getExpirationTime();
			if (exp == null || exp.before(new Date())) {
				writeUnauthorized(response, "Invalid token");
				return;
			}

			String subject = claims.getSubject();
			if (subject == null || subject.isBlank()) {
				writeUnauthorized(response, "Invalid token");
				return;
			}

			List<String> roles = claims.getStringListClaim("roles");
			if (roles == null) {
				roles = List.of();
			}
			Collection<SimpleGrantedAuthority> authorities = roles.stream()
					.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
					.collect(Collectors.toList());

			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(subject, null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		}
		catch (ParseException | JOSEException e) {
			writeUnauthorized(response, "Invalid token");
		}
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		SecurityContextHolder.clearContext();
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		OBJECT_MAPPER.writeValue(response.getOutputStream(), ApiEnvelope.error(401, message, message));
	}
}
