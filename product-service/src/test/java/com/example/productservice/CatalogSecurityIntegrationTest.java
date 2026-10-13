package com.example.productservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class CatalogSecurityIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void catalogReadsRemainPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void everyCatalogWriteRequiresAdmin() throws Exception {
        for (String resource : new String[]{"/api/products", "/api/categories"}) {
            for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                    post(resource), put(resource + "/test"), patch(resource + "/test"), delete(resource + "/test")}) {
                mvc.perform(request).andExpect(status().isUnauthorized());
                mvc.perform(request.header("Authorization", "Bearer " + TestTokens.token(1, "USER")))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void invalidExpiredAndRefreshTokensCannotWrite() throws Exception {
        String secret = "product-test-secret-at-least-32-bytes-long";
        for (String token : new String[]{"invalid",
                TestTokens.signed(Map.of("userId", 1, "role", "ADMIN"), -1000, secret),
                TestTokens.signed(Map.of(), 60000, secret),
                TestTokens.signed(Map.of("userId", 1, "role", "ADMIN"), 60000,
                        "untrusted-secret-at-least-32-bytes-long")}) {
            mvc.perform(post("/api/categories").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void signedAdminTokenCanCreateAndDeleteCategory() throws Exception {
        String token = "Bearer " + TestTokens.token(1, "ADMIN");
        String response = mvc.perform(post("/api/categories").header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"security-" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asText();
        mvc.perform(delete("/api/categories/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
    }
}
