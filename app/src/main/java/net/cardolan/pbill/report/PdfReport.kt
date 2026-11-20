package net.cardolan.pbill.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import net.cardolan.pbill.R
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.domain.Money
import net.cardolan.pbill.domain.Weight
import net.cardolan.pbill.ui.Dates
import net.cardolan.pbill.ui.GroupUiState
import java.io.File
import java.time.LocalDate

/** Group report: per-member totals, remaining reimbursements, expenses and reimbursements made. */
object PdfReport {

    private const val REPORTS_DIR = "reports"

    /** Writes the report into the cache and returns a URI that other apps can read. */
    fun export(context: Context, state: GroupUiState): Uri {
        val group = checkNotNull(state.group) { "group not loaded" }
        val dir = File(context.cacheDir, REPORTS_DIR)
        // Only the latest report is kept: older ones have already been shared
        dir.deleteRecursively()
        dir.mkdirs()
        val safeName = group.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifEmpty { "group" }
        val file = File(dir, "PBill-$safeName-${LocalDate.now()}.pdf")
        file.outputStream().use { out ->
            val document = PdfDocument()
            try {
                render(context, state, document)
                document.writeTo(out)
            } finally {
                document.close()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun render(context: Context, state: GroupUiState, document: PdfDocument) {
        val group = state.group!!
        val currency = group.currency
        fun money(value: Long) = Money.format(value, currency)
        fun str(id: Int, vararg args: Any) = context.getString(id, *args)

        val w = PageWriter(document, footer = context.getString(R.string.app_name) + " · " + group.name)

        w.title(group.name)
        w.paragraph(str(R.string.pdf_subtitle, Dates.format(Dates.todayUtcMillis()), currency), w.small)
        w.paragraph(str(R.string.total_spent, money(state.totalSpent)), w.bold)

        // Per-member totals
        w.section(str(R.string.pdf_section_summary))
        val summaryColumns = listOf(
            Column(str(R.string.pdf_col_member), 120f),
            Column(str(R.string.member_weight_label), 40f, alignRight = true),
            Column(str(R.string.pdf_col_paid), 71f, alignRight = true),
            Column(str(R.string.pdf_col_share), 71f, alignRight = true),
            Column(str(R.string.pdf_col_sent), 71f, alignRight = true),
            Column(str(R.string.pdf_col_received), 71f, alignRight = true),
            Column(str(R.string.pdf_col_balance), 71f, alignRight = true),
        )
        w.header(summaryColumns)
        state.balances.forEach { row ->
            val s = row.summary
            w.row(
                summaryColumns,
                listOf(
                    row.member.name,
                    Weight.format(row.member.weight),
                    money(s.paid),
                    money(s.share),
                    money(s.transfersSent),
                    money(s.transfersReceived),
                    money(s.balance),
                ),
            )
        }
        w.space(4f)
        w.paragraph(str(R.string.pdf_legend), w.small)

        // Still to reimburse
        w.section(str(R.string.pdf_section_remaining))
        if (state.settlements.isEmpty()) {
            w.paragraph(str(R.string.settlements_none))
        } else {
            val columns = listOf(
                Column(str(R.string.transfer_from), 215f),
                Column(str(R.string.transfer_to), 200f),
                Column(str(R.string.expense_amount), 100f, alignRight = true),
            )
            w.header(columns)
            state.settlements.forEach { w.row(columns, listOf(it.from.name, it.to.name, money(it.amount))) }
        }

        // Expenses
        val expenses = state.expenses.filter { it.expense.kind == ExpenseKind.EXPENSE }
        w.section(str(R.string.tab_expenses))
        if (expenses.isEmpty()) {
            w.paragraph(str(R.string.expenses_empty))
        } else {
            val columns = listOf(
                Column(str(R.string.pdf_col_date), 70f),
                Column(str(R.string.expense_title), 140f),
                Column(str(R.string.expense_paid_by), 90f),
                Column(str(R.string.expense_split_header), 140f),
                Column(str(R.string.expense_amount), 75f, alignRight = true),
            )
            w.header(columns)
            expenses.forEach { row ->
                val participants = row.participants.joinToString(", ") { (member, weight) ->
                    if (weight == Weight.ONE) member.name else "${member.name} ×${Weight.format(weight)}"
                }
                w.row(
                    columns,
                    listOf(
                        Dates.format(row.expense.date),
                        row.expense.title,
                        row.payerName,
                        participants,
                        money(row.expense.amountMinor),
                    ),
                )
            }
        }

        // Reimbursements already made
        val transfers = state.expenses.filter { it.expense.kind == ExpenseKind.TRANSFER }
        w.section(str(R.string.pdf_section_transfers))
        if (transfers.isEmpty()) {
            w.paragraph(str(R.string.pdf_no_transfers))
        } else {
            val columns = listOf(
                Column(str(R.string.pdf_col_date), 70f),
                Column(str(R.string.transfer_from), 170f),
                Column(str(R.string.transfer_to), 170f),
                Column(str(R.string.expense_amount), 105f, alignRight = true),
            )
            w.header(columns)
            transfers.forEach { row ->
                w.row(
                    columns,
                    listOf(
                        Dates.format(row.expense.date),
                        row.payerName,
                        row.recipientName.orEmpty(),
                        money(row.expense.amountMinor),
                    ),
                )
            }
        }
        w.close()
    }
}

private data class Column(val title: String, val width: Float, val alignRight: Boolean = false)

/** Minimal top-to-bottom layout on A4 pages, starting a new page when needed. */
private class PageWriter(private val document: PdfDocument, private val footer: String) {

    private val pageWidth = 595 // A4 in PostScript points
    private val pageHeight = 842
    private val margin = 40f
    private val footerHeight = 20f
    private val cellPadding = 3f
    private val contentWidth = pageWidth - 2 * margin

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val titlePaint = paint(20f, bold = true)
    private val sectionPaint = paint(13f, bold = true, color = Color.rgb(0x2E, 0x7D, 0x32))
    val normal = paint(9.5f)
    val bold = paint(9.5f, bold = true)
    val small = paint(8f, color = Color.DKGRAY)
    private val headerBackground = Paint().apply { color = Color.rgb(0xE8, 0xF5, 0xE9) }
    private val rule = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }

    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f

    private fun lineHeight(p: TextPaint) = p.fontSpacing * 1.15f

    private fun canvas() = page?.canvas ?: newPage().let { page!!.canvas }

    private fun newPage() {
        finishPage()
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        y = margin
    }

    private fun finishPage() {
        val current = page ?: return
        val text = "$footer · $pageNumber"
        current.canvas.drawText(text, (pageWidth - small.measureText(text)) / 2, pageHeight - margin / 2, small)
        document.finishPage(current)
        page = null
    }

    /** Starts a new page when [height] does not fit in the remaining space. */
    private fun ensure(height: Float) {
        if (page == null || y + height > pageHeight - margin - footerHeight) newPage()
    }

    private fun drawLine(text: String, p: TextPaint, x: Float = margin) {
        val h = lineHeight(p)
        ensure(h)
        canvas().drawText(text, x, y - p.ascent(), p)
        y += h
    }

    fun title(text: String) {
        drawLine(text, titlePaint)
        y += 2f
    }

    fun section(text: String) {
        // Keep the section title with at least its table header and one row
        ensure(lineHeight(sectionPaint) + 3 * lineHeight(normal) + 12f)
        y += 12f
        drawLine(text, sectionPaint)
        y += 2f
    }

    fun space(height: Float) {
        y += height
    }

    /** Word-wrapped text over the full content width. */
    fun paragraph(text: String, p: TextPaint = normal) {
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && p.measureText(candidate) > contentWidth) {
                drawLine(line, p)
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) drawLine(line, p)
    }

    fun header(columns: List<Column>) {
        val h = lineHeight(bold) + 2 * cellPadding
        ensure(h + lineHeight(normal))
        canvas().drawRect(margin, y, margin + contentWidth, y + h, headerBackground)
        drawCells(columns, columns.map { it.title }, bold, h)
    }

    fun row(columns: List<Column>, values: List<String>) {
        val h = lineHeight(normal) + 2 * cellPadding
        ensure(h)
        drawCells(columns, values, normal, h)
        canvas().drawLine(margin, y, margin + contentWidth, y, rule)
    }

    private fun drawCells(columns: List<Column>, values: List<String>, p: TextPaint, height: Float) {
        val c = canvas()
        var x = margin
        val baseline = y + cellPadding - p.ascent()
        columns.zip(values).forEach { (column, value) ->
            val available = column.width - 2 * cellPadding
            val text = TextUtils.ellipsize(value, p, available, TextUtils.TruncateAt.END).toString()
            val textX = if (column.alignRight) x + column.width - cellPadding - p.measureText(text) else x + cellPadding
            c.drawText(text, textX, baseline, p)
            x += column.width
        }
        y += height
    }

    fun close() = finishPage()
}
