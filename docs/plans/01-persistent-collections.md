# Plan: Persistent Collections via kotlinx-collections-immutable

**Status:** Proposed
**Priority:** High
**Issue:** N/A (new feature)

## Problem

When copying a protokt message via `copy()`, every repeated field and map field is
fully duplicated. The generated `copy()` function:

1. Creates a new `Builder`
2. Assigns every field from the original message into the builder
3. For each repeated/map field, the builder setter calls `copyList()`/`copyMap()` (to be renamed `freezeList()`/`freezeMap()`)
4. These functions create a new `ArrayList`/`LinkedHashMap` from the original collection
5. Wraps it in `UnmodifiableList`/`UnmodifiableMap`

This means copying a message with large repeated fields is O(n) per collection, even
if none of the collections are being modified. For messages with multiple large
repeated fields this cost compounds.

### Current implementation details

**`copyList` (Collections.kt:46-52):**
```kotlin
fun <T> copyList(list: List<T>): List<T> =
    when {
        list.isEmpty() -> list
        list is UnmodifiableList -> list       // <-- fast path exists
        else -> UnmodifiableList(ArrayList(list))
    }
```

The `UnmodifiableList` fast path means that if you assign a list that came directly
from another protokt message (already wrapped in `UnmodifiableList`), no copy occurs.
However, this only helps when the exact same list instance is passed through. Any
intermediate transformation (filtering, mapping, adding elements) loses the wrapper
and triggers a full copy.

**Builder setter (generated):**
```kotlin
var phones: List<PhoneNumber> = emptyList()
    set(newValue) {
        field = copyList(newValue)
    }
```

**`copy()` function (generated):**
```kotlin
fun copy(builder: Builder.() -> Unit): Person =
    Builder().apply {
        phones = this@Person.phones   // triggers copyList() in setter
        numbers = this@Person.numbers // triggers copyMap() in setter
        builder()
    }.build()
```

Since `this@Person.phones` is an `UnmodifiableList`, the fast path in `copyList` does
fire here and avoids the copy. The real cost comes from user code that passes regular
lists to builders or when building messages incrementally.

**Where the real wins are:**

1. **Deserialization accumulator lists.** During deserialization, repeated fields are
   accumulated into `MutableList<T>`. At the end, these are wrapped in
   `UnmodifiableList(mutableList)`. The mutable list is the backing store. With
   persistent collections, the deserialized list would already be a persistent
   structure that's cheap to share.

2. **User-constructed messages with large lists.** When users build messages by passing
   regular `List<T>` values, `copyList` must copy the entire list. With persistent
   collections, the builder could accept `PersistentList<T>` and avoid copying.

3. **Incremental building.** Users building up a list field across multiple builder
   invocations currently copy on every assignment. Persistent list builders allow
   efficient incremental construction with structural sharing.

## Proposed Solution

Add a **beta plugin option** that causes deserialization and builder construction to
produce `PersistentList`/`PersistentMap` (from
[kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable))
instead of the current `UnmodifiableList`/`UnmodifiableMap` wrappers.

### Dependency

Add kotlinx-collections-immutable 0.4.0 as a **`compileOnly`** dependency of
`protokt-runtime`, tracked in the version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kotlinx-collectionsImmutable = "0.4.0"

[libraries]
kotlinx-collectionsImmutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-collectionsImmutable" }
```

This is `compileOnly` — not a transitive dependency for users. The runtime code
references `PersistentList`/`PersistentMap` in `instanceof` branches, but these
branches are ordered **after** the `UnmodifiableList`/`UnmodifiableMap` checks. On
the JVM, `instanceof` class resolution is lazy — the class is only resolved when the
instruction is actually executed. When the option is off, all lists from protokt are
`UnmodifiableList`, so the `PersistentList` branch is never reached and the class is
never loaded.

When `persistentCollections = true`, the Gradle plugin adds the library as a real
`implementation` dependency to the user's project, making the classes available at
runtime.

### Plugin Option

Add a new codegen/Gradle option:

```kotlin
// In ProtoktExtension
class Generate {
    var persistentCollections = false  // beta
}
```

Passed as a plugin parameter: `persistent_collections=true`

When enabled, the Gradle plugin also adds:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
```

to the user's project dependencies.

### Runtime API: Rename and Unify, Branch Order Matters

As a beta-breaking rename, `copyList`/`unmodifiableList` are unified into **`freezeList`**
and `copyMap`/`unmodifiableMap` into **`freezeMap`**. These names accurately describe
the contract: "ensure this collection is immutable — pass through if already frozen,
otherwise snapshot and wrap." The old names are misleading (`copyList` doesn't always
copy; `unmodifiableList` is functionally identical to `copyList` with null handling).

The new functions gain branches for persistent collections, ordered **after** the
existing known-type checks:

```kotlin
@JvmStatic
fun <T> freezeList(list: List<T>): List<T> =
    when {
        list.isEmpty() -> list
        list is UnmodifiableList -> list       // our type — checked first
        list is PersistentList -> list         // only reached if not our type
        else -> UnmodifiableList(ArrayList(list))
    }

@JvmStatic
fun <K, V> freezeMap(map: Map<K, V>): Map<K, V> =
    when {
        map.isEmpty() -> emptyMap()
        map is UnmodifiableMap -> map          // our type — checked first
        map is PersistentMap -> map            // only reached if not our type
        else -> UnmodifiableMap(LinkedHashMap(map))
    }
```

**Why this is safe as `compileOnly`:**
- When `persistentCollections = false` (default): all collections produced by protokt
  are `UnmodifiableList`/`UnmodifiableMap`. The first branch matches. The
  `is PersistentList` instruction is never executed. The JVM never resolves the class.
  No `NoClassDefFoundError`.
- When `persistentCollections = true`: the Gradle plugin has added the real dependency.
  Deserialized collections are `PersistentList`/`PersistentMap` (not
  `UnmodifiableList`), so the second branch matches. The class is on the classpath.
- When a user passes a plain `ArrayList`: falls through to `else` regardless.

### Runtime Interface: ListBuilder

To keep generated code free of kotlinx-collections-immutable imports, the runtime
provides an abstraction over collection accumulation:

```kotlin
// protokt-runtime: protokt/v1/ListBuilder.kt
interface ListBuilder<T> {
    fun add(element: T)
    fun addAll(elements: Iterable<T>)
    fun build(): List<T>
}

interface MapBuilder<K, V> {
    fun put(key: K, value: V)
    fun putAll(from: Map<K, V>)
    fun build(): Map<K, V>
}

// Default implementations backed by MutableList/MutableMap
object Collections {
    // ... existing copyList/copyMap/unmodifiableList/unmodifiableMap ...

    @JvmStatic
    fun <T> mutableListBuilder(): ListBuilder<T> =
        MutableListBuilder()

    @JvmStatic
    fun <K, V> mutableMapBuilder(): MapBuilder<K, V> =
        MutableMapBuilder()
}

private class MutableListBuilder<T> : ListBuilder<T> {
    private val list = mutableListOf<T>()
    override fun add(element: T) { list.add(element) }
    override fun addAll(elements: Iterable<T>) { list.addAll(elements) }
    override fun build(): List<T> = UnmodifiableList(list)
}

private class MutableMapBuilder<K, V> : MapBuilder<K, V> {
    private val map = mutableMapOf<K, V>()
    override fun put(key: K, value: V) { map[key] = value }
    override fun putAll(from: Map<K, V>) { map.putAll(from) }
    override fun build(): Map<K, V> = UnmodifiableMap(map)
}
```

The persistent implementations live in a **separate class** that is only loaded when
referenced — this is what makes `compileOnly` work:

```kotlin
// protokt-runtime: protokt/v1/PersistentCollections.kt
// This class is ONLY loaded when the codegen option is enabled.
// Its static initializer / method bodies reference PersistentList, which is
// fine because the Gradle plugin has added the real dependency.
object PersistentCollections {
    @JvmStatic
    fun <T> listBuilder(): ListBuilder<T> =
        PersistentListBuilder()

    @JvmStatic
    fun <K, V> mapBuilder(): MapBuilder<K, V> =
        PersistentMapBuilder()
}

private class PersistentListBuilder<T> : ListBuilder<T> {
    private val builder = persistentListOf<T>().builder()
    override fun add(element: T) { builder.add(element) }
    override fun addAll(elements: Iterable<T>) { builder.addAll(elements) }
    override fun build(): List<T> = builder.build()
}

private class PersistentMapBuilder<K, V> : MapBuilder<K, V> {
    private val builder = persistentMapOf<K, V>().builder()
    override fun put(key: K, value: V) { builder[key] = value }
    override fun putAll(from: Map<K, V>) { builder.putAll(from) }
    override fun build(): Map<K, V> = builder.build()
}
```

### Code Generation Changes

**Generated message properties (unchanged API):**
```kotlin
// Property type stays as List<T> — PersistentList implements List
val phones: List<PhoneNumber>
```

**Builder properties (no change to setter — still calls `freezeList`):**
```kotlin
var phones: List<PhoneNumber> = emptyList()
    set(newValue) {
        field = freezeList(newValue)  // persistent lists pass through
    }
```

**Deserialization — uses `ListBuilder` interface (both modes):**

When `persistentCollections = false`:
```kotlin
override fun deserialize(deserializer: KtMessageDeserializer): Person {
    var phones: ListBuilder<PhoneNumber>? = null
    // ...
    while (true) {
        when (deserializer.readTag()) {
            0 -> return Person(
                phones?.build() ?: emptyList(),
                // ...
            )
            10 -> (phones ?: Collections.mutableListBuilder<PhoneNumber>().also { phones = it })
                .add(PhoneNumber.deserialize(deserializer))
            // ...
        }
    }
}
```

When `persistentCollections = true`:
```kotlin
override fun deserialize(deserializer: KtMessageDeserializer): Person {
    var phones: ListBuilder<PhoneNumber>? = null
    // ...
    while (true) {
        when (deserializer.readTag()) {
            0 -> return Person(
                phones?.build() ?: emptyList(),
                // ...
            )
            10 -> (phones ?: PersistentCollections.listBuilder<PhoneNumber>().also { phones = it })
                .add(PhoneNumber.deserialize(deserializer))
            // ...
        }
    }
}
```

The only difference in generated code is the factory call: `Collections.mutableListBuilder()`
vs `PersistentCollections.listBuilder()`. All types in the generated code are from the
protokt runtime — no direct imports of kotlinx-collections-immutable.

**Builder `build()` function (no change needed):**
```kotlin
fun build(): Person = Person(
    freezeList(phones),   // PersistentList passes through unchanged
    freezeMap(numbers),
    // ...
)
```

### What Changes in the Codegen

| File | Change |
|------|--------|
| `PluginParams.kt` | Add `persistent_collections` parameter parsing |
| `ProtoktExtension.kt` | Add `persistentCollections` property to `Generate` |
| `DeserializerGenerator.kt` | Use `ListBuilder`/`MapBuilder` as accumulator type; emit `Collections.mutableListBuilder()` or `PersistentCollections.listBuilder()` based on option |
| `DeserializerSupport.kt` | Emit `?.build() ?: emptyList()` at constructor wrapping; use `freezeList`/`freezeMap` |
| `KotlinPoetUtil.kt` | Update member references from `copyList`/`copyMap` → `freezeList`/`freezeMap`; remove `unmodifiableList`/`unmodifiableMap` references |
| `BuilderGenerator.kt` | Update setter codegen to emit `freezeList`/`freezeMap` |
| `Collections.kt` (runtime) | Rename `copyList`/`unmodifiableList` → `freezeList`, `copyMap`/`unmodifiableMap` → `freezeMap`; add `PersistentList`/`PersistentMap` pass-through branches (after known-type checks); add `ListBuilder`/`MapBuilder` interfaces and `MutableListBuilder`/`MutableMapBuilder` |
| `PersistentCollections.kt` (runtime, new) | `PersistentCollections` object with `listBuilder()`/`mapBuilder()` factory methods and their implementations |
| `gradle/libs.versions.toml` | Add kotlinx-collections-immutable 0.4.0 |
| `protokt-runtime/build.gradle.kts` | Add `compileOnly` dependency |
| Gradle plugin | Pass `persistent_collections` parameter to protoc plugin; add `implementation` dependency to user's project when enabled |

### What Does NOT Change

- **Runtime API.** `freezeList`/`freezeMap` (renamed from `copyList`/`unmodifiableList`
  and `copyMap`/`unmodifiableMap`) have the same behavior as before. They gain new
  branches but existing behavior is unchanged.
- **Public API of generated messages.** Properties remain `List<T>` and `Map<K, V>`.
  `PersistentList<T>` implements `List<T>`, so this is binary compatible.
- **Generated builder setter code.** Still calls `freezeList`/`freezeMap`.
- **Generated code imports.** Only `protokt.v1.*` — no kotlinx-collections-immutable
  imports in generated code.
- **Serialization/deserialization wire format.** No change.
- **`copy()` function signature.** Same DSL builder pattern.
- **Existing non-beta behavior.** The option defaults to `false`.

### Migration Path

1. User enables `persistentCollections = true` in their Gradle config
2. Regenerate protobuf code
3. All existing code continues to work (API-compatible)
4. Performance improvements are automatic for `copy()` and message construction

### Risks and Considerations

- **`compileOnly` correctness.** Relies on JVM lazy class resolution for `instanceof`
  instructions. This is specified JVM behavior, not an implementation detail — the
  class is resolved on first execution of the instruction, not at verification time.
  Kotlin/JS and Kotlin/Native have analogous lazy behavior.
- **Deserialization performance.** `PersistentList.Builder` builds a trie
  incrementally. This is slightly more work per `add()` than `ArrayList.add()` (trie
  node allocation vs. amortized array copy). For deserialization-heavy workloads with
  small lists, this could be a minor regression. For large lists that are subsequently
  copied, the structural sharing wins.
- **Memory overhead.** Persistent data structures use more memory per element than
  `ArrayList` due to trie nodes. For small collections (< ~10 elements) this may be
  worse than copying. For large collections the structural sharing wins.
- **Multiplatform.** kotlinx-collections-immutable supports all Kotlin targets
  (JVM, JS, Native), so this is compatible with protokt's multiplatform story.

### Testing Plan

- All existing tests should continue to pass with the option enabled
- Add specific tests for:
  - Message `copy()` with large repeated fields (verify structural sharing)
  - Builder construction with persistent vs. regular lists
  - Deserialization round-trip with persistent collections
  - Interop: passing regular `List` to a builder that uses persistent collections
- Consider adding microbenchmarks comparing copy cost with and without persistent
  collections

### Stretch Goals

- Explore exposing `PersistentList<T>` in the generated property type (breaking API
  change, separate opt-in)
