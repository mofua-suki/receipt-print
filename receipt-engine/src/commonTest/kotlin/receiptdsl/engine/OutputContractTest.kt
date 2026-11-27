package receiptdsl.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import receiptdsl.layout.DefaultLayoutEngine
import receiptdsl.layout.LaidOutQrCode
import receiptdsl.layout.LaidOutTextLine
import receiptdsl.layout.SetStyle
import receiptdsl.layout.TextBytes

public class OutputContractTest {
    private val engine = ReceiptEngine()
    private fun job(vararg nodes: PrintNodeDto, cut: CutModeDto = CutModeDto.None): PrintJobDto =
        PrintJobDto(listOf(TicketDto(PrintNodeListDto(nodes.toList()), cut)))

    @Test
    public fun rejectsControlsInBothModesForAllModelsAndTextPositions() {
        val controls = (0..31).toList() + (127..159).toList()
        for (model in PrinterModelDto.entries) for (mode in CompatibilityModeDto.entries) {
            val printer = PrinterProfileDto(model, compatibilityMode = mode)
            for (code in controls) {
                val text = "A${code.toChar()}@B"
                val nodes = listOf(
                    TextDto(text),
                    TwoColumnDto(TextRunDto(text)),
                    TwoColumnDto(TextRunDto("ok"), TextRunDto(text)),
                )
                nodes.forEachIndexed { index, node ->
                    if (index == 0 && (code == 10 || code == 13)) return@forEachIndexed
                    val input = job(ColumnDto(PrintNodeListDto(listOf(node))))
                    val compiled = engine.compile(input, printer)
                    val preview = engine.preview(input, printer)
                    assertNull(compiled.bytes, "$model $mode U+$code position $index")
                    assertNull(preview.receiptLine)
                    assertEquals(listOf(0, 0), compiled.diagnostics.single().childIndexes)
                    assertEquals(compiled.diagnostics, preview.diagnostics)
                }
            }
        }
    }

    @Test
    public fun textBlockNewlinesAreLayoutAndQrControlsRemainPayload() {
        val printer = PrinterProfileDto(PrinterModelDto.EpsonTmM30)
        val input = job(TextDto("A\r\nB\nC"))
        assertTrue(engine.compile(input, printer).isSuccess)
        assertEquals("{text:nowrap}\nA |\nB |\nC |\n{text:wrap}", engine.preview(input, printer).receiptLine)
        assertTrue(engine.compile(job(QrCodeDto("A\u001b@B")), printer).isSuccess)
        assertNull(engine.preview(job(QrCodeDto("A\u001b@B")), printer).receiptLine)
    }

    @Test
    public fun qrPayloadIsNeverDroppedInCompatibilityMode() {
        for (model in PrinterModelDto.entries) {
            val printer = PrinterProfileDto(model, compatibilityMode = CompatibilityModeDto.Compatibility)
            val compiled = engine.compile(job(QrCodeDto("A🙂B", cellSize = 3)), printer)
            val preview = engine.preview(job(QrCodeDto("A🙂B", cellSize = 3)), printer)
            assertNull(compiled.bytes)
            assertNull(preview.receiptLine)
            assertTrue(compiled.diagnostics.any { it.code == "UNSUPPORTED_GLYPH" && it.severity == DiagnosticSeverityDto.Error })
        }
        val printer = PrinterProfileDto(PrinterModelDto.EpsonTmM30)
        assertTrue(engine.compile(job(QrCodeDto("日本語")), printer).isSuccess)
        assertNull(engine.preview(job(QrCodeDto("日本語")), printer).receiptLine)
    }

    @Test
    public fun previewChecksByteCapacityAtTheEffectiveQrCorrectionLevel() {
        val epson = PrinterProfileDto(PrinterModelDto.EpsonTmM30)
        val capacities = mapOf(
            QrErrorCorrectionDto.Low to 2953,
            QrErrorCorrectionDto.Medium to 2331,
            QrErrorCorrectionDto.Quartile to 1663,
            QrErrorCorrectionDto.High to 1273,
        )
        fun checkBoundary(printer: PrinterProfileDto, requested: QrErrorCorrectionDto, maxBytes: Int) {
            // The final space expands in ReceiptLine markup but is one byte of QR data.
            val qr = QrCodeDto("a".repeat(maxBytes - 1) + " ", errorCorrection = requested)
            assertTrue(engine.preview(job(qr), printer).isSuccess)
            val tooLong = job(TextDto("Order"), qr.copy(data = qr.data + "a"))
            val preview = engine.preview(tooLong, printer)
            assertNull(preview.receiptLine)
            val error = preview.diagnostics.single { it.severity == DiagnosticSeverityDto.Error }
            assertEquals("INVALID_LAYOUT", error.code)
            assertEquals(0, error.ticketIndex)
            assertEquals(listOf(1), error.childIndexes)
            assertTrue(engine.compile(tooLong, printer).isSuccess)
        }
        for ((level, limit) in capacities) checkBoundary(epson, level, limit)
        val compatible = epson.copy(compatibilityMode = CompatibilityModeDto.Compatibility)
        checkBoundary(compatible, QrErrorCorrectionDto.High, 2331)
        checkBoundary(compatible.copy(model = PrinterModelDto.StarMcPrint3), QrErrorCorrectionDto.Low, 1663)
    }

    @Test
    public fun uncutTicketBoundaryPreservesOnlyExplicitFeed() {
        for (printer in listOf(PrinterModelDto.EpsonTmM30, PrinterModelDto.StarMcPrint3)) {
            val profile = PrinterProfileDto(printer)
            for (feed in 0..1) {
                val first = if (feed == 0) job(TextDto("A")) else job(TextDto("A"), FeedDto())
                val input = PrintJobDto(first.tickets + job(TextDto("B")).tickets)
                val expected = "{text:nowrap}\nA |\n" + "\n".repeat(feed) + "B |\n{text:wrap}"
                assertEquals(expected, engine.preview(input, profile).receiptLine)
                val bytes = assertNotNull(engine.compile(input, profile).bytes)
                assertEquals(2 + feed, bytes.count { it == 0x0a.toByte() })
            }
        }
    }

    @Test
    public fun compatibilityQrOptionsAreResolvedAndDiagnosedBeforeOutput() {
        for (model in PrinterModelDto.entries) {
            val printer = PrinterProfileDto(model, compatibilityMode = CompatibilityModeDto.Compatibility)
            val input = job(QrCodeDto("AB", 3, QrErrorCorrectionDto.High))
            val layout = DefaultLayoutEngine().layout(input.toPrintJob(), printer.toPrinterProfile())
            val qr = assertNotNull(layout.job).tickets.single().blocks.single() as LaidOutQrCode
            val compiled = engine.compile(input, printer)
            val preview = engine.preview(input, printer)
            assertTrue(compiled.isSuccess, compiled.diagnostics.toString())
            assertTrue(preview.isSuccess)
            assertEquals(6, qr.cellSize)
            val star = model in listOf(PrinterModelDto.StarFvp10, PrinterModelDto.StarTsp650II, PrinterModelDto.StarMcPrint3)
            val level = if (star) "q" else "m"
            assertEquals("{text:nowrap}\n| {code:AB; option:qrcode,6,$level} |\n{text:wrap}", preview.receiptLine)
            assertEquals(compiled.diagnostics, preview.diagnostics)
            assertEquals(1, compiled.diagnostics.count { it.code == "VALUE_SUBSTITUTED" })
            val bytes = compiled.bytes!!.map { it.toInt() and 255 }
            val ecCommand = if (star) listOf(27, 29, 121, 83, 49, 2) else listOf(29, 40, 107, 3, 0, 49, 69, 49)
            assertTrue(bytes.windowed(ecCommand.size).contains(ecCommand))
        }
    }

    @Test
    public fun tmM30RejectsFullCutOrResolvesItToPartialWithWarning() {
        val input = job(TextDto("A"), cut = CutModeDto.Full)
        val strict = PrinterProfileDto(PrinterModelDto.EpsonTmM30)
        assertNull(engine.compile(input, strict).bytes)
        assertNull(engine.preview(input, strict).receiptLine)
        val compatible = strict.copy(compatibilityMode = CompatibilityModeDto.Compatibility)
        val output = engine.compile(input, compatible)
        assertTrue(output.isSuccess)
        assertEquals(listOf(29, 86, 66, 0), output.bytes!!.map { it.toInt() and 255 }.takeLast(4))
        assertTrue(output.diagnostics.any { it.code == "VALUE_SUBSTITUTED" && it.message.contains("Full to Partial") })
    }

    @Test
    public fun previewUsesWrappedFilteredTextAndResolvedStylesIncludingExplicitReset() {
        val input = job(ColumnDto(
            PrintNodeListDto(listOf(TextDto("A🙂BCD"), TextDto("reset", TextStyleDto()))),
            TextStyleDto(widthScale = 2),
        ))
        val printer = PrinterProfileDto(PrinterModelDto.EpsonTmM30, columnsPerLine = 4, compatibilityMode = CompatibilityModeDto.Compatibility)
        val layout = DefaultLayoutEngine().layout(input.toPrintJob(), printer.toPrinterProfile())
        val lines = layout.job!!.tickets.single().blocks.filterIsInstance<LaidOutTextLine>()
        assertEquals(listOf("AB", "CD", "rese", "t"), lines.map { line -> line.operations.filterIsInstance<TextBytes>().joinToString("") { it.source } })
        assertEquals(listOf(2, 2, 1, 1), lines.map { it.operations.filterIsInstance<SetStyle>().first().style.widthScale })
        assertEquals("{text:nowrap}\n^AB |\n^CD |\nrese |\nt |\n{text:wrap}", engine.preview(input, printer).receiptLine)
    }
    @Test
    public fun missingOrEmptyTrailingColumnStillHonorsEveryOverflowPolicy() {
        for (mode in CompatibilityModeDto.entries) {
            val printer = PrinterProfileDto(PrinterModelDto.EpsonTmM30, compatibilityMode = mode)
            for (trailing in listOf(null, TextRunDto(""))) {
                for (overflow in TwoColumnOverflowDto.entries) {
                    for (text in listOf("ABCD", "ABCDE")) {
                        val input = job(TwoColumnDto(
                            leading = TextRunDto(text), trailing = trailing,
                            widthColumns = 4, overflow = overflow,
                        ))
                        val compiled = engine.compile(input, printer)
                        val preview = engine.preview(input, printer)
                        val rejected = overflow == TwoColumnOverflowDto.Clip ||
                            (overflow == TwoColumnOverflowDto.Fail && text.length > 4)
                        val context = "$mode / $trailing / $overflow / $text"
                        if (rejected) {
                            assertNull(compiled.bytes, context)
                            assertNull(preview.receiptLine, context)
                            assertEquals("INVALID_LAYOUT", compiled.diagnostics.single().code, context)
                            assertEquals(listOf(0), compiled.diagnostics.single().childIndexes, context)
                            assertEquals(compiled.diagnostics, preview.diagnostics, context)
                        } else {
                            assertTrue(compiled.isSuccess, context)
                            assertTrue(compiled.diagnostics.isEmpty(), context)
                            val layout = DefaultLayoutEngine().layout(input.toPrintJob(), printer.toPrinterProfile())
                            val contents = layout.job!!.tickets.single().blocks.filterIsInstance<LaidOutTextLine>()
                                .map { line -> line.operations.filterIsInstance<TextBytes>().joinToString("") { it.source } }
                            assertEquals(if (text.length == 4) listOf("ABCD") else listOf("ABCD", "E"), contents, context)
                            assertTrue(preview.isSuccess, context)
                        }
                    }
                }
            }
        }
    }

}
