package com.createyourpizza.auth.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** OpenAPI info/components for Docker export ({@code ./scripts/generate-openapi.sh}). */
@TestConfiguration
@Profile("openapi-export")
class OpenApiExportConfig {

	static final String BEARER_ADMIN_JWT = "bearerAdminJwt";

	@Bean
	OpenApiCustomizer authErrorResponses() {
		return new AuthErrorResponsesCustomizer();
	}

	@Bean
	OpenAPI authOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("CreateYourPizza Auth API")
						.version("1.0")
						.description("""
								Auth-service API for CreateYourPizza.

								**JSON envelope** (all JSON responses): `status`, `message`, `error`, \
								`data`, and optionally `pagination` on list endpoints. \
								Get-by-id and mutations omit `pagination`.

								**Pagination** (lists such as `GET /auth/users`): default page size **10**, \
								max **100**; `pagination.current`, `pagination.next` (**-1** when no next page), \
								`pagination.total`.

								**Admin JWT:** obtain via `POST /auth/login` (or create further admins via \
								`POST /auth/admins`). Use `Authorization: Bearer <token>` on admin routes.

								**Trusted systems:** `POST /auth/register` → PENDING → admin \
								`POST /auth/users/{id}/approve` (API key + secret once) → `POST /auth/token`. \
								`POST /auth/users/{id}/revoke` blocks further tokens.

								**JWKS:** `GET /auth/.well-known/jwks.json` — public keys for local JWT verify \
								(catalog never calls `/auth/validate`).
								"""))
				.components(new Components()
						.addSecuritySchemes(BEARER_ADMIN_JWT, new SecurityScheme()
								.name(BEARER_ADMIN_JWT)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Admin JWT from POST /auth/login"))
						.addSchemas("Pagination", paginationSchema()));
	}

	// ApiEnvelope was declared here; AuthErrorResponsesCustomizer registers it instead,
	// because a schema declared on the bean above is pruned before customisers run.

	private static Schema<?> paginationSchema() {
		return new ObjectSchema()
				.description("List pagination metadata")
				.addProperty("current", new IntegerSchema().description("Current page (0-based)"))
				.addProperty("next", new IntegerSchema().description("Next page index, or -1 if none"))
				.addProperty("total", new IntegerSchema().format("int64").description("Total matching items"));
	}
}
