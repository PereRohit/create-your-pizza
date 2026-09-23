package com.createyourpizza.catalog.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.BinarySchema;
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
 * their signatures: the 503 busy contract on writes, the envelope errors the security
 * filter chain and {@code CatalogExceptionHandler} produce, and the raw-PDF media type on
 * the public menu endpoint (Design §9; Spec FR-21 / NFR-4 / NFR-12). It also carries the
 * {@code ApiEnvelope} schema those errors reference — see {@link #envelopeSchema()}.
 */
class CatalogErrorResponsesCustomizer implements OpenApiCustomizer {

	static final String BAD_REQUEST = "BadRequest";
	static final String UNAUTHORIZED = "Unauthorized";
	static final String FORBIDDEN = "Forbidden";
	static final String NOT_FOUND = "NotFound";
	static final String SERVICE_BUSY = "ServiceBusy";

	static final String MENU_PDF_PATH = "/api/menu.pdf";
	static final String PRODUCTS_PATH = "/api/products";

	static final String ENVELOPE = "ApiEnvelope";

	private static final String JSON = "application/json";
	private static final String PDF = "application/pdf";
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
				"Request body failed validation, or a catalog rule rejected it",
				400, "Validation failed"));
		components.addResponses(UNAUTHORIZED, envelope(
				"Missing, malformed, or expired JWT",
				401, "Unauthorized"));
		components.addResponses(FORBIDDEN, envelope(
				"Authenticated but not permitted — reads need scope catalog:read, writes need catalog:write and role ADMIN",
				403, "Forbidden"));
		components.addResponses(NOT_FOUND, envelope(
				"No such product, option, or menu version",
				404, "Product not found"));
		components.addResponses(SERVICE_BUSY, serviceBusy());
	}

	private static void declareResponses(String path, HttpMethod method, Operation operation) {
		ApiResponses responses = operation.getResponses();
		if (MENU_PDF_PATH.equals(path)) {
			responses.addApiResponse("200", menuPdfOk());
			responses.addApiResponse("404", ref(NOT_FOUND));
			return;
		}

		reportCreatedOnPost(method, responses);
		responses.values().forEach(CatalogErrorResponsesCustomizer::asJson);

		if (validatesInput(path, method)) {
			responses.addApiResponse("400", ref(BAD_REQUEST));
		}
		responses.addApiResponse("401", ref(UNAUTHORIZED));
		responses.addApiResponse("403", ref(FORBIDDEN));
		if (path.contains("{id}")) {
			responses.addApiResponse("404", ref(NOT_FOUND));
		}
		if (isWrite(method)) {
			responses.addApiResponse("503", ref(SERVICE_BUSY));
		}
	}

	/** Writes are the operations the PDF-generation lock rejects with 503 (Design §6). */
	private static boolean isWrite(HttpMethod method) {
		return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE;
	}

	/** Bodies are bean-validated; the product list rejects unknown {@code type} / {@code category}. */
	private static boolean validatesInput(String path, HttpMethod method) {
		return method == HttpMethod.POST
				|| method == HttpMethod.PUT
				|| (method == HttpMethod.GET && PRODUCTS_PATH.equals(path));
	}

	private static void reportCreatedOnPost(HttpMethod method, ApiResponses responses) {
		if (method != HttpMethod.POST) {
			return;
		}
		ApiResponse ok = responses.remove("200");
		if (ok != null) {
			responses.addApiResponse("201", ok.description("Created"));
		}
	}

	/** SpringDoc defaults an undeclared producer to {@code *\/*}; every JSON route returns the envelope. */
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

	private static ApiResponse menuPdfOk() {
		return new ApiResponse()
				.description("Menu PDF bytes — latest version, or the version named by the version query parameter")
				.content(new Content().addMediaType(PDF, new MediaType().schema(new BinarySchema())));
	}

	private static ApiResponse serviceBusy() {
		return new ApiResponse()
				.description("PDF generation lock held — retry after Retry-After seconds. "
						+ "The envelope omits data and pagination.")
				.addHeaderObject("Retry-After", new Header()
						.description("Seconds to wait before retry (always 60)")
						.schema(new IntegerSchema()._default(60)))
				.content(new Content().addMediaType(JSON, new MediaType()
						.schema(new Schema<>().$ref(ENVELOPE_REF))
						.example(example(503, "please try after sometime", "system busy"))));
	}

	private static ApiResponse envelope(String description, int status, String message) {
		return new ApiResponse()
				.description(description)
				.content(new Content().addMediaType(JSON, new MediaType()
						.schema(new Schema<>().$ref(ENVELOPE_REF))
						.example(example(status, message, message))));
	}

	private static String example(int status, String message, String error) {
		return "{\"status\":%d,\"message\":\"%s\",\"error\":\"%s\"}".formatted(status, message, error);
	}

	private static ApiResponse ref(String componentName) {
		return new ApiResponse().$ref(RESPONSE_REF + componentName);
	}
}
