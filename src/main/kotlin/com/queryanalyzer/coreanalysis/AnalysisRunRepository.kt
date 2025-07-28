package com.queryanalyzer.coreanalysis

import com.queryanalyzer.coreanalysis.domain.AnalysisRun
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Simple repository interface - practical approach
 * Uses Spring Data JPA directly without over-abstraction
 */
@Repository
interface AnalysisRunRepository : JpaRepository<AnalysisRun, UUID>