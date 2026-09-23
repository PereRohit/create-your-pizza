package com.createyourpizza.auth.openapi;

import static com.createyourpizza.auth.openapi.PublishedSpec.asMap;
import static com.createyourpizza.auth.openapi.PublishedSpec.child;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Guards the committed contract itself — the files under {@code docs/openapi} are the only
 * thing integrators import (Design §9), so what ships is asserted here rather than what the
 * exporter would produce. Absence of this check is how BUG-02 shipped with a ticked
 * acceptance criterion.
 */
class StaticOpenApiContractTest {

	private static final String ENVELOPE_REF = "#/components/schemas/ApiEnvelope";

	private static final List<String> ADMIN_ONLY = List.of(
			"POST /auth/admins",
			"GET /auth/users",
			"DELETE /auth/users/{id}",
			"POST /auth/users/{id}/approve",
			"POST /auth/users/{id}/deny",
			"POST /auth/users/{id}/revoke");

	private final PublishedSpec spec = PublishedSpec.json("auth-service.json");

	@Test
	void adminRoutesPublishUnauthenticatedAndNonAdminOutcomes() {
		for (String adminOnly : ADMIN_ONLY) {
			assertThat(spec.responses(adminOnly.split(" ")[0], adminOnly.split(" ")[1]))
					.describedAs(adminOnly)
					.containsKeys("401", "403");
		}
	}

	@Test
	void credentialRoutesPublishRejectedCredentials() {
		assertThat(spec.responses("POST", "/auth/login")).containsKey("401");
		assertThat(spec.responses("POST", "/auth/token")).containsKey("401");
	}

	@Test
	void routesAddressingOneUserPublishNotFound() {
		for (String byId : List.of("DELETE /auth/users/{id}", "POST /auth/users/{id}/approve",
				"POST /auth/users/{id}/deny", "POST /auth/users/{id}/revoke")) {
			assertThat(spec.responses(byId.split(" ")[0], byId.split(" ")[1]))
					.describedAs(byId)
					.containsKey("404");
		}
	}

	@Test
	void theUnauthorizedResponseCarriesTheEnvelope() {
		Map<String, Object> json = child(child(spec.response("GET", "/auth/users", "401"), "content"),
				"application/json");

		assertThat(child(json, "schema")).containsEntry("$ref", ENVELOPE_REF);
		assertThat(child(spec.resolve(ENVELOPE_REF), "properties"))
				.containsOnlyKeys("status", "message", "error", "data", "pagination");
		assertThat((String) json.get("example")).contains("\"status\":401");
	}

	@Test
	void jwksStaysAPublicDocumentWithNoErrorResponses() {
		assertThat(spec.responses("GET", "/auth/.well-known/jwks.json")).containsOnlyKeys("200");
	}

	@Test
	void registrationAndAdminCreationPublishTwoOhOne() {
		assertThat(spec.responses("POST", "/auth/register")).containsKey("201").doesNotContainKey("200");
		assertThat(spec.responses("POST", "/auth/admins")).containsKey("201").doesNotContainKey("200");
	}

	@Test
	void noJsonOperationIsPublishedAsAWildcardMediaType() {
		spec.responseCodesByOperation().keySet().forEach(operation -> {
			String method = operation.split(" ")[0];
			String path = operation.split(" ")[1];
			spec.responses(method, path).forEach((code, response) -> {
				Object content = spec.dereference(asMap(response)).get("content");
				if (content != null) {
					assertThat(asMap(content)).describedAs(operation + " " + code)
							.doesNotContainKey("*/*");
				}
			});
		});
	}

	@Test
	void everyResponseReferenceResolves() {
		spec.responseCodesByOperation().forEach((operation, codes) -> codes.forEach(code -> {
			String[] parts = operation.split(" ", 2);
			assertThat(spec.response(parts[0], parts[1], code))
					.describedAs(operation + " " + code)
					.isNotEmpty();
		}));
	}

	/**
	 * Response-component refs are only some of the refs an importer has to follow. This walks
	 * both committed files end to end — schemas, headers, parameters, request bodies — because
	 * a schema the exporter pruned leaves a response that parses but cannot be dereferenced.
	 */
	@Test
	void everyReferenceInTheCommittedFilesResolves() {
		assertThat(spec.danglingReferences()).describedAs("auth-service.json").isEmpty();
		assertThat(PublishedSpec.yaml("auth-service.yaml").danglingReferences())
				.describedAs("auth-service.yaml").isEmpty();
	}

	@Test
	void theYamlAndJsonPublishTheSameOperations() {
		assertThat(PublishedSpec.yaml("auth-service.yaml").responseCodesByOperation())
				.isEqualTo(spec.responseCodesByOperation());
	}
}
