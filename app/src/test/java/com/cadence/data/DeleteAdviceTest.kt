package com.cadence.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteAdviceTest {

    @Test
    fun withDataGivesExportAdvice() {
        assertEquals(
            "Export your data as CSV first. Cadence has no cloud storage, so deleting here cannot be undone.",
            deleteAdvice(hasRecordedData = true),
        )
    }

    @Test
    fun withoutDataGivesEmptyString() {
        assertEquals("", deleteAdvice(hasRecordedData = false))
    }
}
