# Plan: JS gRPC Runtime Completion

**Status:** Proposed
**Priority:** Low
**Issue:** N/A

## Current State

The JS gRPC runtime is more complete than it might appear. A thorough audit of the
codebase shows:

### What's done

- **Full protobuf serialization/deserialization** for JS via protobuf.js
- **All 4 RPC streaming patterns** (unary, server streaming, client streaming, bidi)
  implemented for both client and server in `protokt-runtime-grpc-lite/src/jsMain/`
- **Coroutine-based API** with Flow integration matching the JVM API style
- **Code generation** fully supports JS targets — `ServiceGenerator.kt` emits
  JS-compatible stubs when `kotlinTarget == MultiplatformJs`
- **@grpc/grpc-js bindings** via Kotlin external declarations
- **Working examples**: HelloWorld, RouteGuide (all 4 streaming types), Animals
  (multi-service) in `examples/grpc-node/`
- **Protobuf conformance tests** running on JS

### What's missing

The implementation covers the happy path but lacks production hardening:

1. **End-to-end gRPC tests in CI** — Examples exist but aren't run as automated tests
2. **SSL/TLS support** — Only `createInsecure()` credentials are used
3. **Call metadata/headers** — No API for sending or receiving gRPC metadata
4. **Interceptors** — No client or server interceptor support
5. **Error handling edge cases** — Status propagation exists but isn't thoroughly tested
6. **Browser/grpc-web support** — Only Node.js is supported
7. **Deadline/timeout support** — No call deadline configuration
8. **Health checking** — No health check service implementation
9. **Reflection** — No server reflection API
10. **Load balancing / retry** — No built-in policies

## Proposed Work Packages

### Package 1: Test Infrastructure

**Goal:** Automated end-to-end gRPC tests for JS in CI.

**Approach:**
- Create a test module under `testing/` that spins up a JS gRPC server and client
- Test all 4 streaming patterns with assertion checks
- Test error propagation (server throws StatusException, client receives correct status)
- Test cancellation (client cancels a streaming call)
- Wire into the existing Gradle test lifecycle

**Key challenges:**
- Need to start a gRPC server as a background process in the test
- Node.js test runner integration with Kotlin/JS test framework
- Port management to avoid conflicts in parallel test runs

### Package 2: Metadata Support

**Goal:** Support sending and receiving gRPC metadata (headers/trailers).

**Approach:**
- Add `Metadata` class to the JS gRPC runtime
- Update `ClientCalls.kt` to accept and return metadata
- Update `ServerCalls.kt` to read request metadata and send response metadata
- Update code generation to thread metadata through stubs

**Changes:**
- `protokt-runtime-grpc-lite/src/jsMain/kotlin/protokt/v1/grpc/ClientCalls.kt`
- `protokt-runtime-grpc-lite/src/jsMain/kotlin/protokt/v1/grpc/ServerCalls.kt`
- `protokt-runtime-grpc-lite/src/jsMain/kotlin/protokt/v1/grpc/GrpcJs.kt` (external decls)

### Package 3: SSL/TLS

**Goal:** Support secure channels.

**Approach:**
- Add `createSsl()` channel credentials alongside `createInsecure()`
- Accept PEM certificates for server and client authentication
- Test with self-signed certificates in CI

**Changes:**
- `GrpcJs.kt` — add SSL credential external declarations
- `Servers.kt` — support starting server with TLS
- Examples — add secure variant

### Package 4: Interceptors

**Goal:** Client and server interceptors matching the JVM API pattern.

**Approach:**
- Define `ClientInterceptor` and `ServerInterceptor` interfaces
- Implement interceptor chaining in `ClientCalls.kt` and `ServerCalls.kt`
- Common use cases: logging, authentication, metrics

### Package 5: Deadline/Timeout

**Goal:** Support call deadlines.

**Approach:**
- Accept deadline/timeout options in client calls
- Propagate via @grpc/grpc-js options
- Cancel calls that exceed deadline

### Package 6: Browser Support (Stretch)

**Goal:** Support gRPC from browser environments via grpc-web.

**Approach:**
- This is a significant undertaking — grpc-web uses a different transport (HTTP/1.1
  with base64-encoded frames or HTTP/2 via envoy proxy)
- Would need a separate client implementation or grpc-web JS library integration
- Server-side would need an envoy proxy or grpc-web compatibility layer

**Recommendation:** Defer this until there's concrete demand. The Node.js story is
the priority.

## Prioritization

| Package | Impact | Effort | Priority |
|---------|--------|--------|----------|
| 1. Test Infrastructure | High (confidence) | Medium | **Do first** |
| 2. Metadata Support | Medium (needed for auth) | Medium | Second |
| 3. SSL/TLS | Medium (production use) | Low | Third |
| 4. Interceptors | Medium (extensibility) | Medium | Fourth |
| 5. Deadline/Timeout | Low (nice to have) | Low | Fifth |
| 6. Browser Support | High (reach) | Very High | Defer |

## Risks and Considerations

- **@grpc/grpc-js version churn.** The JS gRPC ecosystem moves faster than the JVM
  one. Pinning to 1.12.4 is fine for now but may need regular updates.
- **Kotlin/JS IR compiler maturity.** The IR backend is stable but has occasional
  codegen quirks with external declarations. Testing is essential.
- **Node.js version requirements.** @grpc/grpc-js requires Node 12+. Document this.
- **Coroutine support on JS.** kotlinx-coroutines-core for JS works but has
  differences from JVM (no Dispatchers.IO, single-threaded). The current
  implementation handles this correctly via `GlobalScope` and `promise {}`.
