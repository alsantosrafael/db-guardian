package com.example.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface UserRepository : JpaRepository<User, UUID> {
    
    // This should trigger SELECT * warning
    @Query("SELECT * FROM users WHERE active = true")
    fun findActiveUsers(): List<User>
    
    // This should trigger missing WHERE warning  
    @Query("UPDATE users SET last_login = NOW()")
    fun updateAllLastLogin()
    
    // This should be flagged as dangerous DELETE
    @Query("DELETE FROM user_sessions")
    fun clearAllSessions()
    
    // This is a good query - should not trigger warnings
    @Query("SELECT id, name, email FROM users WHERE created_at > :date AND active = true")
    fun findRecentActiveUsers(@Param("date") date: String): List<User>
    
    // JDBC style query that should be detected
    fun findUserByIdUnsafe(userId: String): User? {
        val connection = getConnection()
        val stmt = connection.prepareStatement("SELECT * FROM users WHERE id = '$userId'")
        return stmt.executeQuery()
    }
}