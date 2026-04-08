package com.holderzone.hardware.scale

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleWeightUnitsTest {

    @Test
    fun `kilogramsToGrams converts kilograms to grams`() {
        assertEquals(500.0, kilogramsToGrams(0.5), 0.0001)
        assertEquals(1250.0, kilogramsToGrams(1.25), 0.0001)
    }
}
