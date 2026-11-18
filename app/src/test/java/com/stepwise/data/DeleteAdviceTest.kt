package com.stepwise.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteAdviceTest {

    @Test
    fun withDataGivesExportAdvice() {
        assertEquals(
            "Export your data as CSV first. Stepwise has no cloud storage, so deleting here cannot be undone.",
            deleteAdvice(hasRecordedData = true),
        )
    }

    @Test
    fun withoutDataGivesEmptyString() {
        assertEquals("", deleteAdvice(hasRecordedData = false))
    }
}
