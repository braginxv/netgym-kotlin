/*
 * The MIT License
 *
 * Copyright (c) 2022 Vladimir Bragin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
