package com.createyourpizza.auth.openapi;

import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Declares the responses the controllers really return, which SpringDoc cannot infer from
 * their signatures: the envelope errors raised by {@code AuthExceptionHandler} and the
 * security filter chain (Design §9; Spec FR-21 / NFR-4). It also carries the
 * {@code ApiEnvelope} schema those errors reference — see {@link #envelopeSchema()}.
 */
class AuthErrorResponsesCustomizer implements OpenApiCustomizer {

	static final String BAD_REQUEST = "BadRequest";
	static final String UNAUTHORIZED = "Unauthorized";
	static final String FORBIDDEN = "Forbidden";
	static final String NOT_FOUND = "NotFound";
	static final String CONFLICT = "Conflict";

	static final String JWKS_PATH = "/auth/.well-known/jwks.json";
	static final String ADMINS_PATH = "/auth/admins";
	static final String USERS_PATH = "/auth/users";

	/** Public routes that reject bad credentials with 401 rather than an authentication challenge. */
	private static final Set<String> CREDENTIAL_PATHS = Set.of("/auth/login", "/auth/token");

	/** Routes whose successful POST creates a resource and answers 201. */
	private static final Set<String> CREATED_PATHS = Set.of("/auth/register", ADMINS_PATH);

	static final String ENVELOPE = "ApiEnvelope";

	private static final String JSON = "application/json";
	private static final String ANY_MEDIA_TYPE = "*/*";
	private static final String SCHEMA_REF = "#/components/schemas/";
	private static final String ENVELOPE_REF = SCHEMA_REF + ENVELOPE;
	private static final String PAGINATION_REF = SCHEMA_REF + "Pagination";
	private static final String RESPONSE_REF = "#/components/responses/";

	@Override
	public void customise(OpenAPI openApi) {
		addErrorComponents(openApi.getComponents());
		openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
				.forEach((method, operation) -> declareResponses(path, method, operation)));
	}

	private static void addErrorComponents(Components components) {
		components.addSchemas(ENVELOPE, envelopeSchema());
		components.addResponses(BAD_REQUEST, envelope(
				"Request body failed validation, or the user is not in a state that allows this transition",
				400, "Validation failed"));
		components.addResponses(UNAUTHORIZED, envelope(
				"Invalid credentials, or a missing / malformed / expired admin JWT",
				401, "Unauthorized"));
		components.addResponses(FORBIDDEN, envelope(
				"Authenticated but not an admin, or an admin attempting to delete their own account",
				403, "Forbidden"));
		components.addResponses(NOT_FOUND, envelope(
				"No such user or trusted-system credentials",
				404, "User not found"));
		components.addResponses(CONFLICT, envelope(
				"Username already exists",
				409, "Username already exists"));
	}

	private static void declareResponses(String path, HttpMethod method, Operation operation) {
		if (JWKS_PATH.equals(path)) {
			return;
		}

		ApiResponses responses = operation.getResponses();
		reportCreatedOnPost(path, method, responses);
		responses.values().forEach(AuthErrorResponsesCustomizer::asJson);

		boolean adminOnly = path.startsWith(ADMINS_PATH) || path.startsWith(USERS_PATH);
		boolean userTargeted = path.contains("{id}");

		if (operation.getRequestBody() != null || (userTargeted && method == HttpMethod.POST)) {
			responses.addApiResponse("400", ref(BAD_REQUEST));
		}
		if (adminOnly || CREDENTIAL_PATHS.contains(path)) {
			responses.addApiResponse("401", ref(UNAUTHORIZED));
		}
		if (adminOnly) {
			responses.addApiResponse("403", ref(FORBIDDEN));
		}
		if (userTargeted) {
			responses.addApiResponse("404", ref(NOT_FOUND));
		}
		if (ADMINS_PATH.equals(path)) {
			responses.addApiResponse("409", ref(CONFLICT));
		}
	}

	private static void reportCreatedOnPost(String path, HttpMethod method, ApiResponses responses) {
		if (method != HttpMethod.POST || !CREATED_PATHS.contains(path)) {
			return;
		}
		ApiResponse ok = responses.remove("200");
		if (ok != null) {
			responses.addApiResponse("201", ok.description("Created"));
		}
	}

	/** SpringDoc defaults an undeclared producer to {@code *\/*}; every auth route returns the envelope. */
	private static void asJson(ApiResponse response) {
		Content content = response.getContent();
		if (content == null) {
			return;
		}
		MediaType anyType = content.remove(ANY_MEDIA_TYPE);
		if (anyType != null) {
			content.addMediaType(JSON, anyType);
		}
	}

	/**
	 * Swagger's {@code removeBrokenReferenceDefinitions} drops component schemas nothing
	 * references yet, and it runs <em>before</em> customisers — so an envelope declared on the
	 * {@code OpenAPI} bean is pruned and every response below ships a dangling {@code $ref}.
	 * The schema is therefore registered here, alongside its only referrers. {@code Pagination}
	 * stays on the bean: SpringDoc's generated {@code ApiEnvelope*} schemas reference it, so it
	 * survives the prune.
	 */
	private static Schema<?> envelopeSchema() {
		return new ObjectSchema()
				.description("Standard JSON response envelope")
				.addProperty("status", new IntegerSchema().description("HTTP status code echoed in body"))
				.addProperty("message", new StringSchema().description("Human-readable message; success → \"success\""))
				.addProperty("error", new StringSchema().description("Error detail; empty string on success"))
				.addProperty("data", new ObjectSchema().description("Payload; null on pure errors"))
				.addProperty("pagination", new Schema<>().$ref(PAGINATION_REF)
						.description("Present only on list JSON responses"));
	}

	private static ApiResponse envelope(String description, int status, String message) {
		return new ApiResponse()
				.description(description)
				.content(new Content().addMediaType(JSON, new MediaType()
						.schema(new Schema<>().$ref(ENVELOPE_REF))
						.example("{\"status\":%d,\"message\":\"%s\",\"error\":\"%s\"}"
								.formatted(status, message, message))));
	}

	private static ApiResponse ref(String componentName) {
		return new ApiResponse().$ref(RESPONSE_REF + componentName);
	}
}
