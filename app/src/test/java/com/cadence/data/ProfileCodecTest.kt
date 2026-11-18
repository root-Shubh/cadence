package com.cadence.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    private fun profile(
        id: String,
        name: String = "Me",
        stride: Double? = null,
    ) = ProfileState(
        heightCm = 172.5,
        weightKg = 68.25,
        age = 34,
        sex = "FEMALE",
        strideOverrideCm = stride,
        id = id,
        name = name,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val list = listOf(
            profile("default", name = "Me"),
            profile("3f2c-uuid", name = "Partner", stride = 71.5),
        )

        assertEquals(list, ProfileCodec.decode(ProfileCodec.encode(list)))
    }

    @Test
    fun namesWithCommasPipesQuotesAndNewlinesRoundTrip() {
        val tricky = "Ann, Bob | \"Q\"\nline two\r\\end"
        val list = listOf(profile("a", name = tricky), profile("b", name = "x,y|z"))

        val decoded = ProfileCodec.decode(ProfileCodec.encode(list))

        assertEquals(list, decoded)
        assertEquals(tricky, decoded[0].name)
    }

    @Test
    fun emptyListRoundTripsAndNullOrBlankDecodesToEmpty() {
        assertTrue(ProfileCodec.decode(ProfileCodec.encode(emptyList())).isEmpty())
        assertTrue(ProfileCodec.decode(null).isEmpty())
        assertTrue(ProfileCodec.decode("").isEmpty())
    }

    @Test
    fun nullStrideRoundTripsAsNull() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(listOf(profile("a", stride = null))))

        assertNull(decoded[0].strideOverrideCm)
    }

    @Test
    fun corruptInputReturnsEmptyListWithoutThrowing() {
        val corrupt = listOf(
            "not a profile list at all",
            "cadence-profiles-v1\na|Me|abc|70|30||",
            "cadence-profiles-v1\na|Me|170.0|70.0|30|MALE",
            "cadence-profiles-v1\na|Me|170.0|70.0|30|MALE|\\q",
            "cadence-profiles-v1\na|Me|170.0|70.0|30|MALE|\\",
            "cadence-profiles-v1\na|Me|170.0|70.0|30|MALE|\nb|Me|170.0|70.0|30|MALE",
            "cadence-profiles-v1\n|Me|170.0|70.0|30|MALE|",
            "cadence-profiles-v1\na|Me|170.0|70.0|30|MALE|\n\u0000garbage",
        )
        for (raw in corrupt) {
            assertTrue("should be empty for: $raw", ProfileCodec.decode(raw).isEmpty())
        }
    }

    @Test
    fun nextActiveKeepsCurrentWhenItSurvives() {
        val list = listOf(profile("a"), profile("b"), profile("c"))

        assertEquals("c", nextActiveId(list, removedId = "a", currentActive = "c"))
    }

    @Test
    fun nextActiveFallsBackToFirstRemainingWhenActiveDeleted() {
        val list = listOf(profile("a"), profile("b"), profile("c"))

        assertEquals("a", nextActiveId(list, removedId = "b", currentActive = "b"))
        assertEquals("b", nextActiveId(list, removedId = "a", currentActive = "a"))
    }

    @Test
    fun nextActiveIsNullWhenLastProfileDeleted() {
        assertNull(nextActiveId(listOf(profile("only")), removedId = "only", currentActive = "only"))
    }

    @Test
    fun nextActiveFallsBackWhenCurrentActiveIsUnknown() {
        val list = listOf(profile("a"), profile("b"))

        assertEquals("a", nextActiveId(list, removedId = "b", currentActive = null))
        assertEquals("a", nextActiveId(list, removedId = "b", currentActive = "ghost"))
    }

    @Test
    fun nameRules() {
        assertNull(profileNameError("A"))
        assertNull(profileNameError("  Work  "))
        assertNull(profileNameError("x".repeat(30)))
        assertTrue(profileNameError("").orEmpty().isNotEmpty())
        assertTrue(profileNameError("   ").orEmpty().isNotEmpty())
        assertTrue(profileNameError("x".repeat(31)).orEmpty().isNotEmpty())
    }

    @Test
    fun defaultNamesCountUpAndSkipUsedNames() {
        assertEquals("Me", defaultProfileName(emptyList()))
        assertEquals("Profile 2", defaultProfileName(listOf(profile("a", name = "Me"))))
        assertEquals(
            "Profile 3",
            defaultProfileName(listOf(profile("a", name = "Me"), profile("b", name = "Profile 2"))),
        )
        assertEquals(
            "Profile 4",
            defaultProfileName(listOf(profile("a", name = "Me"), profile("b", name = "Profile 3"))),
        )
    }
}
