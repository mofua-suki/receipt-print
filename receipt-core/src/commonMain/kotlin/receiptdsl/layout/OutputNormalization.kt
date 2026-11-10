package receiptdsl.layout

import kotlinx.collections.immutable.toPersistentList
import receiptdsl.diagnostic.Diagnostic
import receiptdsl.diagnostic.DiagnosticSeverity
import receiptdsl.diagnostic.UnsupportedFeature
import receiptdsl.diagnostic.NodePath
import receiptdsl.diagnostic.ValueClamped
import receiptdsl.diagnostic.ValueSubstituted
import receiptdsl.model.CommandSet
import receiptdsl.model.CompatibilityMode
import receiptdsl.model.CutMode
import receiptdsl.model.PrintDirection
import receiptdsl.model.PrinterProfile
import receiptdsl.model.ReverseMode

/** Resolves compatibility substitutions once for both command and preview output.
 * Idempotent so callers of the low-level encoders receive the same substitutions.
 */
public fun normalizeOutput(job: LaidOutJob, profile: PrinterProfile): LayoutResult {
    val diagnostics = mutableListOf<Diagnostic>()
    if (profile.compatibilityMode != CompatibilityMode.Compatibility) {
        return LayoutResult(job, diagnostics.toPersistentList())
    }
    fun substitute(property: String, requested: Any, actual: Any, path: NodePath?) {
        if (requested != actual) {
            diagnostics += ValueSubstituted(property, requested.toString(), actual.toString(), path)
        }
    }
    val tickets = job.tickets.mapIndexed { index, ticket ->
        val path = NodePath(ticketIndex = index)
        val cuts = profile.capabilities.supportedCuts
        val cut = when {
            ticket.cut == CutMode.None -> CutMode.None
            ticket.cut == CutMode.NoMargin && ticket.cut in cuts -> ticket.cut
            // Existing ESC/POS compatibility output uses feed-and-partial-cut.
            profile.commandSet == CommandSet.EscPos && CutMode.Partial in cuts -> CutMode.Partial
            ticket.cut in cuts -> ticket.cut
            CutMode.Full in cuts -> CutMode.Full
            CutMode.Partial in cuts -> CutMode.Partial
            else -> CutMode.None
        }
        substitute("ticket.cut", ticket.cut, cut, path)
        ticket.copy(
            cut = cut,
            blocks = ticket.blocks.mapNotNull { block ->
                if (block !is LaidOutQrCode) return@mapNotNull block
                if (!profile.capabilities.supportsQrCode) {
                    diagnostics += UnsupportedFeature("qr-code", profile.id, DiagnosticSeverity.Warning, block.path)
                    return@mapNotNull null
                }
                val cellSize = profile.capabilities.compatibilityQrCellSize
                    ?: block.cellSize.coerceIn(1, profile.capabilities.maxQrCellSize.coerceAtLeast(1))
                if (cellSize != block.cellSize) {
                    diagnostics += ValueClamped("qrCode.cellSize", block.cellSize, cellSize, block.path)
                }
                val correction = profile.capabilities.compatibilityQrErrorCorrection ?: block.errorCorrection
                substitute("qrCode.errorCorrection", block.errorCorrection, correction, block.path)
                block.copy(cellSize = cellSize, errorCorrection = correction)
            }.toPersistentList(),
        )
    }.toPersistentList()
    val direction = if (profile.capabilities.reverseMode == ReverseMode.Unsupported) {
        if (job.direction == PrintDirection.Reverse) {
            diagnostics += UnsupportedFeature("reverse", profile.id, DiagnosticSeverity.Warning, null)
        }
        PrintDirection.Normal
    } else {
        job.direction
    }
    return LayoutResult(job.copy(tickets = tickets, direction = direction), diagnostics.toPersistentList())
}
