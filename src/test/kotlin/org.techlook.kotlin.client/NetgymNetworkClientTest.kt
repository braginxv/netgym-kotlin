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

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.techlook.net.client.SocketClient
import org.techlook.net.client.http.client.HttpListener
import org.techlook.net.client.kotlin.ConnectionLifetime
import org.techlook.net.client.kotlin.NetgymHttpClient
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ForkJoinPool
import kotlin.test.assertNull


@ExtendWith(MockKExtension::class)
class NetgymNetworkClientTest {
  private val host = "test.domain.org"
  private val port = 443
  private val transportChannelId = 1234
  val agentHeader = with(NetgymHttpClient) {
    AGENT_HEADER to AGENT_CLIENT
  }

  private val basicHeaders = mapOf(
    "Basic header 1" to "value1",
    "Basic header 2" to "value2",
    "Basic header 3" to "value3"
  )
  private val requestHeaders = mapOf(
    "Request header 1" to "value1",
    "Request header 2" to "value2",
    "Request header 3" to "value3"
  )
  private val parameters = mapOf(
    "param1" to "value1",
    "param2" to "value2",
    "param3" to "value3"
  )
  private val content = """
            test content
            to be sent
        """.trimIndent()
  private val baseUrl = "/base/url"
  private val resource = "/test/path"
  private val contentType = "text/plain"
  private var client: NetgymHttpClient = spyk(NetgymHttpClient(
    URL("https://test.domain.org$baseUrl"), ConnectionLifetime.Closable, basicHeaders = basicHeaders))

  @MockK
  lateinit var mockTransportLayer: SocketClient

  init {
    MockKAnnotations.init(this)
    every { mockTransportLayer.threadPool } returns ForkJoinPool.commonPool()
    every { client.fromProtocol(any(), any(), any()) } returns mockTransportLayer
  }


  @Test
  fun `test a base url assembly when whole url parts match`() {
    val urls = listOf(
      "protocol://www.domain.org/common/path/first/sub/",
      "protocol://www.domain.org/common/path/first/sub/resource.txt",
      "protocol://www.domain.org/common/path/firstpath/sub/request?param1=value1&param2=value2",
      "protocol://www.domain.org/common/path/second/path/request?param1=value1&param2=value2"
    )

    assertEquals("protocol://www.domain.org/common/path/", NetgymHttpClient.baseUrlFor(urls))
  }

  @Test
  fun `test a base url assembly when partial url parts match`() {
    val urls = listOf(
      "protocol://www.domain.org/common/path/first/sub/",
      "protocol://www.domain.org/common/path/first/sub/resource.txt",
      "protocol://www.domain.org/common/path/firstpath/sub/request?param1=value1&param2=value2",
      "protocol://www.domain.org/common/path/firstpath/request?param1=value1&param2=value2"
    )

    assertEquals("protocol://www.domain.org/common/path/", NetgymHttpClient.baseUrlFor(urls))
  }

  @Test
  fun `test there is no base url when hosts are different`() {
    val urls = listOf(
      "protocol://www.domain1.org/common/path/first/sub/",
      "protocol://www.domain2.org/common/path/first/sub/resource.txt",
      "protocol://www.domain3.org/common/path/firstpath/sub/request?param1=value1&param2=value2",
      "protocol://www.domain4.org/common/path/firstpath/request?param1=value1&param2=value2"
    )

    assertNull(NetgymHttpClient.baseUrlFor(urls))
  }

  @Test
  fun `test a common domain`() {
    val urls = listOf(
      "protocol://www.domain.org",
      "protocol://www.domain.org/common/path/first/sub/resource.txt",
      "protocol://www.domain.org",
      "protocol://www.domain.org/"
    )

    assertEquals("protocol://www.domain.org/", NetgymHttpClient.baseUrlFor(urls))
  }

  @Test
  fun `test there is a bad url in the list`() {
    val urls = listOf(
      "protocol://www.domain.org",
      "protocol://www.domain.org/common/path/first/sub/resource.txt",
      "bad/url",
      "protocol://www.domain.org/"
    )

    assertThrows<IllegalArgumentException>("bad url: bad/url") { NetgymHttpClient.baseUrlFor(urls) }
  }

  @Test
  fun `test a http method filling`() = runTest {
    val httpListener = slot<HttpListener>()

    every { client.connection.put(any(), any(), any(), any(), any(), any(), capture(httpListener)) } answers {
      httpListener.captured.complete()
    }

    every {
      mockTransportLayer.connect(InetSocketAddress(host, port), any())
    } returns transportChannelId

    client.put(resource, content, contentType, StandardCharsets.UTF_8, requestHeaders, parameters)

    verify {
      client.connection.put(
        eq("$baseUrl$resource"),
        eq(NetgymHttpClient.toTechlook(basicHeaders + agentHeader + requestHeaders)),
        eq(NetgymHttpClient.toTechlook(parameters)),
        eq(contentType),
        eq(StandardCharsets.UTF_8),
        eq(content.encodeToByteArray()),
        any()
      )
    }
  }
}
