package com.optiqueue.auth;

import com.optiqueue.testsupport.TestDatabaseConfig;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void register_returnsTokenAndCustomerRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "password": "password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @Order(2)
    void register_duplicateUsername_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "password": "password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    @Order(3)
    void register_adminRole_isRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "eve", "password": "password123", "role": "ADMIN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ROLE_NOT_ALLOWED"));
    }

    @Test
    @Order(4)
    void login_correctPassword_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    @Order(5)
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"));
    }

    @Test
    @Order(6)
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }
}
