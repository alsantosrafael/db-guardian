package com.dbguardian.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    locations = ["classpath:application-test.yml"],
    properties = ["app.internal.token=integration-test-token-456"]
)
class ApiAuthenticationIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun createUrl(path: String) = "http://localhost:$port$path"

    @Test
    fun `health endpoint should be accessible without authentication`() {
        val response = restTemplate.getForEntity(
            createUrl("/actuator/health"), 
            String::class.java
        )
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("UP"))
    }

    @Test
    fun `protected endpoint should return 401 without token`() {
        val response = restTemplate.getForEntity(
            createUrl("/api/analyze"), 
            String::class.java
        )
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `protected endpoint should return 401 with invalid token`() {
        val headers = HttpHeaders()
        headers.set("x-api-token", "invalid-token")
        val entity = HttpEntity<String>(headers)
        
        val response = restTemplate.exchange(
            createUrl("/api/analyze"),
            HttpMethod.GET,
            entity,
            String::class.java
        )
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `protected endpoint should allow access with valid token`() {
        val headers = HttpHeaders()
        headers.set("x-api-token", "integration-test-token-456")
        headers.set("Content-Type", "application/json")
        
        val requestBody = """
            {
                "mode": "STATIC",
                "source": "test",
                "config": {
                    "dialect": "POSTGRESQL"
                }
            }
        """.trimIndent()
        
        val entity = HttpEntity(requestBody, headers)
        
        val response = restTemplate.exchange(
            createUrl("/api/analyze"),
            HttpMethod.POST,
            entity,
            String::class.java
        )
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        // The response should contain analysis results
        assertTrue(response.body!!.isNotEmpty())
        println("✅ API authentication working - Response: ${response.body}")
    }

    @Test
    fun `should reject requests with empty token header`() {
        val headers = HttpHeaders()
        headers.set("x-api-token", "")
        val entity = HttpEntity<String>(headers)
        
        val response = restTemplate.exchange(
            createUrl("/api/analyze"),
            HttpMethod.GET,
            entity,
            String::class.java
        )
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `should handle different HTTP methods with authentication`() {
        val headers = HttpHeaders()
        headers.set("x-api-token", "integration-test-token-456")
        val entity = HttpEntity<String>(headers)
        
        // Test GET with valid token
        val getResponse = restTemplate.exchange(
            createUrl("/api/analyze"),
            HttpMethod.GET,
            entity,
            String::class.java
        )
        
        // Should not be 401 (might be 400 for missing body, but not unauthorized)
        assertTrue(getResponse.statusCode != HttpStatus.UNAUTHORIZED)
        
        // Test OPTIONS with valid token (if supported)
        val optionsResponse = restTemplate.exchange(
            createUrl("/api/analyze"),
            HttpMethod.OPTIONS,
            entity,
            String::class.java
        )
        
        // Should not be 401
        assertTrue(optionsResponse.statusCode != HttpStatus.UNAUTHORIZED)
    }
}