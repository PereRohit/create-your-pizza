package com.createyourpizza.catalog.openapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** OpenAPI info/components for Docker export ({@code ./scripts/generate-openapi.sh}). */
@TestConfiguration
@Profile("openapi-export")
class OpenApiExportConfig {

	static final String BEARER_JWT = "bearerJwt";
	static final String RESPONSE_SERVICE_BUSY = "ServiceBusy";

	@Bean
	OpenAPI catalogOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("CreateYourPizza Catalog API")
						.version("1.0")
						.description("""
								Catalog-service API for CreateYourPizza.

								**JSON envelope** (JSON responses): `status`, `message`, `error`, `data`, \
								and optionally `pagination` on list endpoints.

								**Pagination** (`GET /api/products` only): default page size **10**, \
								max **100**; `pagination.current`, `pagination.next` (**-1** when no next page), \
								`pagination.total`. Options are get-by-id only (no list/pagination).

								**503 Service Busy:** while the Redis PDF-generation lock is held, catalog \
								**writes** return HTTP **503** with envelope \
								`message=please try after sometime`, `error=system busy`, and header \
								`Retry-After: 60`. See response component `ServiceBusy`.

								**Public PDF:** `GET /api/menu.pdf` returns **raw PDF binary** (not JSON). \
								Omit `version` for latest (Redis); `?version=N` loads history from the catalog DB. \
								Unknown / missing version → JSON **404** envelope.

								**JWT:** verify via JWKS at auth-service (`AUTH_JWKS_URL`). \
								Reads need `catalog:read`; writes need `catalog:write` **and** `ADMIN` role.
								"""))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
				.components(new Components()
						.addSecuritySchemes(BEARER_JWT, new SecurityScheme()
								.name(BEARER_JWT)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("JWT from auth-service (admin login or trusted /auth/token)"))
						.addSchemas("ApiEnvelope", envelopeSchema())
						.addSchemas("Pagination", paginationSchema())
						.addResponses(RESPONSE_SERVICE_BUSY, serviceBusyResponse()));
	}

	private static Schema<?> envelopeSchema() {
		return new ObjectSchema()
				.description("Standard JSON response envelope")
				.addProperty("status", new IntegerSchema().description("HTTP status code echoed in body"))
				.addProperty("message", new StringSchema().description("Human-readable message; success → \"success\""))
				.addProperty("error", new StringSchema().description("Error detail; empty string on success"))
				.addProperty("data", new ObjectSchema().description("Payload; null on pure errors"))
				.addProperty("pagination", new Schema<>().$ref("#/components/schemas/Pagination")
						.description("Present only on list JSON responses"));
	}

	private static Schema<?> paginationSchema() {
		return new ObjectSchema()
				.description("List pagination metadata")
				.addProperty("current", new IntegerSchema().description("Current page (0-based)"))
				.addProperty("next", new IntegerSchema().description("Next page index, or -1 if none"))
				.addProperty("total", new IntegerSchema().format("int64").description("Total matching items"));
	}

	private static ApiResponse serviceBusyResponse() {
		return new ApiResponse()
				.description("PDF generation lock held — try again after Retry-After seconds")
				.addHeaderObject("Retry-After", new Header()
						.description("Seconds to wait before retry (always 60)")
						.schema(new IntegerSchema()._default(60)))
				.content(new Content().addMediaType("application/json", new MediaType()
						.schema(new Schema<>().$ref("#/components/schemas/ApiEnvelope"))
						.example("""
								{"status":503,"message":"please try after sometime","error":"system busy"}
								""")));
	}
}
