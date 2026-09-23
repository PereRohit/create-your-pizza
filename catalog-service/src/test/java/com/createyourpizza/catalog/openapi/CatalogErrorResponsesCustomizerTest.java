package com.createyourpizza.catalog.openapi;

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
class CatalogErrorResponsesCustomizerTest {

	private OpenAPI openApi;

	@BeforeEach
	void buildSpringDocOutput() {
		Paths paths = new Paths();
		paths.addPathItem("/api/products", new PathItem()
				.get(springDocOperation(false))
				.post(springDocOperation(true)));
		paths.addPathItem("/api/products/{id}", new PathItem()
				.get(springDocOperation(false))
				.put(springDocOperation(true))
				.delete(springDocOperation(false)));
		paths.addPathItem("/api/options", new PathItem()
				.post(springDocOperation(true)));
		paths.addPathItem("/api/options/{id}", new PathItem()
				.get(springDocOperation(false))
				.put(springDocOperation(true))
				.delete(springDocOperation(false)));
		paths.addPathItem("/api/menu.pdf", new PathItem()
				.get(springDocOperation(false)));

		// Components arrive with the unreferenced schemas already pruned — in particular without
		// ApiEnvelope. Seeding one here is what hid the dangling $refs in the published files.
		openApi = new OpenAPI().components(new Components()).paths(paths);

		new CatalogErrorResponsesCustomizer().customise(openApi);
	}

	@Test
	void writesDocumentTheBusyContract() {
		for (String write : new String[] {"POST /api/products", "PUT /api/products/{id}",
				"DELETE /api/products/{id}", "POST /api/options", "PUT /api/options/{id}",
				"DELETE /api/options/{id}"}) {
			assertThat(responses(write).get("503"))
					.describedAs(write)
					.isNotNull()
					.extracting(ApiResponse::get$ref)
					.isEqualTo("#/components/responses/ServiceBusy");
		}
	}

	@Test
	void serviceBusyCarriesRetryAfterAndAnEnvelopeWithoutDataOrPagination() {
		ApiResponse busy = openApi.getComponents().getResponses().get("ServiceBusy");

		assertThat(busy.getHeaders()).containsKey("Retry-After");
		assertThat(busy.getHeaders().get("Retry-After").getSchema().getDefault()).isEqualTo(60);

		MediaType json = busy.getContent().get("application/json");
		assertThat(json.getSchema().get$ref()).isEqualTo("#/components/schemas/ApiEnvelope");
		assertThat((String) json.getExample())
				.contains("\"status\":503")
				.contains("please try after sometime")
				.contains("system busy")
				.doesNotContain("data")
				.doesNotContain("pagination");
	}

	@Test
	void readsAndWritesDocumentTheirAuthFailures() {
		for (String secured : new String[] {"GET /api/products", "GET /api/products/{id}",
				"POST /api/products", "PUT /api/products/{id}", "DELETE /api/products/{id}",
				"GET /api/options/{id}", "POST /api/options", "PUT /api/options/{id}",
				"DELETE /api/options/{id}"}) {
			assertThat(responses(secured))
					.describedAs(secured)
					.containsKeys("401", "403");
		}
	}

	@Test
	void byIdOperationsDocumentNotFound() {
		assertThat(responses("GET /api/products/{id}")).containsKey("404");
		assertThat(responses("GET /api/options/{id}")).containsKey("404");
		assertThat(responses("DELETE /api/options/{id}")).containsKey("404");
		assertThat(responses("GET /api/products")).doesNotContainKey("404");
	}

	@Test
	void onlyValidatingOperationsDocumentBadRequest() {
		assertThat(responses("POST /api/products")).containsKey("400");
		assertThat(responses("PUT /api/products/{id}")).containsKey("400");
		assertThat(responses("GET /api/products")).containsKey("400");
		assertThat(responses("DELETE /api/products/{id}")).doesNotContainKey("400");
		assertThat(responses("GET /api/products/{id}")).doesNotContainKey("400");
	}

	@Test
	void createsReportTwoOhOne() {
		assertThat(responses("POST /api/products")).containsKey("201").doesNotContainKey("200");
		assertThat(responses("POST /api/options")).containsKey("201").doesNotContainKey("200");
		assertThat(responses("PUT /api/products/{id}")).containsKey("200").doesNotContainKey("201");
	}

	@Test
	void jsonOperationsAdvertiseJsonRatherThanAnyMediaType() {
		Content created = responses("POST /api/products").get("201").getContent();

		assertThat(created).containsKey("application/json").doesNotContainKey("*/*");
	}

	@Test
	void menuPdfServesBinaryPdfAndNothingElse() {
		ApiResponses menu = responses("GET /api/menu.pdf");
		MediaType pdf = menu.get("200").getContent().get("application/pdf");

		assertThat(menu.get("200").getContent()).containsOnlyKeys("application/pdf");
		assertThat(pdf.getSchema().getType()).isEqualTo("string");
		assertThat(pdf.getSchema().getFormat()).isEqualTo("binary");
		assertThat(menu).containsKey("404");
		assertThat(menu).doesNotContainKeys("401", "403", "503");
	}

	@Test
	void everyReferencedErrorComponentExists() {
		assertThat(openApi.getComponents().getResponses())
				.containsOnlyKeys("BadRequest", "Unauthorized", "Forbidden", "NotFound", "ServiceBusy");
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

		new CatalogErrorResponsesCustomizer().customise(openApi);

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
