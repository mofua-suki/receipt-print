package receiptdsl.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewAlignmentRendererTest {
    @Test
    fun enginePreviewMatchesTheRendererFixturesForAllAlignments() {
        val fixtures = File(requireNotNull(System.getProperty("receipt.fixtures.dir")), "previews")
        for (align in TextAlignDto.entries) {
            val preview = ReceiptEngine().preview(
                PrintJobDto(listOf(TicketDto(PrintNodeListDto(listOf(
                    TextDto("ABC", style = if (align == TextAlignDto.Left) null else TextStyleDto(align = align)),
                    QrCodeDto("A", cellSize = 6, align = align),
                )), cut = CutModeDto.None))),
                PrinterProfileDto(PrinterModelDto.EpsonTmM30),
            )
            assertTrue(preview.isSuccess, preview.diagnostics.toString())
            val expected = File(fixtures, "alignment-${align.name.lowercase()}.receiptline")
                .readText()
                .replace("\r\n", "\n")
                .removeSuffix("\n")
            assertEquals(expected, preview.receiptLine, align.name)
        }
    }
}
