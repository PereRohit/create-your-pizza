package com.createyourpizza.catalog.openapi;

import static com.createyourpizza.catalog.openapi.PublishedSpec.asMap;
import static com.createyourpizza.catalog.openapi.PublishedSpec.child;
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

	private static final List<String> WRITES = List.of(
			"POST /api/products",
			"PUT /api/products/{id}",
			"DELETE /api/products/{id}",
			"POST /api/options",
			"PUT /api/options/{id}",
			"DELETE /api/options/{id}");

	private static final List<String> SECURED = List.of(
			"GET /api/products",
			"GET /api/products/{id}",
			"GET /api/options/{id}",
			"POST /api/products",
			"PUT /api/products/{id}",
			"DELETE /api/products/{id}",
			"POST /api/options",
			"PUT /api/options/{id}",
			"DELETE /api/options/{id}");

	private final PublishedSpec spec = PublishedSpec.json("catalog-service.json");

	@Test
	void everyWritePublishesTheBusyResponse() {
		for (String write : WRITES) {
			Map<String, Object> busy = response(write, "503");

			assertThat(child(busy, "headers")).describedAs(write).containsKey("Retry-After");
			assertThat(child(child(busy, "content"), "application/json")).describedAs(write)
					.containsKey("schema");
		}
	}

	@Test
	void theBusyEnvelopeOmitsDataAndPagination() {
		Map<String, Object> json = child(child(response("POST /api/products", "503"), "content"),
				"application/json");

		assertThat(child(json, "schema")).containsEntry("$ref", ENVELOPE_REF);
		assertThat(child(spec.resolve(ENVELOPE_REF), "properties"))
				.containsOnlyKeys("status", "message", "error", "data", "pagination");
		assertThat((String) json.get("example"))
				.contains("\"status\":503")
				.contains("please try after sometime")
				.contains("system busy")
				.doesNotContain("data")
				.doesNotContain("pagination");
	}

	@Test
	void retryAfterIsPublishedAsSixtySeconds() {
		Map<String, Object> retryAfter = child(child(response("PUT /api/options/{id}", "503"), "headers"),
				"Retry-After");

		assertThat(child(retryAfter, "schema")).containsEntry("default", 60);
	}

	@Test
	void securedOperationsPublishTheirAuthFailures() {
		for (String secured : SECURED) {
			assertThat(spec.responses(secured.split(" ")[0], secured.split(" ")[1]))
					.describedAs(secured)
					.containsKeys("401", "403");
		}
	}

	@Test
	void operationsAddressingOneEntityPublishNotFound() {
		for (String byId : List.of("GET /api/products/{id}", "GET /api/options/{id}",
				"PUT /api/products/{id}", "DELETE /api/options/{id}")) {
			assertThat(spec.responses(byId.split(" ")[0], byId.split(" ")[1]))
					.describedAs(byId)
					.containsKey("404");
		}
	}

	@Test
	void theMenuIsPublishedAsBinaryPdf() {
		Map<String, Object> content = child(response("GET /api/menu.pdf", "200"), "content");

		assertThat(content).containsOnlyKeys("application/pdf");
		assertThat(child(child(content, "application/pdf"), "schema"))
				.containsEntry("type", "string")
				.containsEntry("format", "binary");
	}

	@Test
	void theMenuKeepsItsVersionParameterAndUnknownVersionResponse() {
		List<?> parameters = (List<?>) spec.operation("GET", "/api/menu.pdf").get("parameters");

		assertThat(parameters).anySatisfy(parameter -> assertThat(asMap(parameter))
				.containsEntry("name", "version")
				.containsEntry("in", "query"));
		assertThat(spec.responses("GET", "/api/menu.pdf")).containsKey("404");
	}

	@Test
	void productAndOptionCreationPublishTwoOhOne() {
		assertThat(spec.responses("POST", "/api/products")).containsKey("201").doesNotContainKey("200");
		assertThat(spec.responses("POST", "/api/options")).containsKey("201").doesNotContainKey("200");
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
		spec.responseCodesByOperation().forEach((operation, codes) -> codes.forEach(code -> assertThat(
				response(operation, code)).describedAs(operation + " " + code).isNotEmpty()));
	}

	/**
	 * Response-component refs are only some of the refs an importer has to follow. This walks
	 * both committed files end to end — schemas, headers, parameters, request bodies — because
	 * a schema the exporter pruned leaves a response that parses but cannot be dereferenced.
	 */
	@Test
	void everyReferenceInTheCommittedFilesResolves() {
		assertThat(spec.danglingReferences()).describedAs("catalog-service.json").isEmpty();
		assertThat(PublishedSpec.yaml("catalog-service.yaml").danglingReferences())
				.describedAs("catalog-service.yaml").isEmpty();
	}

	@Test
	void theYamlAndJsonPublishTheSameOperations() {
		assertThat(PublishedSpec.yaml("catalog-service.yaml").responseCodesByOperation())
				.isEqualTo(spec.responseCodesByOperation());
	}

	private Map<String, Object> response(String methodAndPath, String code) {
		String[] parts = methodAndPath.split(" ", 2);
		return spec.response(parts[0], parts[1], code);
	}
}
