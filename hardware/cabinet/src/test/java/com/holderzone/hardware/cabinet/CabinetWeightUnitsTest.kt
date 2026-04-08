package com.holderzone.hardware.cabinet

import org.junit.Assert.assertEquals
import org.junit.Test

class CabinetWeightUnitsTest {

    @Test
    fun `kilogramsToGrams converts kilograms to grams`() {
        assertEquals(500.0, kilogramsToGrams(0.5), 0.0001)
        assertEquals(2000.0, kilogramsToGrams(2.0), 0.0001)
    }
}
