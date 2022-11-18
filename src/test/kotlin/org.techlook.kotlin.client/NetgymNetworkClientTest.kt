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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetgymNetworkClientTest {
    @Test
    fun testBaseUrlAssemblyWithSingleUrlParts() {
        val urls = listOf(
            "protocol://www.domain.org/common/path/first/sub/",
            "protocol://www.domain.org/common/path/first/sub/resource.txt",
            "protocol://www.domain.org/common/path/firstpath/sub/request?param1=value1&param2=value2",
            "protocol://www.domain.org/common/path/second/path/request?param1=value1&param2=value2"
        )

        assertEquals(NetgymHttpClient.baseUrlFor(urls), "protocol://www.domain.org/common/path/")
    }

    @Test
    fun testBaseUrlAssemblyWithPartialUrlParts() {
        val urls = listOf(
            "protocol://www.domain.org/common/path/first/sub/",
            "protocol://www.domain.org/common/path/first/sub/resource.txt",
            "protocol://www.domain.org/common/path/firstpath/sub/request?param1=value1&param2=value2",
            "protocol://www.domain.org/common/path/firstpath/request?param1=value1&param2=value2"
        )

        assertEquals(NetgymHttpClient.baseUrlFor(urls), "protocol://www.domain.org/common/path/")
    }
}
