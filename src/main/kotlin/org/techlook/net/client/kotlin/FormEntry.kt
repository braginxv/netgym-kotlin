package org.techlook.net.client.kotlin

import org.techlook.net.client.http.FormField
import org.techlook.net.client.http.FormFileField
import java.nio.charset.Charset

/**
 * Bytes block with overridden equals/hashCode methods for matching in data classes
 */
class FormEntryBody(val content: ByteArray) {
    override fun equals(other: Any?): Boolean {
        return other is FormEntryBody && content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        return content.contentHashCode()
    }
}

/**
 * Multipart form-data entry
 */
sealed class FormEntry {
    abstract val name: String
    abstract val contentType: String
    abstract val charset: Charset?

    internal abstract val netgymFormForm: FormField
}

/**
 * Multipart form-data entry containing raw bytes block
 */
data class RawContentFormEntry(
    override val name: String,
    val body: FormEntryBody,
    override val contentType: String,
    override val charset: Charset?
) : FormEntry() {
    override val netgymFormForm = FormField(name, body.content, contentType, charset)
}

/**
 * Multipart form-data entry containing String body
 */
data class StringFormEntry(
    override val name: String,
    val content: String,
    override val contentType: String,
    override val charset: Charset?
) : FormEntry() {
    override val netgymFormForm = FormField(name,
        charset?.let { content.toByteArray(it) } ?: content.encodeToByteArray(), contentType, charset)
}

/**
 * Multipart form-data entry comprises file content
 */
data class FileFormEntry(
    override val name: String,
    val fileName: String,
    val body: FormEntryBody,
    override val contentType: String,
    override val charset: Charset?
) : FormEntry() {
    override val netgymFormForm = FormFileField(name, fileName, body.content, contentType, charset)
}
