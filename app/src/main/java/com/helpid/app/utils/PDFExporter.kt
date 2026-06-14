package com.helpid.app.utils

import android.content.Context
import android.os.Environment
import com.helpid.app.R
import com.helpid.app.data.UserProfile
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PDFExporter {

    fun exportProfileToPDF(context: Context, profile: UserProfile): File? {
        return try {
            val fileName = "HelpID_${profile.userId}_${System.currentTimeMillis()}.pdf"
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                fileName
            )
            val fonts = createFonts()

            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            val title = Paragraph(context.getString(R.string.pdf_title), fonts.title)
            title.alignment = Element.ALIGN_CENTER
            document.add(title)

            document.add(Paragraph("\n"))

            addSection(document, context.getString(R.string.pdf_section_personal), fonts)
            addKeyValue(document, context.getString(R.string.name), profile.name, fonts)
            addKeyValue(document, context.getString(R.string.blood_group), profile.bloodGroup, fonts)
            addKeyValue(document, context.getString(R.string.emergency_id), profile.userId, fonts)

            document.add(Paragraph("\n"))

            if (profile.medicalNotes.isNotEmpty()) {
                addSection(document, context.getString(R.string.pdf_section_medical), fonts)
                profile.medicalNotes.forEach { note ->
                    document.add(Paragraph("- $note", fonts.body))
                }
            }

            document.add(Paragraph("\n"))

            if (profile.emergencyContacts.isNotEmpty()) {
                addSection(document, context.getString(R.string.pdf_section_contacts), fonts)
                profile.emergencyContacts.forEach { contact ->
                    addKeyValue(document, contact.name, contact.phone, fonts)
                }
            }

            document.add(Paragraph("\n\n"))

            val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val footer = Paragraph(context.getString(R.string.pdf_generated, generatedAt), fonts.footer)
            footer.alignment = Element.ALIGN_CENTER
            document.add(footer)

            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addSection(document: Document, title: String, fonts: PdfFonts) {
        val section = Paragraph(title, fonts.section)
        section.spacingBefore = 10f
        document.add(section)
    }

    private fun addKeyValue(document: Document, key: String, value: String, fonts: PdfFonts) {
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(30f, 70f))

        val keyCell = PdfPCell(Phrase(key, fonts.key))
        keyCell.border = 0
        keyCell.paddingBottom = 5f
        table.addCell(keyCell)

        val valueCell = PdfPCell(Phrase(value, fonts.body))
        valueCell.border = 0
        valueCell.paddingBottom = 5f
        table.addCell(valueCell)

        document.add(table)
    }

    private fun createFonts(): PdfFonts {
        val baseFont = listOf(
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        ).firstOrNull { File(it).exists() }?.let { path ->
            runCatching { BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED) }.getOrNull()
        }

        fun font(size: Float, style: Int = Font.NORMAL): Font {
            return if (baseFont != null) {
                Font(baseFont, size, style)
            } else {
                Font(Font.FontFamily.HELVETICA, size, style)
            }
        }

        return PdfFonts(
            title = font(20f, Font.BOLD),
            section = font(14f, Font.BOLD),
            key = font(11f, Font.BOLD),
            body = font(11f),
            footer = font(9f)
        )
    }

    private data class PdfFonts(
        val title: Font,
        val section: Font,
        val key: Font,
        val body: Font,
        val footer: Font
    )
}
