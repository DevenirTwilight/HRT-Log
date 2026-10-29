package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test

class MedicalFileTitleTest {
    @Test fun displayNamesAreValidatedWithoutTruncatingOrInferringAPath() {
        assertTrue(MedicalAttachmentOperations.validTitle("2026年10月雌二醇化验报告.pdf"))
        assertTrue(MedicalAttachmentOperations.validTitle("x".repeat(256)))
        assertTrue(MedicalAttachmentOperations.validTitle("  A chosen name  "))
        for(title in listOf("", "   ", "\t", "bad\nname", "bad\u0000name", "bad\u007fname", "bad\u0085name", "x".repeat(257)))
            assertFalse(MedicalAttachmentOperations.validTitle(title))
    }
}
