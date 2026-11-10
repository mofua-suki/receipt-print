package receiptdsl.engine

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewAlignmentRendererTest {
    @Test
    fun enginePreviewRetainsAllAlignmentsThroughThePinnedSvgRenderer() {
        val root = File(requireNotNull(System.getProperty("receipt.fixtures.dir"))).parentFile
        for (align in TextAlignDto.entries) {
            val preview = ReceiptEngine().preview(
                PrintJobDto(listOf(TicketDto(PrintNodeListDto(listOf(
                    TextDto("ABC", style = if (align == TextAlignDto.Left) null else TextStyleDto(align = align)),
                    QrCodeDto("A", cellSize = 6, align = align),
                )), cut = CutModeDto.None))),
                PrinterProfileDto(PrinterModelDto.EpsonTmM30),
            )
            assertTrue(preview.isSuccess, preview.diagnostics.toString())
            val process = ProcessBuilder("node", "tools/receiptline/verify-alignment.mjs", align.name)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { it.write(requireNotNull(preview.receiptLine)) }
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            assertTrue(finished, "ReceiptLine renderer timed out for $align")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.exitValue(), "$align: $output")
        }
    }
}
