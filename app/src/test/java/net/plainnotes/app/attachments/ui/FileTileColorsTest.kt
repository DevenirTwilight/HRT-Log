package net.plainnotes.app.attachments.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import net.plainnotes.app.ui.Contrast
import net.plainnotes.app.ui.accentColors
import net.plainnotes.app.ui.notesColorScheme
import net.plainnotes.app.ui.semanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UI-13 (owner, 2026-10-30): a PDF tile is blue like the sample file library; a picture keeps its warm tile; unavailable and other files stay grey. */
class FileTileColorsTest {
    private fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun pdfIsBlueAndTheOthersKeepTheirMeaning() {
        for (dark in listOf(false, true)) for (contrast in Contrast.entries) {
            val scheme = notesColorScheme(dark, contrast); val sem = semanticColors(scheme, dark, contrast); val acc = accentColors(scheme, dark, contrast)
            val pdf = fileTileColors(MedicalAttachmentsController.FileKind.PDF, true, scheme, sem, acc)
            assertEquals(acc.blue.container to acc.blue.onContainer, pdf)
            assertEquals(sem.attention.container to sem.attention.onContainer, fileTileColors(MedicalAttachmentsController.FileKind.IMAGE, true, scheme, sem, acc))
            assertEquals(scheme.surfaceContainerHigh to scheme.onSurfaceVariant, fileTileColors(MedicalAttachmentsController.FileKind.OTHER, true, scheme, sem, acc))
            assertEquals(scheme.surfaceContainerHigh to scheme.outline, fileTileColors(MedicalAttachmentsController.FileKind.PDF, false, scheme, sem, acc))
            assertNotEquals("a PDF tile is no longer the plain grey", scheme.surfaceContainerHigh, pdf.first)
            for (kind in MedicalAttachmentsController.FileKind.entries) { val (bg, fg) = fileTileColors(kind, true, scheme, sem, acc); assertTrue("$kind dark=$dark $contrast ${ratio(bg, fg)}", ratio(bg, fg) >= 4.5f) }
        }
    }
}
