/*
 * Copyright (c) 2023 Toast, Inc.
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

package protokt.v1

actual class CachingReference<S : Any, T : Any>(
    @Volatile private var ref: Any,
    private val converter: Converter<S, T>
) {
    actual val wrapped: S
        get() =
            ref.let {
                if (converter.wrapper.isInstance(it)) {
                    @Suppress("UNCHECKED_CAST")
                    it as S
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val converted = converter.wrap(it as T)
                    ref = converted
                    converted
                }
            }

    actual val unwrapped: T
        get() =
            ref.let {
                if (converter.wrapped.isInstance(it)) {
                    @Suppress("UNCHECKED_CAST")
                    it as T
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val converted = converter.unwrap(it as S)
                    ref = converted
                    converted
                }
            }
}
