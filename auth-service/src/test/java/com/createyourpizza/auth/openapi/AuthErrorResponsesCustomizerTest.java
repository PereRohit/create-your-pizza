package com.createyourpizza.auth.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * The customiser is what turns SpringDoc's bare {@code 200} into the real contract, so it
 * is exercised here against a stand-in for SpringDoc's output — no Docker export needed.
 */
class AuthErrorResponsesCustomizerTest {

	private OpenAPI openApi;

	@BeforeEach
	void buildSpringDocOutput() {
		Paths paths = new Paths();
		paths.addPathItem("/auth/login", new PathItem().post(springDocOperation(true)));
		paths.addPathItem("/auth/register", new PathItem().post(springDocOperation(true)));
		paths.addPathItem("/auth/token", new PathItem().post(springDocOperation(true)));
		paths.addPathItem("/auth/admins", new PathItem().post(springDocOperation(true)));
		paths.addPathItem("/auth/users", new PathItem().get(springDocOperation(false)));
		paths.addPathItem("/auth/users/{id}", new PathItem().delete(springDocOperation(false)));
		paths.addPathItem("/auth/users/{id}/approve", new PathItem().post(springDocOperation(false)));
		paths.addPathItem("/auth/users/{id}/deny", new PathItem().post(springDocOperation(false)));
		paths.addPathItem("/auth/users/{id}/revoke", new PathItem().post(springDocOperation(false)));
		paths.addPathItem("/auth/.well-known/jwks.json", new PathItem().get(springDocOperation(false)));

		// Components arrive with the unreferenced schemas already pruned — in particular without
		// ApiEnvelope. Seeding one here is what hid the dangling $refs in the published files.
		openApi = new OpenAPI().components(new Components()).paths(paths);

		new AuthErrorResponsesCustomizer().customise(openApi);
	}

	@Test
	void credentialRoutesDocumentRejectedCredentials() {
		assertThat(responses("POST /auth/login")).containsKeys("400", "401");
		assertThat(responses("POST /auth/token")).containsKeys("400", "401");
		assertThat(responses("POST /auth/login")).doesNotContainKeys("403", "404");
	}

	@Test
	void adminRoutesDocumentUnauthenticatedAndNonAdminCallers() {
		for (String adminOnly : new String[] {"POST /auth/admins", "GET /auth/users",
				"DELETE /auth/users/{id}", "POST /auth/users/{id}/approve",
				"POST /auth/users/{id}/deny", "POST /auth/users/{id}/revoke"}) {
			assertThat(responses(adminOnly)).describedAs(adminOnly).containsKeys("401", "403");
		}
	}

	@Test
	void userTargetedRoutesDocumentNotFound() {
		assertThat(responses("DELETE /auth/users/{id}")).containsKey("404");
		assertThat(responses("POST /auth/users/{id}/approve")).containsKey("404");
		assertThat(responses("GET /auth/users")).doesNotContainKey("404");
	}

	@Test
	void stateTransitionsDocumentBadRequestButDeleteDoesNot() {
		assertThat(responses("POST /auth/users/{id}/approve")).containsKey("400");
		assertThat(responses("POST /auth/users/{id}/revoke")).containsKey("400");
		assertThat(responses("DELETE /auth/users/{id}")).doesNotContainKey("400");
		assertThat(responses("GET /auth/users")).doesNotContainKey("400");
	}

	@Test
	void duplicateAdminUsernameIsDocumentedAsConflict() {
		assertThat(responses("POST /auth/admins")).containsKey("409");
		assertThat(responses("POST /auth/register")).doesNotContainKey("409");
	}

	@Test
	void registrationIsPublicAndReportsTwoOhOne() {
		assertThat(responses("POST /auth/register")).containsKey("201").doesNotContainKeys("200", "401", "403");
		assertThat(responses("POST /auth/admins")).containsKey("201").doesNotContainKey("200");
		assertThat(responses("POST /auth/users/{id}/approve")).containsKey("200").doesNotContainKey("201");
	}

	@Test
	void jsonOperationsAdvertiseJsonRatherThanAnyMediaType() {
		Content listed = responses("GET /auth/users").get("200").getContent();

		assertThat(listed).containsKey("application/json").doesNotContainKey("*/*");
	}

	@Test
	void jwksStaysAPlainPublicDocument() {
		ApiResponses jwks = responses("GET /auth/.well-known/jwks.json");

		assertThat(jwks).containsOnlyKeys("200");
	}

	@Test
	void everyReferencedErrorComponentExists() {
		assertThat(openApi.getComponents().getResponses())
				.containsOnlyKeys("BadRequest", "Unauthorized", "Forbidden", "NotFound", "Conflict");
		assertThat(openApi.getComponents().getResponses().get("Unauthorized")
				.getContent().get("application/json").getSchema().get$ref())
				.isEqualTo("#/components/schemas/ApiEnvelope");
	}

	@Test
	void theEnvelopeSchemaTheErrorResponsesReferenceIsContributedToo() {
		Schema<?> envelope = openApi.getComponents().getSchemas().get("ApiEnvelope");

		assertThat(envelope).describedAs("pruned before the customiser runs, so it must be re-added")
				.isNotNull();
		assertThat(envelope.getProperties())
				.containsOnlyKeys("status", "message", "error", "data", "pagination");
	}

	@Test
	void reapplyingLeavesTheSpecUnchanged() {
		String before = openApi.getPaths().toString();

		new AuthErrorResponsesCustomizer().customise(openApi);

		assertThat(openApi.getPaths()).hasToString(before);
	}

	private ApiResponses responses(String methodAndPath) {
		String[] parts = methodAndPath.split(" ", 2);
		PathItem item = openApi.getPaths().get(parts[1]);
		return item.readOperationsMap().get(PathItem.HttpMethod.valueOf(parts[0])).getResponses();
	}

	/** Mirrors what SpringDoc emits for an undocumented controller method. */
	private static Operation springDocOperation(boolean withBody) {
		Operation operation = new Operation().responses(new ApiResponses()
				.addApiResponse("200", new ApiResponse()
						.description("OK")
						.content(new Content().addMediaType("*/*", new MediaType()
								.schema(new Schema<>().$ref("#/components/schemas/ApiEnvelope"))))));
		return withBody ? operation.requestBody(new RequestBody()) : operation;
	}
}
