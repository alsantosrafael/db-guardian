package com.queryanalyzer.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "app.internal.token=test-token-123",
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint should be accessible without authentication`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `protected endpoints should return 401 without token`() {
        mockMvc.perform(get("/api/analyze"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoints should return 401 with invalid token`() {
        mockMvc.perform(
            get("/api/analyze")
                .header("x-api-token", "invalid-token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoints should allow access with valid token`() {
        mockMvc.perform(
            post("/api/analyze")
                .header("x-api-token", "test-token-123")
                .contentType("application/json")
                .content("""
                    {
                        "mode": "STATIC",
                        "source": "test",
                        "config": {
                            "dialect": "POSTGRESQL"
                        }
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `should reject requests with empty token`() {
        mockMvc.perform(
            get("/api/analyze")
                .header("x-api-token", "")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should reject requests with missing token header`() {
        mockMvc.perform(post("/api/analyze"))
            .andExpect(status().isUnauthorized)
    }
}