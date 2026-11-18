package tech.youssefachraf.image_to_pdf.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfIntentActionsTest {

    @Test
    fun formatFileSize_formatsBytesKilobytesAndMegabytes() {
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("4 kB", formatFileSize(4_200L))
        assertEquals("2.5 MB", formatFileSize(2_500_000L))
    }

    @Test
    fun formatPdfDate_formatsTimestampDeterministically() {
        val formatted = formatPdfDate(1_763_400_000_000L)
        assertTrue(formatted.matches(Regex("\\d{2}/\\d{2} \\d{2}:\\d{2}")))
    }
}
