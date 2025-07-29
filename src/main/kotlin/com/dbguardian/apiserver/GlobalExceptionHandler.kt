package com.dbguardian.apiserver

import com.dbguardian.apiserver.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.map { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage ?: "Invalid value"
            "$fieldName: $errorMessage"
        }
        
        val errorResponse = ErrorResponse(
            error = "Validation failed",
            message = errors.joinToString(", "),
            status = HttpStatus.BAD_REQUEST.value(),
            timestamp = LocalDateTime.now()
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "Bad Request",
            message = ex.message ?: "Invalid argument provided",
            status = HttpStatus.BAD_REQUEST.value(),
            timestamp = LocalDateTime.now()
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupportedOperationException(ex: UnsupportedOperationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "Not Implemented",
            message = ex.message ?: "Feature not implemented",
            status = HttpStatus.NOT_IMPLEMENTED.value(),
            timestamp = LocalDateTime.now()
        )
        
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(errorResponse)
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            timestamp = LocalDateTime.now()
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}