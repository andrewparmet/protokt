import protokt.v1.helloworld.runExample

// Native gRPC transport crashes during GrpcServer construction (segfault in
// gRPC C Core). This appears to be a kotlinx-rpc dev preview issue with the
// prebuilt native shims — the binary compiles and links but crashes at runtime.
// Tracked in: https://github.com/Kotlin/kotlinx-rpc/issues/XXX
fun main() = runExample()
