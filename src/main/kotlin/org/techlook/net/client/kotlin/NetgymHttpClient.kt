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

import org.techlook.net.client.ClientSystem
import org.techlook.net.client.Either
import org.techlook.net.client.SocketClient
import org.techlook.net.client.http.*
import org.techlook.net.client.http.adapters.ByteResponseListener
import org.techlook.net.client.http.adapters.Response
import org.techlook.net.client.http.adapters.StringResponse
import org.techlook.net.client.http.adapters.StringResponseListener
import org.techlook.net.client.http.client.HttpListener
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager
import kotlin.Pair
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

typealias NamedParams = Map<String, String>

/**
 * This is the wrapper around the netgym network library (https://github.com/braginxv/netgym) which aims to bring
 * high performance means from this library to perform HTTP(S) requests in Kotlin.
 *
 * @param baseUrl - common beginning part of request urls including protocol, port, domain
 * e.g. https://domain.org/base/path/
 * if there is no appropriate common part, then https://domain.org can be used for a single request
 *
 * @param connectionLifetime - HTTP 1.1 Close, Keepalive or Pipelining connection
 * @see ConnectionLifetime
 *
 * @param userAgent - User-Agent header in HTTP method
 * @param basicHeaders - set of HTTP headers presented in all requests sent via this http-client instance
 * @param pipelineSendingInterval - time interval between requests sent on a pipelining connection if it's in use
 * @param clientKeyManagers - user's certificates to authenticate it to the server
 * @param trustManagers - in addition to system certificates to authenticate a server on the client side
 *
 */
class NetgymHttpClient(
  baseUrl: URL,
  connectionLifetime: ConnectionLifetime,
  userAgent: String = AGENT_CLIENT,
  basicHeaders: NamedParams = emptyMap(),
  pipelineSendingInterval: Long = PipeliningConnection.DEFAULT_SENDING_INTERVAL,
  clientKeyManagers: Iterable<KeyManager>? = null,
  trustManagers: Iterable<TrustManager>? = null
) {
  internal val connection: HttpConnection by lazy {
    (baseUrl.port.takeIf { it > 0 } ?: baseUrl.defaultPort).let { port ->
      val keyManagers: Array<KeyManager>? = clientKeyManagers?.toList()?.toTypedArray()
      val additionalTrustManagers: Array<TrustManager>? = trustManagers?.toList()?.toTypedArray()
      when (connectionLifetime) {
        ConnectionLifetime.Closable -> SingleConnection(
          baseUrl.host,
          port,
          fromProtocol(baseUrl.protocol, keyManagers, additionalTrustManagers)
        )
        ConnectionLifetime.Sequential -> SequentialConnection(
          baseUrl.host,
          port,
          fromProtocol(baseUrl.protocol, keyManagers, additionalTrustManagers)
        )
        ConnectionLifetime.Pipelining -> PipeliningConnection(
          baseUrl.host,
          port,
          fromProtocol(baseUrl.protocol, keyManagers, additionalTrustManagers),
          pipelineSendingInterval
        )
      }
    }
  }

  private val headers = basicHeaders + Pair(AGENT_HEADER, userAgent)

  private val basePartOfUrl = baseUrl.path

  /**
   * GET HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response
   */
  suspend fun get(
    resource: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.get(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * HEAD HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in string representation
   */
  suspend fun head(
    resource: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.head(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * TRACE HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in string representation
   */
  suspend fun trace(
    resource: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.trace(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * CONNECT HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in string representation
   */
  suspend fun connect(
    resource: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.connect(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * GET HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in raw byte buffer representation
   */
  suspend fun rawGet(
    resource: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
      connection.get(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * OPTIONS HTTP method: baseUrl/resource
   * @param path - path to resource relative to baseUrl
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in string representation
   */
  suspend fun options(
    path: String,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.optionsWithUrl(basePartOfUrl + path, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * OPTIONS "*" HTTP method to get general server capabilities: baseUrl/resource
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   *
   * @return server response in string representation
   */
  suspend fun optionsServer(
    additionalHeaders: NamedParams = emptyMap(),
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.options(toTechlook(headers), listener)
    }
  }

  /**
   * PATCH HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param requestBody - request body itself
   * @param contentType - MIME-type of the content
   * @param charset - encoding charset of the request body
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in string representation
   */
  suspend fun patch(
    resource: String,
    requestBody: String,
    contentType: String,
    charset: Charset = StandardCharsets.UTF_8,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.patch(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody.toByteArray(charset), listener
      )
    }
  }

  /**
   * PUT HTTP method: baseUrl/resource
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
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.put(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody.toByteArray(charset), listener
      )
    }
  }

  /**
   * PUT HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param requestBody - request body itself
   * @param contentType - MIME-type of the content
   * @param charset - encoding charset of the request body
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in raw byte buffer representation
   */
  suspend fun rawPut(
    resource: String,
    requestBody: ByteArray,
    contentType: String,
    charset: Charset = StandardCharsets.UTF_8,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
      connection.put(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody, listener
      )
    }
  }

  /**
   * DELETE HTTP method: baseUrl/resource
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
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.delete(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody.toByteArray(charset), listener
      )
    }
  }

  /**
   * DELETE HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param requestBody - request body itself
   * @param contentType - MIME-type of the content
   * @param charset - encoding charset of the request body
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in raw byte buffer representation
   */
  suspend fun rawDelete(
    resource: String,
    requestBody: ByteArray,
    contentType: String,
    charset: Charset = StandardCharsets.UTF_8,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
      connection.delete(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody, listener
      )
    }
  }

  /**
   * POST HTTP method: baseUrl/resource
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
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.postContent(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody.toByteArray(charset), listener
      )
    }
  }

  /**
   * POST HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param requestBody - request body itself
   * @param contentType - MIME-type of the content
   * @param charset - encoding charset of the request body
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @param parameters - url-encoded parameters
   *
   * @return server response in raw byte buffer representation
   */
  suspend fun rawPost(
    resource: String,
    requestBody: ByteArray,
    contentType: String,
    charset: Charset = StandardCharsets.UTF_8,
    additionalHeaders: NamedParams = emptyMap(),
    parameters: NamedParams = emptyMap()
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
      connection.postContent(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), contentType, charset,
        requestBody, listener
      )
    }
  }

  /**
   * Url-encoded POST HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param parameters - url-encoded parameters
   * @param additionalHeaders - additional headers to base headers specified where client instance is created
   *
   * @return server response in string representation
   */
  suspend fun postWithEncodedParameters(
    resource: String,
    parameters: NamedParams,
    additionalHeaders: NamedParams = emptyMap()
  ): StringResponse = suspendCoroutine { continuation ->
    stringRequest(additionalHeaders, continuation) { headers, listener ->
      connection.postWithEncodedParameters(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * Url-encoded POST HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param parameters - url-encoded parameters
   * @param additionalHeaders - additional headers to base headers specified where client instance is created
   *
   * @return server response in raw byte buffer representation
   */
  suspend fun rawPostWithEncodedParameters(
    resource: String,
    parameters: NamedParams,
    additionalHeaders: NamedParams = emptyMap()
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
      connection.postWithEncodedParameters(basePartOfUrl + resource, toTechlook(headers), toTechlook(parameters), listener)
    }
  }

  /**
   * Multipart Form POST HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param formData - multipart form data
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @return server response in string representation
   */
  suspend fun postFormData(
    resource: String,
    formData: Set<FormEntry>,
    additionalHeaders: NamedParams = emptyMap(),
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

      connection.postFormData(basePartOfUrl + resource, toTechlook(headers), formRequest, listener)
    }
  }

  /**
   * Multipart Form POST HTTP method: baseUrl/resource
   * @param resource - path to resource relative to baseUrl
   * @param formData - multipart form data
   * @param additionalHeaders - additional headers to base headers specified when client instance was created
   * @return server response in raw byte buffer representation
   */
  suspend fun rawPostFormData(
    resource: String,
    formData: Set<FormEntry>,
    additionalHeaders: NamedParams = emptyMap(),
  ): Response = suspendCoroutine { continuation ->
    rawRequest(additionalHeaders, continuation) { headers, listener ->
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

      connection.postFormData(basePartOfUrl + resource, toTechlook(headers), formRequest, listener)
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

    underlying(headers + additionalHeaders, listener)
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

    underlying(headers + additionalHeaders, listener)
  }

  internal fun fromProtocol(
    protocol: String,
    clientKeyManagers: Array<KeyManager>? = null,
    trustManagers: Array<TrustManager>? = null
  ): SocketClient = when (protocol) {
    HTTP -> ClientSystem.client()
    HTTPS -> ClientSystem.sslClient(clientKeyManagers, trustManagers)
    else -> throw UnsupportedOperationException("unsupported protocol: $protocol")
  }

  companion object {
    private const val HTTP = "http"
    private const val HTTPS = "https"
    const val AGENT_HEADER: String = "User-Agent"
    const val AGENT_CLIENT: String = "Netgym network library (https://github.com/braginxv/netgym)"
    private val protocolOnly: Pattern = Pattern.compile("^\\w+://$")
    private val serverPart: Pattern = Pattern.compile("(^\\w+://[^/]+)")

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

    fun baseUrlFor(urls: Iterable<String>): String? {
      val basePart: () -> String = {
        urls
          .onEach {
            if (!serverPart.matcher(it).find()) {
              throw IllegalArgumentException("bad url: $it")
            }
          }.reduce { baseUrl, url ->
            val urlLength = fun(): Int {
              val lengthToCompare = min(baseUrl.length, url.length)
              for (index in 0 until lengthToCompare) {
                if (baseUrl[index].lowercaseChar() != url[index].lowercaseChar()) {
                  return index
                }
              }
              return lengthToCompare
            }()

            baseUrl.take(urlLength)
          }
      }

      val iterator = urls.iterator()
      return iterator.hasNext()
        .takeIf { it }
        .let {
          val baseUrl = basePart()
          val firstUrl = iterator.next()

          val matcher = serverPart.matcher(firstUrl)
          if (matcher.find() && baseUrl == matcher.group(1)) {
            return "$baseUrl/"
          }

          baseUrl
            .dropLastWhile { it != '/' }
            .takeUnless { protocolOnly.matcher(it).matches() }
        }
    }

    internal fun <K, V> toTechlook(keyValueSet: Map<K, V>): Set<org.techlook.net.client.http.Pair<K, V>> =
      keyValueSet.map { (key, value) ->
        org.techlook.net.client.http.Pair(key, value)
      }.toSet()
  }
}

class NetworkClientException(message: String) : Exception(message)
