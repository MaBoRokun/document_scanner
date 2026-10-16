package io.github.maborokun.docscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfNamesTest {
    @Test
    fun fileNamesAreSafeAndNeverEmpty() {
        assertEquals("Blue Bakery 2026 10.pdf", pdfFileName("Blue Bakery: 2026/10"))
        assertEquals("Scan.pdf", pdfFileName("   "))
        assertEquals("Scan.pdf", pdfFileName("///"))
        assertEquals("Café Müller.pdf", pdfFileName("Café Müller"))
        assertEquals(60 + 4, pdfFileName("x".repeat(200)).length)
    }

    @Test
    fun pagesFitA4InTheirOwnOrientation() {
        assertEquals(595 to 842, pageSize(2100, 2970))
        assertEquals(842 to 595, pageSize(2970, 2100))
        // A tall receipt keeps its proportions and is limited by the page height.
        assertEquals(210 to 842, pageSize(300, 1203))
        // Tiny images are scaled up to the page rather than printed as postage stamps.
        assertEquals(595 to 595, pageSize(100, 100))
    }
}
