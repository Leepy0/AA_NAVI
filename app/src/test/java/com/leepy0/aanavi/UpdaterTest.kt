package com.leepy0.aanavi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {
    @Test
    fun codeOf() {
        assertEquals(100, Updater.codeOf("0.1.0"))
        assertEquals(10203, Updater.codeOf("v1.2.3"))
        assertNull(Updater.codeOf("0.0.0"))
        assertNull(Updater.codeOf("1.100.0"))
        assertNull(Updater.codeOf("1.2"))
        assertNull(Updater.codeOf("0.1.0-spike"))
    }

    @Test
    fun normalizeSha256() {
        val hex = "a".repeat(64)
        assertEquals(hex, Updater.normalizeSha256(hex.uppercase()))
        assertEquals(hex, Updater.normalizeSha256("$hex  AA_NAVI.apk\n"))
        assertNull(Updater.normalizeSha256("abc"))
        assertNull(Updater.normalizeSha256("g".repeat(64)))
        assertNull(Updater.normalizeSha256(null))
    }
}
