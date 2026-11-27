package receiptdsl.receiptline

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import receiptdsl.diagnostic.CompilationIssue
import receiptdsl.diagnostic.Diagnostic
import receiptdsl.diagnostic.DiagnosticSeverity
import receiptdsl.diagnostic.InvalidLayout
import receiptdsl.diagnostic.NodePath
import receiptdsl.layout.DefaultLayoutEngine
import receiptdsl.layout.LaidOutBlock
import receiptdsl.layout.LaidOutTextLine
import receiptdsl.layout.LaidOutQrCode
import receiptdsl.layout.LaidOutRule
import receiptdsl.layout.LaidOutFeed
import receiptdsl.layout.SetStyle
import receiptdsl.layout.TextBytes
import receiptdsl.model.CommandSet
import receiptdsl.model.CompatibilityMode
import receiptdsl.model.CutMode
import receiptdsl.model.PrintDirection
import receiptdsl.model.PrintJob
import receiptdsl.model.PrinterCapabilities
import receiptdsl.model.PrinterProfile
import receiptdsl.model.QrErrorCorrection
import receiptdsl.model.ReverseMode
import receiptdsl.model.SoundMode
import receiptdsl.model.TextAlign
import receiptdsl.model.TextStyle

public interface ReceiptLineExporter {
    public fun export(job: PrintJob, profile: PreviewProfile = PreviewProfile()): ReceiptLineResult

    public fun export(job: PrintJob, profile: PrinterProfile): ReceiptLineResult
}

public data class PreviewProfile(
    val columnsPerLine: Int = 48,
)

public data class ReceiptLineResult(
    val markdown: String?,
    val diagnostics: PersistentList<Diagnostic>,
) {
    public val isSuccess: Boolean
        get() = markdown != null && diagnostics.none { it.severity == DiagnosticSeverity.Error }
}

public data class PreviewApproximation(
    val feature: String,
    override val path: NodePath?,
    override val code: String = "PREVIEW_APPROXIMATION",
    override val severity: DiagnosticSeverity = DiagnosticSeverity.Warning,
    override val message: String = "$feature is approximated in ReceiptLine preview",
) : CompilationIssue

public object DefaultReceiptLineExporter : ReceiptLineExporter {
    override fun export(job: PrintJob, profile: PreviewProfile): ReceiptLineResult {
        if (profile.columnsPerLine <= 0) {
            return ReceiptLineResult(
                markdown = null,
                diagnostics = listOf<Diagnostic>(
                    InvalidLayout(
                        reason = "PreviewProfile.columnsPerLine must be greater than zero",
                        path = null,
                    ),
                ).toPersistentList(),
            )
        }

        return export(job, previewPrinterProfile(profile))
    }

    override fun export(job: PrintJob, profile: PrinterProfile): ReceiptLineResult {
        val layout = DefaultLayoutEngine().layout(job, profile)
        if (layout.job == null) {
            return ReceiptLineResult(
                markdown = null,
                diagnostics = layout.diagnostics,
            )
        }

        val resolvedJob = requireNotNull(layout.job)
        val lines = mutableListOf("{text:nowrap}")
        val diagnostics = layout.diagnostics.toMutableList()
        if (resolvedJob.direction == PrintDirection.Reverse) {
            diagnostics += PreviewApproximation("reverse printing", path = null)
        }
        if (resolvedJob.startActions.isNotEmpty()) {
            diagnostics += PreviewApproximation("start actions", path = null)
        }

        resolvedJob.tickets.forEachIndexed { ticketIndex, ticket ->
            ticket.blocks.forEach { block ->
                appendBlock(block, profile.columnsPerLine, lines, diagnostics)
            }
            appendCut(ticket.cut, NodePath(ticketIndex = ticketIndex), lines, diagnostics)
        }

        lines += "{text:wrap}"
        return ReceiptLineResult(
            markdown = if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) null else lines.joinToString("\n"),
            diagnostics = diagnostics.toPersistentList(),
        )
    }

    private fun appendBlock(
        block: LaidOutBlock,
        columnsPerLine: Int,
        lines: MutableList<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        when (block) {
            is LaidOutTextLine -> {
                var style = TextStyle.Default
                var align = TextAlign.Left
                var foundStyle = false
                var previousStyle = TextStyle.Default
                var previousMarker = ""
                var physicalColumns = 0L
                val text = buildString {
                    block.operations.forEach { operation ->
                        when (operation) {
                            is SetStyle -> {
                                style = operation.style
                                if (!foundStyle) align = style.align
                                foundStyle = true
                            }
                            is TextBytes -> {
                                physicalColumns += operation.bytes.size.toLong() * style.widthScale
                                if (previousStyle.inverted != style.inverted) append('`')
                                if (previousStyle.emphasized != style.emphasized) append('"')
                                val marker = styleMarker(style, block.path, diagnostics)
                                if (previousMarker != marker) append(marker.ifEmpty { previousMarker })
                                append(escapeText(operation.source))
                                previousStyle = style
                                previousMarker = marker
                            }
                        }
                    }
                }
                if (physicalColumns > columnsPerLine) {
                    diagnostics += InvalidLayout("ReceiptLine cannot preserve an unwrapped line wider than $columnsPerLine columns", block.path)
                }
                lines += alignColumn(text, align)
            }
            is LaidOutRule -> {
                lines += "---"
                if (block.thickness != 1) diagnostics += PreviewApproximation("rule thickness ${block.thickness}", block.path)
            }
            is LaidOutFeed -> repeat(block.lines) { lines += "" }
            is LaidOutQrCode -> appendQrCode(block, lines, diagnostics)
        }
    }

    private fun appendQrCode(
        qrCode: LaidOutQrCode,
        lines: MutableList<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val source = requireNotNull(qrCode.source)
        // ReceiptLine's SVG QR generator uses UTF-8. Only emit data that has the
        // exact same bytes as the CP932 payload sent to the printer.
        if (!source.encodeToByteArray().contentEquals(qrCode.data.toByteArray()) ||
            source.any { it.code < 32 || it.code == 127 }
        ) {
            diagnostics += InvalidLayout(
                reason = "ReceiptLine QR preview cannot preserve this CP932 payload as UTF-8",
                path = qrCode.path,
            )
            return
        }
        // ReceiptLine getCapacity(level, 40) in byte mode. The encoding check above
        // ensures data.size is also the UTF-8 length before ReceiptLine escaping.
        val maximumBytes = when (qrCode.errorCorrection) {
            QrErrorCorrection.Low -> 2953
            QrErrorCorrection.Medium -> 2331
            QrErrorCorrection.Quartile -> 1663
            QrErrorCorrection.High -> 1273
        }
        if (qrCode.data.size > maximumBytes) {
            diagnostics += InvalidLayout(
                reason = "ReceiptLine QR preview supports at most $maximumBytes bytes at ${qrCode.errorCorrection} correction",
                path = qrCode.path,
            )
            return
        }
        val cellSize = qrCode.cellSize.coerceAtMost(MAX_PREVIEW_QR_CELL_SIZE)
        if (qrCode.cellSize !in MIN_STANDARD_QR_CELL_SIZE..MAX_PREVIEW_QR_CELL_SIZE) {
            diagnostics += PreviewApproximation("QR cell size ${qrCode.cellSize}", qrCode.path)
        }
        lines += alignColumn(
            "{code:${escapeProperty(source)}; option:qrcode,$cellSize,${qrErrorCorrection(qrCode.errorCorrection)}}",
            qrCode.align,
        )
    }

    private fun appendCut(
        cut: CutMode,
        path: NodePath,
        lines: MutableList<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        when (cut) {
            CutMode.None -> Unit
            CutMode.Full -> lines += "="
            CutMode.Partial,
            CutMode.NoMargin,
            -> {
                lines += "="
                diagnostics += PreviewApproximation("${cut.name} cut", path)
            }
        }
    }

    private fun styleMarker(
        style: TextStyle,
        path: NodePath?,
        diagnostics: MutableList<Diagnostic>,
    ): String = when {
        style.widthScale == 1 && style.heightScale == 1 -> ""
        style.widthScale == 2 && style.heightScale == 1 -> "^"
        style.widthScale == 1 && style.heightScale == 2 -> "^^"
        style.widthScale == style.heightScale -> {
            val scale = style.widthScale.coerceIn(2, MAX_PREVIEW_SCALE)
            if (scale != style.widthScale) {
                diagnostics += PreviewApproximation("text scale ${style.widthScale}", path)
            }
            "^".repeat(scale + 1)
        }
        else -> {
            diagnostics += PreviewApproximation(
                "asymmetric text scale ${style.widthScale}x${style.heightScale}",
                path,
            )
            // Double height is the tallest marker that keeps text one column wide.
            if (style.widthScale == 1) "^^" else "^".repeat(style.widthScale.coerceAtMost(MAX_PREVIEW_SCALE) + 1)
        }
    }

    private fun alignColumn(content: String, align: TextAlign): String {
        if (content.isEmpty()) return content
        return when (align) {
            TextAlign.Left -> "$content |"
            TextAlign.Center -> "| $content |"
            TextAlign.Right -> "| $content"
        }
    }

    private fun escapeText(value: String): String = buildString {
        value.forEachIndexed { index, character ->
            if (character == ' ' && (index == 0 || index == value.lastIndex)) {
                append("\\x20")
                return@forEachIndexed
            }
            if (character in TEXT_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private fun escapeProperty(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when (character) {
                ' ' -> append("\\x20")
                '\r' -> {
                    append("\\n")
                    if (index + 1 < value.length && value[index + 1] == '\n') index += 1
                }
                '\n' -> append("\\n")
                else -> {
                    if (character in PROPERTY_SPECIAL_CHARACTERS) append('\\')
                    append(character)
                }
            }
            index += 1
        }
    }

    private fun qrErrorCorrection(value: QrErrorCorrection): String = when (value) {
        QrErrorCorrection.Low -> "l"
        QrErrorCorrection.Medium -> "m"
        QrErrorCorrection.Quartile -> "q"
        QrErrorCorrection.High -> "h"
    }

    private val TEXT_SPECIAL_CHARACTERS: Set<Char> =
        setOf('\\', '|', '{', '}', '-', '=', '~', '_', '"', '`', '^')
    private val PROPERTY_SPECIAL_CHARACTERS: Set<Char> = setOf('\\', '|', '{', '}', ';')
    private const val MIN_STANDARD_QR_CELL_SIZE: Int = 3
    private const val MAX_PREVIEW_QR_CELL_SIZE: Int = 8
    private const val MAX_PREVIEW_SCALE: Int = 6
}

private fun previewPrinterProfile(profile: PreviewProfile): PrinterProfile = PrinterProfile(
    id = "receiptline-preview",
    commandSet = CommandSet.EscPos,
    columnsPerLine = profile.columnsPerLine,
    capabilities = PrinterCapabilities(
        maxWidthScale = profile.columnsPerLine,
        maxHeightScale = Int.MAX_VALUE,
        supportedCuts = persistentSetOf(CutMode.Full, CutMode.Partial, CutMode.NoMargin),
        supportsQrCode = true,
        supportsEmphasis = true,
        supportsInverse = true,
        reverseMode = ReverseMode.NativePageReverse,
        soundMode = SoundMode.NativeBuzzer,
        maxQrCellSize = Int.MAX_VALUE,
    ),
    compatibilityMode = CompatibilityMode.Strict,
)
