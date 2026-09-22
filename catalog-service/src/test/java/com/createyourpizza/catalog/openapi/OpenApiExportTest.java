package com.createyourpizza.catalog.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Docker-only OpenAPI export helper. Invoked by {@code ./scripts/generate-openapi.sh}
 * ({@code -Dopenapi.export=true}). Disabled during normal {@code mvn test}.
 */
@EnabledIfSystemProperty(named = "openapi.export", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("openapi-export")
@Import(OpenApiExportConfig.class)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiExportTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void exportStaticOpenApi() throws Exception {
		Path yaml = repoRoot().resolve("docs/openapi/catalog-service.yaml");
		Path json = repoRoot().resolve("docs/openapi/catalog-service.json");
		Files.createDirectories(yaml.getParent());

		String liveYaml = mockMvc.perform(get("/v3/api-docs.yaml"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		String liveJson = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		Files.writeString(yaml, normalize(liveYaml) + "\n", StandardCharsets.UTF_8);
		Files.writeString(json, normalize(liveJson) + "\n", StandardCharsets.UTF_8);
	}

	private static Path repoRoot() {
		return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
	}

	private static String normalize(String content) {
		return content.replace("\r\n", "\n").stripTrailing();
	}
}
