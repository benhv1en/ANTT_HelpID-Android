package com.helpid.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.helpid.app.R
import com.helpid.app.data.UserProfile
import java.io.File

object ShareUtils {

    fun shareEmergencyInfo(context: Context, profile: UserProfile) {
        val shareText = buildShareText(context, profile)

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_emergency_info)))
    }

    fun sharePDFFile(context: Context, pdfFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/pdf"
            }

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_pdf_title)))
        } catch (_: Exception) {
        }
    }

    fun shareViaWhatsApp(context: Context, profile: UserProfile) {
        val shareText = buildShareText(context, profile)

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/?text=${Uri.encode(shareText)}")
                }
            )
        }
    }

    fun shareViaSMS(context: Context, profile: UserProfile) {
        val shareText = buildShareText(context, profile)

        val intent = Intent().apply {
            action = Intent.ACTION_SENDTO
            data = Uri.parse("smsto:")
            putExtra("sms_body", shareText)
        }

        context.startActivity(intent)
    }

    fun shareViaEmail(context: Context, profile: UserProfile) {
        val shareText = buildShareText(context, profile)

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_email_subject))
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "message/rfc822"
        }

        context.startActivity(intent)
    }

    private fun buildShareText(context: Context, profile: UserProfile): String {
        val sb = StringBuilder()

        sb.append(context.getString(R.string.share_profile_title)).append("\n\n")
        sb.append(context.getString(R.string.share_label_name, profile.name)).append("\n")
        sb.append(context.getString(R.string.share_label_blood_group, profile.bloodGroup)).append("\n")
        sb.append(context.getString(R.string.share_label_emergency_id, profile.userId)).append("\n\n")

        if (profile.medicalNotes.isNotEmpty()) {
            sb.append(context.getString(R.string.share_conditions_header)).append("\n")
            profile.medicalNotes.forEach { note ->
                sb.append("- $note\n")
            }
            sb.append("\n")
        }

        if (profile.emergencyContacts.isNotEmpty()) {
            sb.append(context.getString(R.string.share_contacts_header)).append("\n")
            profile.emergencyContacts.forEach { contact ->
                sb.append("- ${contact.name}: ${contact.phone}\n")
            }
        }

        return sb.toString()
    }
}
