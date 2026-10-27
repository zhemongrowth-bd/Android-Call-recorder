package com.winstone.connect

import com.winstone.connect.data.LeadEvaluation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadEvaluationTest {
    @Test
    fun `all no is complete cold and zero`() {
        val answers = LeadEvaluation.questions.associate { it.key to false }
        assertTrue(LeadEvaluation.isComplete(answers))
        assertEquals(0, LeadEvaluation.score(answers))
        assertEquals("cold", LeadEvaluation.category(0))
    }

    @Test
    fun `locked score bands match web`() {
        assertEquals("cold", LeadEvaluation.category(20))
        assertEquals("warm", LeadEvaluation.category(21))
        assertEquals("warm", LeadEvaluation.category(50))
        assertEquals("hot", LeadEvaluation.category(51))
        assertEquals("hot", LeadEvaluation.category(90))
    }

    @Test
    fun `maximum score is ninety and partial answers are incomplete`() {
        val allYes = LeadEvaluation.questions.associate { it.key to true }
        assertEquals(LeadEvaluation.MAX_SCORE, LeadEvaluation.score(allYes))
        assertFalse(LeadEvaluation.isComplete(mapOf("project_interest" to true)))
    }
}