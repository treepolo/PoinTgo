package com.pointgo.capture.data.repository

import com.pointgo.capture.data.model.MeasurementSession
import com.pointgo.capture.data.model.SessionAnnotation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SessionRepository {
    val sessions: StateFlow<List<MeasurementSession>>

    suspend fun save(session: MeasurementSession)

    suspend fun addAnnotation(sessionId: String, annotation: SessionAnnotation)
}

/** In-memory repository keeps the first scaffold dependency-free and replaceable. */
class InMemorySessionRepository : SessionRepository {
    private val _sessions = MutableStateFlow<List<MeasurementSession>>(emptyList())
    override val sessions: StateFlow<List<MeasurementSession>> = _sessions.asStateFlow()

    override suspend fun save(session: MeasurementSession) {
        _sessions.update { current ->
            listOf(session) + current.filterNot { it.id == session.id }
        }
    }

    override suspend fun addAnnotation(sessionId: String, annotation: SessionAnnotation) {
        _sessions.update { current ->
            current.map { session ->
                if (session.id == sessionId) {
                    session.copy(annotations = session.annotations + annotation)
                } else {
                    session
                }
            }
        }
    }
}
