package com.winstone.connect.location

import com.winstone.connect.data.Lead
import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyLeadLocatorTest {
    private fun lead(id: String, distanceKm: Double?) = Lead(
        id = id,
        name = id,
        phone = "",
        company = null,
        address = null,
        status = "pending",
        attempts = 0,
        notes = null,
        distanceKm = distanceKm,
    )

    @Test
    fun `resolved leads sort nearest first and unresolved leads remain last`() {
        val sorted = sortNearbyLeads(listOf(lead("unknown", null), lead("far", 8.2), lead("near", 1.1)))

        assertEquals(listOf("near", "far", "unknown"), sorted.map { it.id })
    }

    @Test
    fun `distance labels use metres below one kilometre`() {
        assertEquals("750 মিটার দূরে", 0.75.asDistanceLabel())
        assertEquals("1.3 কিমি দূরে", 1.25.asDistanceLabel())
    }
}