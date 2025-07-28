package com.queryanalyzer.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.core.context.SecurityContextHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class ApiTokenAuthenticationFilterTest {

    @Mock
    private lateinit var request: HttpServletRequest

    @Mock
    private lateinit var response: HttpServletResponse

    @Mock
    private lateinit var filterChain: FilterChain

    private lateinit var filter: ApiTokenAuthenticationFilter

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        filter = ApiTokenAuthenticationFilter("valid-token-123")
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should set authentication when valid token provided`() {
        `when`(request.getHeader("x-api-token")).thenReturn("valid-token-123")

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals("api-client", authentication.name)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `should not set authentication when invalid token provided`() {
        `when`(request.getHeader("x-api-token")).thenReturn("invalid-token")

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNull(authentication)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `should not set authentication when no token provided`() {
        `when`(request.getHeader("x-api-token")).thenReturn(null)

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNull(authentication)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `should not set authentication when empty token provided`() {
        `when`(request.getHeader("x-api-token")).thenReturn("")

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNull(authentication)
        verify(filterChain).doFilter(request, response)
    }
}