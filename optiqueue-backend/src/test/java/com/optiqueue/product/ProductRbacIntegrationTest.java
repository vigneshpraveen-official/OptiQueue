package com.optiqueue.product;

import com.optiqueue.testsupport.TestDatabaseConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String staffToken;
    private String customerToken;

    @BeforeAll
    void setUp() throws Exception {
        // admin + staff come from DemoUserBootstrap defaults
        adminToken = login("admin", "admin12345");
        staffToken = login("staff", "staff12345");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "buyer1", "password": "password123"}
                        """));
        customerToken = login("buyer1", "password123");
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("token").asText();
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "X-1", "name": "Blocked", "price": 10.00, "stockQuantity": 5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCreateProductButCanRestock() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "X-2", "name": "Blocked", "price": 10.00, "stockQuantity": 5}
                                """))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "RESTOCK-1", "name": "Widget", "price": 25.50, "stockQuantity": 10}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/products/" + id + "/restock")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 15}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }

    @Test
    void adminCreatesAndAnyAuthenticatedUserCanRead() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "READ-1", "name": "Gadget", "price": 99.99, "stockQuantity": 3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("READ-1"));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void duplicateSkuRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "DUP-1", "name": "First", "price": 5.00, "stockQuantity": 1}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "DUP-1", "name": "Second", "price": 6.00, "stockQuantity": 2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SKU_TAKEN"));
    }

    @Test
    void validationFailureReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "", "name": "", "price": -1, "stockQuantity": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
