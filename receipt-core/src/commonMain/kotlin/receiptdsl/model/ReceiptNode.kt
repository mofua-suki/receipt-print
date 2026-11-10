package receiptdsl.model

import kotlinx.collections.immutable.PersistentList

public sealed interface ReceiptNode

public sealed interface BlockNode : ReceiptNode

public data class Column(
    val children: PersistentList<BlockNode>,
    /** null inherits; an explicit default style resets all inherited properties. */
    val style: TextStyle? = null,
) : BlockNode

public data class TextBlock(
    val text: String,
    /** null inherits; an explicit default style resets all inherited properties. */
    val style: TextStyle? = null,
    val wrap: WrapMode = WrapMode.WordOrCharacter,
    val preserveNewLines: Boolean = true,
) : BlockNode

public data class TwoColumnLine(
    val leading: TextRun,
    val trailing: TextRun? = null,
    val widthColumns: Int? = null,
    val leadingReservedColumns: Int? = null,
    val trailingReservedColumns: Int? = null,
    val overflow: TwoColumnOverflow = TwoColumnOverflow.WrapLeadingThenTrailing,
) : BlockNode

public data class Rule(
    val thickness: Int = 1,
) : BlockNode

public data class QrCode(
    val data: String,
    val cellSize: Int = 6,
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.Medium,
    val align: TextAlign = TextAlign.Center,
) : BlockNode

public data class Feed(
    val lines: Int = 1,
) : BlockNode

public data class TextRun(
    val text: String,
    /** null inherits; an explicit default style resets all inherited properties. */
    val style: TextStyle? = null,
)

public enum class QrErrorCorrection {
    Low,
    Medium,
    Quartile,
    High,
}

public enum class WrapMode {
    None,
    Character,
    WordOrCharacter,
}

public enum class TwoColumnOverflow {
    WrapLeadingThenTrailing,
    TrailingOnNextLine,
    /** Reserved: always returns INVALID_LAYOUT; no clipping policy is implemented. */
    Clip,
    Fail,
}

/** A preformatted physical line. Runs are concatenated without padding or column expansion. */
public data class InlineLine(
    val runs: PersistentList<TextRun>,
    val align: TextAlign = TextAlign.Left,
) : BlockNode
