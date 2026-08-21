package com.rodsan.pay.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.rodsan.pay.data.settlement.SettlementEntity
import com.rodsan.pay.data.settlement.SettlementItemEntity
import com.rodsan.pay.data.payment.PaymentEntity
import com.rodsan.pay.data.payment.PaymentItemEntity
import com.rodsan.pay.data.settlement.WorkerMonthlySummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera y comparte reportes en PDF usando únicamente APIs nativas de
 * Android (android.graphics.pdf.PdfDocument), sin dependencias externas.
 *
 * El PDF se puede generar en cualquier momento -no solo a fin de mes-: el
 * botón "Descargar PDF" está disponible en el detalle de cada liquidación y
 * en el resumen mensual, cuando el usuario lo pida. El recordatorio de fin
 * de mes (ver [ReminderScheduler]) es independiente: solo avisa que ya
 * terminó el mes, no genera el PDF automáticamente.
 *
 * El archivo se guarda en el caché privado de la app y se entrega mediante
 * [FileProvider] a través de un Intent para compartir/guardar (el usuario
 * elige dónde: Descargas, Drive, WhatsApp, imprimir, etc.), lo cual evita
 * pedir permisos de almacenamiento en cualquier versión de Android.
 */
object PdfExportUtil {

    private const val PAGE_WIDTH = 595 // A4 a 72dpi aprox.
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private val dateFormat = SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", Locale("es", "CO"))

    fun exportSettlement(context: Context, settlement: SettlementEntity, items: List<SettlementItemEntity>): Uri {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        var y = MARGIN

        // La liquidación es únicamente el comprobante de los trabajos realizados.
        // No lleva "Cuenta de cobro", cédula, valor total ni firma.
        y = drawCenteredSubtitle(canvas, settlement.companyName.ifBlank { "MUEBLES RODSAN" }.uppercase(), y, bold = true)
        y = drawCenteredSubtitle(canvas, "NIT 1061736327-5", y)
        y += 20f
        y = drawCenteredSubtitle(canvas, settlement.workerName, y, bold = true, size = 15f)
        y = drawCenteredSubtitle(canvas, "Fecha de liquidación: ${formatDate(settlement.dateMillis)}", y)
        y += 14f
        y = drawLine(canvas, "COMPROBANTE DE TRABAJOS REALIZADOS", y, bold = true)
        y += 8f
        items.forEach { item ->
            val effectiveTotal = item.paymentOverride ?: item.subtotal
            val effectiveUnit = if (item.paymentOverride != null && item.quantity > 0)
                effectiveTotal / item.quantity else item.unitPrice
            y = drawLine(
                canvas,
                "${item.quantity} × ${CurrencyFormatter.format(effectiveUnit)} — ${item.code} ${item.name} = ${CurrencyFormatter.format(effectiveTotal)}" +
                    if (item.paymentOverride != null) " — PAGO OPCIONAL APLICADO" else "",
                y
            )
            y = drawLine(canvas, "Fecha del trabajo: ${formatDate(item.dateMillis ?: settlement.dateMillis)}", y, size = 10f)
        }

        document.finishPage(page)
        val fileName = "liquidacion_${settlement.workerName.sanitize()}_${formatFileDate(settlement.dateMillis)}_${settlement.id}.pdf"
        return document.saveAndGetUri(context, fileName)
    }

    fun exportMonthlySummary(
        context: Context,
        periodLabel: String,
        rows: List<WorkerMonthlySummary>,
        totalGeneral: Long,
        companyName: String = ""
    ): Uri {
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )
        val canvas = page.canvas
        var y = MARGIN

        y = drawTitle(canvas, "Resumen mensual", y)
        y = drawSubtitle(canvas, companyName.ifBlank { "Muebles RodSan" }, y)
        y = drawSubtitle(canvas, periodLabel, y)
        y = drawSubtitle(canvas, "Fecha de generación: ${formatDate(System.currentTimeMillis())}", y)
        y += 16f
        y = drawDivider(canvas, y)
        y += 12f

        if (rows.isEmpty()) {
            drawLine(canvas, "No hay liquidaciones registradas en este mes.", y)
        } else {
            rows.forEach { row ->
                y = drawLine(canvas, row.workerName, y, bold = true)
                y = drawLine(
                    canvas,
                    "  ${row.settlementCount} liquidaciones · ${row.totalQuantity} unidades · ${CurrencyFormatter.format(row.totalPaid)}",
                    y
                )
            }
            y += 8f
            y = drawDivider(canvas, y)
            y += 16f
            drawLine(canvas, "TOTAL GENERAL: ${CurrencyFormatter.format(totalGeneral)}", y, bold = true, size = 16f)
        }

        document.finishPage(page)
        val fileName = "resumen_mensual_${periodLabel.sanitize()}_${formatFileDate(System.currentTimeMillis())}.pdf"
        return document.saveAndGetUri(context, fileName)
    }

    fun exportPayment(
        context: Context,
        payment: PaymentEntity,
        items: List<PaymentItemEntity>,
        workerDocumentId: String? = null
    ): Uri {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        var y = MARGIN

        // El PDF de pagos conserva el formato de Cuenta de Cobro suministrado:
        // encabezado y datos centrados, con el detalle de los trabajos como concepto.
        y = drawCenteredTitle(canvas, "CUENTA DE COBRO", y)
        y = drawCenteredSubtitle(canvas, payment.companyName.ifBlank { "MUEBLES RODSAN" }.uppercase(), y, bold = true)
        y = drawCenteredSubtitle(canvas, "NIT 1061736327-5", y)
        y = drawCenteredSubtitle(canvas, "Fecha de pago: ${formatDate(payment.paidAtMillis)}", y)
        y += 20f
        y = drawCenteredLine(canvas, "DEBE A: ${payment.workerName}", y, bold = true)
        y = drawCenteredLine(canvas, "C.C. ${workerDocumentId?.takeIf { it.isNotBlank() } ?: "NO REGISTRADA"}", y)
        y += 8f
        y = drawCenteredLine(canvas, "LA SUMA DE: ${amountInWords(payment.total)} PESOS", y)
        y = drawCenteredLine(canvas, "($ ${CurrencyFormatter.format(payment.total)})", y, bold = true)
        y += 14f
        y = drawCenteredLine(canvas, "POR CONCEPTO DE:", y, bold = true)
        items.forEach { item ->
            val effectiveUnit = item.paymentOverride ?: item.unitPrice
            val effectiveTotal = item.quantity.toLong() * effectiveUnit
            // El precio opcional es unitario, por lo que el concepto siempre muestra
            // cantidad × precio unitario y el total correcto del trabajo.
            y = drawCenteredLine(
                canvas,
                "${item.quantity} × ${CurrencyFormatter.format(effectiveUnit)} — ${item.code} ${item.name} = ${CurrencyFormatter.format(effectiveTotal)}",
                y
            )
            y = drawCenteredLine(canvas, "Fecha del trabajo: ${formatDate(item.dateMillis)}", y, size = 10f)
        }
        y += 18f
        y = drawCenteredLine(canvas, "ATENTAMENTE:", y, bold = true)
        y += 36f
        y = drawCenteredLine(canvas, "________________________________________", y)
        y = drawCenteredLine(canvas, "Nombre: ${payment.workerName}", y)
        drawCenteredLine(canvas, "C.C.: ${workerDocumentId?.takeIf { it.isNotBlank() } ?: "NO REGISTRADA"}", y)

        document.finishPage(page)
        val fileName = "pago_${payment.workerName.sanitize()}_${formatFileDate(payment.paidAtMillis)}_${payment.id}.pdf"
        return document.saveAndGetUri(context, fileName)
    }

    /** Abre el selector del sistema para guardar/compartir el PDF ya generado. */
    fun sharePdf(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Descargar / compartir PDF"))
    }

    private fun PdfDocument.saveAndGetUri(context: Context, fileName: String): Uri {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { writeTo(it) }
        close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun formatDate(millis: Long): String {
        val value = SimpleDateFormat("EEEE dd/MM/yyyy", Locale("es", "CO")).format(Date(millis))
        return value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
    }

    private fun formatFileDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun amountInWords(value: Long): String {
        if (value == 0L) return "CERO"
        fun belowThousand(n: Int): String {
            val units = arrayOf("", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE")
            val teens = arrayOf("DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE")
            val tens = arrayOf("", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA")
            val hundreds = arrayOf("", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS")
            if (n == 100) return "CIEN"
            val parts = mutableListOf<String>(); var x=n
            if (x >= 100) { parts += hundreds[x/100]; x %= 100 }
            if (x >= 20 && x <= 29) { parts += if (x == 20) "VEINTE" else "VEINTI" + units[x%10]; x = 0 }
            else if (x >= 30) { parts += tens[x/10] + if (x%10>0) " Y ${units[x%10]}" else ""; x=0 }
            else if (x >= 10) { parts += teens[x-10]; x=0 }
            if (x > 0) parts += units[x]
            return parts.joinToString(" ")
        }
        var n=value; val parts=mutableListOf<String>()
        if (n >= 1_000_000) { val m=(n/1_000_000).toInt(); parts += if(m==1) "UN MILLÓN" else "${belowThousand(m)} MILLONES"; n%=1_000_000 }
        if (n >= 1000) { val t=(n/1000).toInt(); parts += if(t==1) "MIL" else "${belowThousand(t)} MIL"; n%=1000 }
        if (n > 0) parts += belowThousand(n.toInt())
        return parts.joinToString(" ")
    }

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun drawTitle(canvas: Canvas, text: String, y: Float): Float {
        val paint = Paint().apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.BLACK
        }
        canvas.drawText(text, MARGIN, y + paint.textSize, paint)
        return y + paint.textSize + 6f
    }

    private fun drawSubtitle(canvas: Canvas, text: String, y: Float): Float {
        val paint = Paint().apply {
            textSize = 13f
            color = android.graphics.Color.DKGRAY
        }
        canvas.drawText(text, MARGIN, y + paint.textSize, paint)
        return y + paint.textSize + 4f
    }

    private fun drawCenteredTitle(canvas: Canvas, text: String, y: Float): Float {
        val paint = Paint().apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, PAGE_WIDTH / 2f, y + paint.textSize, paint)
        return y + paint.textSize + 6f
    }

    private fun drawCenteredSubtitle(canvas: Canvas, text: String, y: Float, bold: Boolean = false, size: Float = 13f): Float {
        val paint = Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = android.graphics.Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, PAGE_WIDTH / 2f, y + paint.textSize, paint)
        return y + paint.textSize + 4f
    }

    private fun drawCenteredLine(canvas: Canvas, text: String, y: Float, bold: Boolean = false, size: Float = 12f): Float {
        val paint = Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, PAGE_WIDTH / 2f, y + paint.textSize, paint)
        return y + paint.textSize + 8f
    }

    private fun drawDivider(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
        return y
    }

    private fun drawLine(canvas: Canvas, text: String, y: Float, bold: Boolean = false, size: Float = 12f): Float {
        val paint = Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = android.graphics.Color.BLACK
        }
        canvas.drawText(text, MARGIN, y + paint.textSize, paint)
        return y + paint.textSize + 8f
    }
}
