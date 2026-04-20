---
name: gerrit-caches
description: Implements or modifies caches in Gerrit's Java backend. Use when
adding a new cache, changing cache configuration, implementing CacheSerializer,
or working with CacheModule, Cache, LoadingCache, or CacheLoader in the Gerrit
project.
---

# Skill: Implementing Caches in Gerrit

Gerrit caches wrap **Guava** (`com.google.common.cache.Cache` / `LoadingCache`).
Declare via `CacheModule`; backend is Caffeine (in-memory) or H2 (persistent,
survives restarts).

---

## Declaring a Cache

Extend `CacheModule`; define a `CACHE_NAME` constant:

```java
// In-memory cache
public class MyModule extends CacheModule {
  static final String CACHE_NAME = "my_cache";

  @Override
  protected void configure() {
    cache(CACHE_NAME, String.class, MyValue.class)
        .maximumWeight(1024)
        .expireAfterWrite(Duration.ofMinutes(30));
    // Bind a loader to auto-load on miss:
    // bindLoader(CACHE_NAME).to(MyLoader.class);
  }
}
```

```java
// Persistent cache (H2-backed, survives restarts)
public class MyModule extends CacheModule {
  static final String CACHE_NAME = "my_cache";

  @Override
  protected void configure() {
    persist(CACHE_NAME, MyKey.class, MyValue.class)
        .version(1)                                    // bump on schema change → wipes disk cache
        .keySerializer(MyKey.Serializer.INSTANCE)
        .valueSerializer(MyValue.Serializer.INSTANCE)
        .diskLimit(128 << 20);                         // bytes; ≤0 falls back to in-memory
  }
}
```

Default `maximumWeight` is **1024** (set by `bindCache`). Install the module in a
server module: `install(new MyModule());`

---

## Injecting and Using

```java
@Singleton
public class MyService {
  private final Cache<String, MyValue> cache;  // or LoadingCache for auto-load

  @Inject
  MyService(@Named(MyModule.CACHE_NAME) Cache<String, MyValue> cache) {
    this.cache = cache;
  }
}
```

| Method | Behaviour |
|---|---|
| `getIfPresent(k)` | Returns `V` or `null` |
| `get(k, callable)` | Returns `V`, computing on miss |
| `loadingCache.get(k)` | Returns `V`, calls `CacheLoader` on miss |
| `put(k, v)` | Unconditional write |
| `invalidate(k)` | Remove one entry |
| `invalidateAll()` | Flush all entries |
| `size()` | Current entry count |
| `stats()` | `CacheStats` (hit rate, load count, …) |

---

## CacheLoader (auto-load on miss)

```java
@Singleton
public class MyLoader extends CacheLoader<String, MyValue> {
  @Override
  public MyValue load(String key) throws Exception {
    return loadFromBackend(key);
  }
}
```

Bind in the module's `configure()`: `bindLoader(CACHE_NAME).to(MyLoader.class);`
Inject as `@Named(CACHE_NAME) LoadingCache<String, MyValue>`.

---

## CacheSerializer (persistent caches only)

Implement `com.google.gerrit.server.cache.serialize.CacheSerializer<T>`.
Prefer Protobuf:

```java
public enum Serializer implements CacheSerializer<MyValue> {
  INSTANCE;

  @Override
  public byte[] serialize(MyValue obj) {
    return obj.toProto().toByteArray();
  }

  @Override
  public MyValue deserialize(byte[] in) throws InvalidProtocolBufferException {
    return MyValue.fromProto(MyValueProto.parseFrom(in));
  }
}
```

Built-in serializers (`c.g.g.server.cache.serialize`):

| Class | Use for |
|---|---|
| `StringCacheSerializer` | `String` keys/values |
| `ObjectIdCacheSerializer` | `ObjectId` keys/values |
| `BooleanCacheSerializer` | `Boolean` values |
| `JavaCacheSerializer` | Legacy Java serialization — avoid for new code |

---

## Key Rules

- Holder class must be `@Singleton`.
- Bump `version(n)` on persistent caches whenever key/value serialization changes (wipes the H2 table).
- Call `invalidate(key)` after any write that changes cached state.
- Prefer Protobuf over Java serialization for `CacheSerializer`.
- Cache names must be globally unique across the server; use `snake_case`.
- `PerThreadCache` (`c.g.g.server.cache`) provides a request-scoped, non-persistent deduplication cache for within-request use.

---
