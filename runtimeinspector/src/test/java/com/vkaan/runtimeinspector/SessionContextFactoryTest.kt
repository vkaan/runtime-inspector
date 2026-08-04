package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.report.SessionContextFactory.deviceName
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionContextFactoryTest {

    @Test
    fun `joins the maker and the model`() {
        assertEquals("PAX A920", deviceName("PAX", "A920"))
    }

    @Test
    fun `does not repeat a maker the model already carries`() {
        assertEquals("Google Pixel 3", deviceName("Google", "Google Pixel 3"))
    }

    @Test
    fun `reports unknown rather than an empty string`() {
        assertEquals("unknown", deviceName(null, null))
    }
}
