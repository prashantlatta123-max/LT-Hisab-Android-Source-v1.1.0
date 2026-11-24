package com.sarotech.lthisab.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.sarotech.lthisab.R
import com.sarotech.lthisab.data.AppSettings
import com.sarotech.lthisab.data.BalanceCalculator
import com.sarotech.lthisab.data.CashTransaction
import com.sarotech.lthisab.i18n.AppStrings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatementData(
    val periodLabel: String,
    val openingBalancePaise: Long,
    val transactions: List<CashTransaction>,
    val runningBalances: Map<Long, Long>,
    val totalInPaise: Long,
    val totalOutPaise: Long,
    val closingBalancePaise: Long
)

object StatementPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 32f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X
    private const val FOOTER_Y = 816f
    private const val CONTENT_BOTTOM_LIMIT = 786f

    private const val COL_DATE_X = MARGIN_X
    private const val COL_DATE_W = 86f
    private const val COL_NOTES_X = COL_DATE_X + COL_DATE_W + 6f
    private const val COL_NOTES_W = 191f
    private const val COL_IN_X = COL_NOTES_X + COL_NOTES_W + 6f
    private const val COL_IN_W = 76f
    private const val COL_OUT_X = COL_IN_X + COL_IN_W + 6f
    private const val COL_OUT_W = 76f
    private const val COL_BAL_X = COL_OUT_X + COL_OUT_W + 6f
    private const val COL_BAL_W = 78f

    private val BRAND_BURGUNDY = Color.rgb(75, 16, 40)
    private val SOFT_HEADER_BG = Color.rgb(245, 240, 242)
    private val ALT_ROW_BG = Color.rgb(250, 248, 249)
    private val BORDER_GRAY = Color.rgb(215, 208, 211)
    private val TEXT_DARK = Color.rgb(33, 27, 30)
    private val TEXT_MUTED = Color.rgb(100, 92, 96)
    private val COLOR_IN = Color.rgb(30, 126, 67)
    private val COLOR_OUT = Color.rgb(201, 52, 52)

    private data class PreparedRow(
        val dateLayout: StaticLayout,
        val noteLayout: StaticLayout,
        val inText: String,
        val outText: String,
        val balText: String,
        val rowHeight: Float
    )

    fun generatePdfFile(
        context: Context,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings
    ): File {
        val doc = PdfDocument()
        val ltLogo = decodeScaledBitmap(context, R.drawable.lt_hisab_logo, 160)
        val sarotechLogo = decodeScaledBitmap(context, R.drawable.sarotech_logo, 260)

        val dateFmt = SimpleDateFormat(
            if (settings.showTimeOnRows) settings.dateTimePattern() else settings.dateFormat.pattern,
            Locale.getDefault()
        )
        val generatedFmt = SimpleDateFormat(settings.dateTimePattern(), Locale.getDefault())
        val generatedText = "${strings.pdfGeneratedOn}: ${generatedFmt.format(Date())}"

        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 8.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val ordered = statement.transactions.sortedWith(
            compareBy<CashTransaction> { it.timestamp }.thenBy { it.id }
        )

        val preparedRows = ordered.map { item ->
            val dateStr = dateFmt.format(Date(item.timestamp))
            val dLayout = buildStaticLayout(dateStr, datePaint, COL_DATE_W.toInt())
            val nLayout = buildStaticLayout(item.note, notePaint, COL_NOTES_W.toInt())
            val inText = if (item.type == "IN") formatAmount(item.amountPaise, settings) else "-"
            val outText = if (item.type == "OUT") formatAmount(item.amountPaise, settings) else "-"
            val balPaise = statement.runningBalances[item.id] ?: statement.openingBalancePaise
            val balText = formatAmount(balPaise, settings)
            val contentH = maxOf(dLayout.height, nLayout.height).toFloat()
            PreparedRow(dLayout, nLayout, inText, outText, balText, maxOf(22f, contentH + 10f))
        }

        val firstPageHeaderHeight = calculateFirstPageHeaderHeight(settings)
        val contPageHeaderHeight = 82f
        val tableHeaderHeight = 24f
        val summaryBlockHeight = 96f

        val pages = mutableListOf<MutableList<PreparedRow>>()
        var currentPageRows = mutableListOf<PreparedRow>()
        var currentY = firstPageHeaderHeight + tableHeaderHeight
        pages.add(currentPageRows)

        for (row in preparedRows) {
            if (currentY + row.rowHeight > CONTENT_BOTTOM_LIMIT) {
                currentPageRows = mutableListOf()
                pages.add(currentPageRows)
                currentY = contPageHeaderHeight + tableHeaderHeight
            }
            currentPageRows.add(row)
            currentY += row.rowHeight
        }

        val needsExtraSummaryPage = (currentY + summaryBlockHeight + 12f) > CONTENT_BOTTOM_LIMIT
        val totalPages = if (needsExtraSummaryPage) pages.size + 1 else pages.size

        pages.forEachIndexed { index, pageRows ->
            val pageNumber = index + 1
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val canvas = page.canvas
            var y = if (pageNumber == 1) {
                drawFirstPageHeader(canvas, ltLogo, sarotechLogo, statement, settings, strings, generatedText)
            } else {
                drawContinuationHeader(canvas, ltLogo, sarotechLogo, statement, strings)
            }

            y = drawTableHeader(canvas, y, strings)

            pageRows.forEachIndexed { rowIdx, row ->
                drawTransactionRow(canvas, y, row, rowIdx % 2 == 1)
                y += row.rowHeight
            }

            if (pageNumber == pages.size && !needsExtraSummaryPage) {
                drawSummaryBox(canvas, y + 12f, statement, settings, strings)
            }

            drawFooter(canvas, pageNumber, totalPages, strings)
            doc.finishPage(page)
        }

        if (needsExtraSummaryPage) {
            val pageNumber = totalPages
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val canvas = page.canvas
            val y = drawContinuationHeader(canvas, ltLogo, sarotechLogo, statement, strings)
            drawSummaryBox(canvas, y + 12f, statement, settings, strings)
            drawFooter(canvas, pageNumber, totalPages, strings)
            doc.finishPage(page)
        }

        ltLogo?.recycle()
        sarotechLogo?.recycle()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val outFile = File(context.cacheDir, "LT-Hisab-Statement-$stamp.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun sharePdf(
        context: Context,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings
    ) {
        val file = generatePdfFile(context, statement, settings, strings)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${strings.emailSubject} (${statement.periodLabel})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, strings.sharePdfChooser))
    }

    fun emailPdf(
        context: Context,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings
    ) {
        val file = generatePdfFile(context, statement, settings, strings)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val summaryText = buildString {
            appendLine("${strings.appName} - ${strings.statementTitle}")
            appendLine("${strings.pdfPeriodLabel}: ${statement.periodLabel}")
            appendLine("${strings.openingBalance}: ${formatAmount(statement.openingBalancePaise, settings)}")
            appendLine("${strings.pdfTotalIn}: ${formatAmount(statement.totalInPaise, settings)}")
            appendLine("${strings.pdfTotalOut}: ${formatAmount(statement.totalOutPaise, settings)}")
            appendLine("${strings.closingBalance}: ${formatAmount(statement.closingBalancePaise, settings)}")
            appendLine()
            append("${strings.generatedBy} • ${strings.brandName}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${strings.emailSubject} - ${statement.periodLabel}")
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, strings.emailChooserTitle))
    }

    fun printPdf(
        context: Context,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings
    ) {
        val file = generatePdfFile(context, statement, settings, strings)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = FilePdfPrintAdapter(file, strings.printJobName)
        printManager.print(strings.printJobName, adapter, PrintAttributes.Builder().build())
    }

    private class FilePdfPrintAdapter(
        private val pdfFile: File,
        private val jobName: String
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (input.read(buf).also { len = it } > 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            output.write(buf, 0, len)
                        }
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    private fun calculateFirstPageHeaderHeight(settings: AppSettings): Float {
        var h = 114f
        if (settings.userName.isNotBlank()) h += 16f
        if (settings.userAddress.isNotBlank()) h += 16f
        return h + 28f
    }

    private fun drawFirstPageHeader(
        canvas: Canvas,
        ltLogo: Bitmap?,
        sarotechLogo: Bitmap?,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings,
        generatedText: String
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 28f

        if (ltLogo != null) {
            canvas.drawBitmap(ltLogo, null, RectF(MARGIN_X, y, MARGIN_X + 42f, y + 42f), paint)
        }

        if (sarotechLogo != null) {
            val sW = 88f
            val sH = 49.5f
            val sLeft = PAGE_WIDTH - MARGIN_X - sW
            canvas.drawBitmap(sarotechLogo, null, RectF(sLeft, y, sLeft + sW, y + sH), paint)
        }

        val textX = MARGIN_X + 52f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_BURGUNDY
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawTextBlock(canvas, strings.appName, titlePaint, textX, y + 2f, 340)

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        drawTextBlock(canvas, "${strings.statementTitle} • ${strings.brandName}", subPaint, textX, y + 25f, 340)

        y += 56f

        paint.color = BRAND_BURGUNDY
        paint.strokeWidth = 1.6f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, paint)
        y += 10f

        val metaBold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaNormal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        if (settings.userName.isNotBlank()) {
            drawTextBlock(canvas, settings.userName, metaBold, MARGIN_X, y, 320)
            y += 15f
        }
        if (settings.userAddress.isNotBlank()) {
            drawTextBlock(canvas, settings.userAddress, metaNormal, MARGIN_X, y, 320)
            y += 15f
        }

        drawTextBlock(
            canvas,
            "${strings.pdfPeriodLabel}: ${statement.periodLabel}   |   $generatedText",
            metaNormal,
            MARGIN_X,
            y,
            CONTENT_WIDTH.toInt()
        )
        y += 18f

        paint.color = SOFT_HEADER_BG
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 22f), 4f, 4f, paint)
        drawTextBlock(canvas, strings.carriedForward, metaBold, MARGIN_X + 8f, y + 5f, 300)
        val openStr = formatAmount(statement.openingBalancePaise, settings)
        val rightPaint = TextPaint(metaBold).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(openStr, PAGE_WIDTH - MARGIN_X - 8f, y + 15f, rightPaint)
        y += 30f

        return y
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        ltLogo: Bitmap?,
        sarotechLogo: Bitmap?,
        statement: StatementData,
        strings: AppStrings
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val y = 24f
        if (ltLogo != null) {
            canvas.drawBitmap(ltLogo, null, RectF(MARGIN_X, y, MARGIN_X + 26f, y + 26f), paint)
        }
        if (sarotechLogo != null) {
            canvas.drawBitmap(
                sarotechLogo,
                null,
                RectF(PAGE_WIDTH - MARGIN_X - 58f, y, PAGE_WIDTH - MARGIN_X, y + 32.5f),
                paint
            )
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_BURGUNDY
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawTextBlock(
            canvas,
            "${strings.appName} — ${strings.statementTitle} (${statement.periodLabel})",
            titlePaint,
            MARGIN_X + 34f,
            y + 6f,
            380
        )
        val lineY = y + 38f
        paint.color = BRAND_BURGUNDY
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN_X, lineY, PAGE_WIDTH - MARGIN_X, lineY, paint)
        return lineY + 12f
    }

    private fun drawTableHeader(canvas: Canvas, topY: Float, strings: AppStrings): Float {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_BURGUNDY }
        canvas.drawRect(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + 22f, bgPaint)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawTextBlock(canvas, strings.pdfDateCol, headerPaint, COL_DATE_X + 4f, topY + 5f, COL_DATE_W.toInt())
        drawTextBlock(canvas, strings.pdfNotesCol, headerPaint, COL_NOTES_X, topY + 5f, COL_NOTES_W.toInt())

        val rightHeader = TextPaint(headerPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(strings.pdfInCol, COL_IN_X + COL_IN_W, topY + 14.5f, rightHeader)
        canvas.drawText(strings.pdfOutCol, COL_OUT_X + COL_OUT_W, topY + 14.5f, rightHeader)
        canvas.drawText(strings.pdfBalanceCol, COL_BAL_X + COL_BAL_W - 4f, topY + 14.5f, rightHeader)

        return topY + 24f
    }

    private fun drawTransactionRow(
        canvas: Canvas,
        topY: Float,
        row: PreparedRow,
        shaded: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shaded) {
            paint.color = ALT_ROW_BG
            canvas.drawRect(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + row.rowHeight, paint)
        }

        canvas.save()
        canvas.translate(COL_DATE_X + 4f, topY + 5f)
        row.dateLayout.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(COL_NOTES_X, topY + 5f)
        row.noteLayout.draw(canvas)
        canvas.restore()

        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.0f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val baseline = topY + 14.5f

        numPaint.color = if (row.inText == "-") TEXT_MUTED else COLOR_IN
        canvas.drawText(row.inText, COL_IN_X + COL_IN_W, baseline, numPaint)

        numPaint.color = if (row.outText == "-") TEXT_MUTED else COLOR_OUT
        canvas.drawText(row.outText, COL_OUT_X + COL_OUT_W, baseline, numPaint)

        numPaint.color = TEXT_DARK
        numPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(row.balText, COL_BAL_X + COL_BAL_W - 4f, baseline, numPaint)

        paint.color = BORDER_GRAY
        paint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN_X, topY + row.rowHeight, PAGE_WIDTH - MARGIN_X, topY + row.rowHeight, paint)
    }

    private fun drawSummaryBox(
        canvas: Canvas,
        topY: Float,
        statement: StatementData,
        settings: AppSettings,
        strings: AppStrings
    ) {
        val boxRect = RectF(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + 86f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SOFT_HEADER_BG }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_BURGUNDY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(boxRect, 6f, 6f, fill)
        canvas.drawRoundRect(boxRect, 6f, 6f, stroke)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_BURGUNDY
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val leftX = MARGIN_X + 12f
        val rightX = PAGE_WIDTH - MARGIN_X - 12f
        var y = topY + 10f

        drawTextBlock(canvas, strings.carriedForward, labelPaint, leftX, y, 280)
        valPaint.color = TEXT_DARK
        canvas.drawText(formatAmount(statement.openingBalancePaise, settings), rightX, y + 10f, valPaint)
        y += 18f

        drawTextBlock(canvas, strings.pdfTotalIn, labelPaint, leftX, y, 280)
        valPaint.color = COLOR_IN
        canvas.drawText(formatAmount(statement.totalInPaise, settings), rightX, y + 10f, valPaint)
        y += 18f

        drawTextBlock(canvas, strings.pdfTotalOut, labelPaint, leftX, y, 280)
        valPaint.color = COLOR_OUT
        canvas.drawText(formatAmount(statement.totalOutPaise, settings), rightX, y + 10f, valPaint)
        y += 20f

        drawTextBlock(canvas, strings.closingBalance, boldPaint, leftX, y, 280)
        valPaint.color = BRAND_BURGUNDY
        valPaint.textSize = 11f
        canvas.drawText(formatAmount(statement.closingBalancePaise, settings), rightX, y + 11f, valPaint)
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        strings: AppStrings
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_GRAY
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_X, FOOTER_Y - 14f, PAGE_WIDTH - MARGIN_X, FOOTER_Y - 14f, linePaint)

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = 8.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        drawTextBlock(
            canvas,
            "${strings.generatedBy}  •  ${strings.brandName}",
            footerPaint,
            MARGIN_X,
            FOOTER_Y - 8f,
            340
        )

        val pageStr = String.format(Locale.getDefault(), strings.pdfPageOf, pageNumber, totalPages)
        val rightPaint = TextPaint(footerPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageStr, PAGE_WIDTH - MARGIN_X, FOOTER_Y + 2f, rightPaint)
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int
    ) {
        val layout = buildStaticLayout(text, paint, width)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun formatAmount(paise: Long, settings: AppSettings): String =
        BalanceCalculator.formatMoney(paise, settings.currencySymbol, settings.indianNumberFormat)

    private fun decodeScaledBitmap(context: Context, resId: Int, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeResource(context.resources, resId, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }
}
