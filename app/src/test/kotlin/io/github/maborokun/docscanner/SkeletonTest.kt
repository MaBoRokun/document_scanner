package io.github.maborokun.docscanner

import org.junit.Assert.assertTrue
import org.junit.Test

class SkeletonTest {
    @Test
    fun namespaceIsSet() {
        assertTrue("io.github.maborokun.docscanner".isNotBlank())
    }
}
