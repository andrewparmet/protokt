/*
 * Copyright (c) 2019 Toast Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.toasttab.protokt.ext

import com.google.auto.service.AutoService
import com.toasttab.protokt.Bytes
import java.net.InetAddress

@AutoService(Converter::class)
object InetAddressBytesConverter : Converter<InetAddress, Bytes> {
    override val wrapper = InetAddress::class

    override val wrapped = Bytes::class

    override fun wrap(unwrapped: Bytes): InetAddress {
        require(unwrapped.isNotEmpty()) {
            "cannot unwrap absent InetAddress"
        }
        return InetAddress.getByAddress(unwrapped.bytes)
    }

    override fun unwrap(wrapped: InetAddress): Bytes =
        Bytes(wrapped.address)
}