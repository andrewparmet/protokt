/*
 * Copyright (c) 2026 Toast, Inc.
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

package protokt.v1.helloworld

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.grpc.marshaller.GrpcMarshallerResolver
import kotlinx.rpc.grpc.server.GrpcServer
import kotlinx.rpc.registerService
import kotlinx.rpc.withService
import protokt.v1.grpc.krpc.ProtoktGrpcMarshaller
import protokt.v1.grpc.krpc.ProtoktMarshallerResolver
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.milliseconds

internal class GreeterService : Greeter {
    override suspend fun SayHello(message: HelloRequest) =
        HelloReply { this.message = "Hello ${message.name}" }
}

val marshallerResolver: GrpcMarshallerResolver =
    ProtoktMarshallerResolver(
        mapOf(
            typeOf<HelloRequest>() to ProtoktGrpcMarshaller(HelloRequest),
            typeOf<HelloReply>() to ProtoktGrpcMarshaller(HelloReply),
        )
    )

suspend fun client(user: String, port: Int) {
    val client =
        GrpcClient("localhost", port) {
            messageMarshallerResolver = marshallerResolver
            credentials = plaintext()
        }

    val greeter = client.withService<Greeter>()
    val reply = greeter.SayHello(HelloRequest { name = user })
    println("Received: ${reply.message}")
}

fun runExample() {
    runBlocking {
        val port = 50051

        val server =
            GrpcServer(port) {
                messageMarshallerResolver = marshallerResolver
                services {
                    registerService<Greeter> { GreeterService() }
                }
            }
        server.start()
        println("Server started on port $port")

        launch {
            delay(100.milliseconds)
            client("Native World", port)
            server.shutdownNow()
        }

        server.awaitTermination()
    }
}
