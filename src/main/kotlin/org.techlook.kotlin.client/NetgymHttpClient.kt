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

package org.techlook.kotlin.client

import org.techlook.ClientSystem
import org.techlook.Either
import org.techlook.http.*
import org.techlook.http.adapters.*
import org.techlook.http.client.HttpListener
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager
import kotlin.Pair
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

typealias NamedParams = Set<Pair<String, String>>

/**
 * This is the wrapper around the netgym network library (https://github.com/braginxv/netgym) which aims to bring
 * high performance means from this library to perform HTTP(S) requests in Kotlin.
 *
 * @param baseUrl - common beginning part of request urls including protocol, port, domain
 * e.g. https://domain.org/base/path/
 *
 * @param connectionLifetime - HTTP 1.1 Close, Keepalive or Pipelining connection
 * @see ConnectionLifetime
 *
 * @param userAgent - User-Agent header in HTTP request
 *
 * @param basicHeaders - set of HTTP headers presented in all requests sent via this http-client instance
 *
 * @param pipelineSendingInterval - time interval between requests sent on a pipelining connection if it's in use
 *
 * @param clientKeyManagers - user's certificates to authenticate it to the server
 *
 * @param trustManagers - in addition to system certificates to authenticate a server on the client side
 *
 */
class NetgymHttpClient(
    baseUrl: URL,
    connectionLifetime: ConnectionLifetime,
    userAgent: String = AGENT_CLIENT,
    basicHeaders: NamedParams = emptySet(),
    pipelineSendingInterval: Long = PipeliningConnection.DEFAULT_SENDING_INTERVAL,
    clientKeyManagers: Array<KeyManager>? = null,
    trustManagers: Array<TrustManager>? = null
) {
    private val connection: HttpConnection by lazy {
        (baseUrl.port.takeIf { it > 0 } ?: baseUrl.defaultPort).let { port ->
            when (connectionLifetime) {
                ConnectionLifetime.Closable -> SingleConnection(
                    baseUrl.host,
                    port,
                    fromProtocol(baseUrl.protocol, clientKeyManagers, trustManagers)
                )
                ConnectionLifetime.Sequential -> SequentialConnection(
                    baseUrl.host,
                    port,
                    fromProtocol(baseUrl.protocol, clientKeyManagers, trustManagers)
                )
                ConnectionLifetime.Pipelining -> PipeliningConnection(
                    baseUrl.host,
                    port,
                    fromProtocol(baseUrl.protocol, clientKeyManagers, trustManagers),
                    pipelineSendingInterval
                )
            }
        }
    }

    private val headers = basicHeaders + Pair(AGENT_HEADER, userAgent)

    /**
     * GET HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response
     */
    suspend fun get(
        resource: String,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.get(resource, toTechlook(headers), toTechlook(parameters), listener)
        }
    }

    /**
     * HEAD HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response in string representation
     */
    suspend fun head(
        resource: String,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.head(resource, toTechlook(headers), toTechlook(parameters), listener)
        }
    }

    /**
     * GET HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response in raw byte buffer representation
     */
    suspend fun rawGet(
        resource: String,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): Response = suspendCoroutine { continuation ->
        rawRequest(additionalHeaders, continuation) { headers, listener ->
            connection.get(resource, toTechlook(headers), toTechlook(parameters), listener)
        }
    }

    /**
     * PUT HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param requestBody - request body itself
     * @param contentType - MIME-type of the content
     * @param charset - encoding charset of the request body
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response in string representation
     */
    suspend fun put(
        resource: String,
        requestBody: String,
        contentType: String,
        charset: Charset = StandardCharsets.UTF_8,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.put(
                resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
                requestBody.toByteArray(charset), listener
            )
        }
    }

    /**
     * DELETE HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param requestBody - request body itself
     * @param contentType - MIME-type of the content
     * @param charset - encoding charset of the request body
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response in string representation
     */
    suspend fun delete(
        resource: String,
        requestBody: String,
        contentType: String,
        charset: Charset = StandardCharsets.UTF_8,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.delete(
                resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
                requestBody.toByteArray(charset), listener
            )
        }
    }

    /**
     * POST HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param requestBody - request body itself
     * @param contentType - MIME-type of the content
     * @param charset - encoding charset of the request body
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @param parameters - url-encoded parameters
     *
     * @return server response in string representation
     */
    suspend fun post(
        resource: String,
        requestBody: String,
        contentType: String,
        charset: Charset = StandardCharsets.UTF_8,
        additionalHeaders: NamedParams = emptySet(),
        parameters: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.postContent(
                resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
                requestBody.toByteArray(charset), listener
            )
        }
    }

    /**
     * Url-encoded POST HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param parameters - url-encoded parameters
     * @param additionalHeaders - additional headers to base headers specified where client instance is created
     *
     * @return server response in string representation
     */
    suspend fun postWithEncodedParameters(
        resource: String,
        parameters: NamedParams,
        additionalHeaders: NamedParams = emptySet()
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            connection.postWithEncodedParameters(resource, toTechlook(headers), toTechlook(parameters), listener)
        }
    }

    /**
     * Multipart Form POST HTTP request: baseUrl/resource
     * @param resource - path to resource relative to baseUrl
     * @param formData - multipart form data
     * @param additionalHeaders - additional headers to base headers specified when client instance was created
     * @return server response in string representation
     */
    suspend fun postFormData(
        resource: String,
        formData: Set<FormEntry>,
        additionalHeaders: NamedParams = emptySet(),
    ): StringResponse = suspendCoroutine { continuation ->
        stringRequest(additionalHeaders, continuation) { headers, listener ->
            val formRequest = FormRequestData()

            formData.forEach { entry ->
                when (entry) {
                    is StringFormEntry ->
                        formRequest.addInputField(entry.name, entry.content, entry.contentType, entry.charset)
                    is RawContentFormEntry ->
                        formRequest.addInputField(entry.name, entry.body.content, entry.contentType, entry.charset)
                    is FileFormEntry ->
                        formRequest.addFileField(
                            entry.name, entry.fileName, entry.body.content, entry.contentType, entry.charset
                        )
                }
            }

            connection.postFormData(resource, toTechlook(headers), formRequest, listener)
        }
    }

    private inline fun stringRequest(
        additionalHeaders: NamedParams,
        continuation: Continuation<StringResponse>,
        underlying: (NamedParams, HttpListener) -> Unit
    ) {
        val listener: HttpListener = object : StringResponseListener() {
            override fun respondString(response: Either<String, StringResponse>) {
                continuation.resumeWith(responseToCoroutineResult(response))
            }
        }

        val allHeaders = LinkedHashSet(headers)
        allHeaders.addAll(additionalHeaders)

        underlying(allHeaders, listener)
    }

    private inline fun rawRequest(
        additionalHeaders: NamedParams,
        continuation: Continuation<Response>,
        underlying: (NamedParams, HttpListener) -> Unit
    ) {
        val listener: HttpListener = object : ByteResponseListener() {
            override fun respond(response: Either<String, Response>) {
                continuation.resumeWith(responseToCoroutineResult(response))
            }
        }

        val allHeaders = LinkedHashSet(headers)
        allHeaders.addAll(additionalHeaders)

        underlying(allHeaders, listener)
    }

    companion object {
        private const val HTTP = "http"
        private const val HTTPS = "https"
        const val AGENT_HEADER: String = "User-Agent"
        const val AGENT_CLIENT: String = "Netgym network library (https://github.com/braginxv/netgym)"

        private fun <L, R> responseToCoroutineResult(response: Either<L, R>): Result<R> {
            var result: Result<R> = Result.failure(NetworkClientException("result is unset"))

            response.right().apply {
                result = Result.success(it)
            }
            response.left().apply {
                result = Result.failure(NetworkClientException(it.toString()))
            }

            return result
        }

        fun baseUrlFor(urls: Iterable<String>): String {
            val basePart = urls.reduce { baseUrl, url ->
                val urlLength = fun(): Int {
                    val lengthToCompare = min(baseUrl.length, url.length)
                    for (index in 0 until lengthToCompare) {
                        if (baseUrl[index] != url[index]) {
                            return index
                        }
                    }
                    return lengthToCompare
                }()

                baseUrl.take(urlLength)
            }

            return basePart.dropLastWhile { it != '/' }
        }

        private fun <K, V> toTechlook(keyValueSet: Set<Pair<K, V>>): Set<org.techlook.http.Pair<K, V>> =
            keyValueSet.map { (key, value) ->
                org.techlook.http.Pair(key, value)
            }.toSet()

        private fun fromProtocol(
            protocol: String,
            clientKeyManagers: Array<KeyManager>? = null,
            trustManagers: Array<TrustManager>? = null
        ) = when (protocol) {
            HTTP -> ClientSystem.client()
            HTTPS -> ClientSystem.sslClient(clientKeyManagers, trustManagers)
            else -> throw UnsupportedOperationException("unsupported protocol: $protocol")
        }
    }
}

class NetworkClientException(message: String) : Exception(message)
