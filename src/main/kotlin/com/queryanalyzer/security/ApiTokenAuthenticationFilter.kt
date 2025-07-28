package com.queryanalyzer.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class ApiTokenAuthenticationFilter(
    @Value("\${app.internal.token:}") private val validToken: String
) : OncePerRequestFilter() {
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader("x-api-token")
        
        if (token != null && token.isNotEmpty() && isValidToken(token)) {
            val authentication = UsernamePasswordAuthenticationToken(
                "api-client", 
                null, 
                emptyList()
            )
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }
    
    private fun isValidToken(providedToken: String): Boolean {
        if (validToken.isEmpty()) {
            return false
        }
        
        val providedBytes = providedToken.toByteArray(Charsets.UTF_8)
        val validBytes = validToken.toByteArray(Charsets.UTF_8)
        
        return MessageDigest.isEqual(providedBytes, validBytes)
    }
}