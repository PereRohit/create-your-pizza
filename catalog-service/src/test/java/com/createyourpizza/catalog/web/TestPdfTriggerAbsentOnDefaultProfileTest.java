package com.createyourpizza.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TestPdfTriggerAbsentOnDefaultProfileTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void postIsNotMappedWithoutTestOrDevProfile() throws Exception {
		mockMvc.perform(post("/test/pdf/generate"))
				.andExpect(status().isNotFound());
	}
}
