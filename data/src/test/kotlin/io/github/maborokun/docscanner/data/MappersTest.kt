package io.github.maborokun.docscanner.data

import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun quadRoundTripsThroughJson() {
        val quad = Quad(Point(0.1f, 0.2f), Point(0.9f, 0.1f), Point(0.95f, 0.8f), Point(0.05f, 0.9f))
        assertEquals(quad, quadFromJson(quad.toJson()))
    }

    @Test
    fun malformedQuadJsonFallsBackToFullImage() {
        assertEquals(Quad.FULL, quadFromJson(""))
        assertEquals(Quad.FULL, quadFromJson("[1,2,3]"))
        assertEquals(Quad.FULL, quadFromJson("[a,b,c,d,e,f,g,h]"))
    }

    @Test
    fun ftsQueryQuotesWordsAndStripsQuotes() {
        assertEquals("\"milk*\" \"bread*\"", ftsQuery("  milk   bread "))
        assertEquals("\"evil*\"", ftsQuery("\"evil\""))
        assertNull(ftsQuery("   "))
        assertNull(ftsQuery("\"\""))
    }

    @Test
    fun likePatternEscapesWildcards() {
        assertEquals("%50\\% off\\_x\\\\%", likePattern("50% off_x\\"))
    }
}
