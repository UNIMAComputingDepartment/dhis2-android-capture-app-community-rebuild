package org.dhis2.community.workflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowConditionEvaluatorTest {

    @Test
    fun `equals returns true when values match`() {
        assertTrue(evaluateWorkflowCondition("Female", "Female", "equals"))
    }

    @Test
    fun `between returns true when actual value is inside range`() {
        assertTrue(evaluateWorkflowCondition("3", "1,5", "between"))
    }

    @Test
    fun `greater_than returns false when actual is not numeric`() {
        assertFalse(evaluateWorkflowCondition("NA", "12", "greater_than"))
    }

    @Test
    fun `not_contains returns true when expected is missing`() {
        assertTrue(evaluateWorkflowCondition("Abc Def", "xyz", "not_contains"))
    }
}

