// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.cache.h2;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.inject.TypeLiteral;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class H2CacheTest {
  private static final TypeLiteral<String> KEY_TYPE = new TypeLiteral<String>() {};
  private static final int DEFAULT_VERSION = 1234;
  private static int dbCnt;

  private static int nextDbId() {
    return ++dbCnt;
  }

  private static H2CacheImpl<String, String> newH2CacheImpl(
      int id, Cache<String, ValueHolder<String>> mem, int version) {
    SqlStore<String, String> store =
        new SqlStore<>(
            "jdbc:h2:mem:Test_" + id,
            KEY_TYPE,
            StringSerializer.INSTANCE,
            StringSerializer.INSTANCE,
            version,
            1 << 20,
            null);
    return new H2CacheImpl<>(MoreExecutors.directExecutor(), store, KEY_TYPE, mem);
  }

  @Test
  public void get() throws ExecutionException {
    Cache<String, ValueHolder<String>> mem = CacheBuilder.newBuilder().build();
    H2CacheImpl<String, String> impl = newH2CacheImpl(nextDbId(), mem, DEFAULT_VERSION);

    assertThat(impl.getIfPresent("foo")).isNull();

    AtomicBoolean called = new AtomicBoolean();
    assertThat(
            impl.get(
                "foo",
                () -> {
                  called.set(true);
                  return "bar";
                }))
        .isEqualTo("bar");
    assertThat(called.get()).named("Callable was called").isTrue();
    assertThat(impl.getIfPresent("foo")).named("in-memory value").isEqualTo("bar");
    mem.invalidate("foo");
    assertThat(impl.getIfPresent("foo")).named("persistent value").isEqualTo("bar");

    called.set(false);
    assertThat(
            impl.get(
                "foo",
                () -> {
                  called.set(true);
                  return "baz";
                }))
        .named("cached value")
        .isEqualTo("bar");
    assertThat(called.get()).named("Callable was called").isFalse();
  }

  @Test
  public void stringSerializer() {
    String input = "foo";
    byte[] serialized = StringSerializer.INSTANCE.serialize(input);
    assertThat(serialized).isEqualTo(new byte[] {'f', 'o', 'o'});
    assertThat(StringSerializer.INSTANCE.deserialize(serialized)).isEqualTo(input);
  }

  @Test
  public void version() throws Exception {
    int id = nextDbId();
    H2CacheImpl<String, String> oldImpl = newH2CacheImpl(id, disableMemCache(), DEFAULT_VERSION);
    H2CacheImpl<String, String> newImpl =
        newH2CacheImpl(id, disableMemCache(), DEFAULT_VERSION + 1);

    assertThat(oldImpl.diskStats().space()).isEqualTo(0);
    assertThat(newImpl.diskStats().space()).isEqualTo(0);
    oldImpl.put("key", "val");
    assertThat(oldImpl.getIfPresent("key")).isEqualTo("val");
    assertThat(oldImpl.diskStats().space()).isEqualTo(12);
    assertThat(oldImpl.diskStats().hitCount()).isEqualTo(1);

    // Can't find key in cache with wrong version, but the data is still there.
    assertThat(newImpl.diskStats().requestCount()).isEqualTo(0);
    assertThat(newImpl.diskStats().space()).isEqualTo(12);
    assertThat(newImpl.getIfPresent("key")).isNull();
    assertThat(newImpl.diskStats().space()).isEqualTo(12);

    // Re-putting it via the new cache works, and uses the same amount of space.
    newImpl.put("key", "val2");
    assertThat(newImpl.getIfPresent("key")).isEqualTo("val2");
    assertThat(newImpl.diskStats().hitCount()).isEqualTo(1);
    assertThat(newImpl.diskStats().space()).isEqualTo(14);

    // Now it's no longer in the old cache.
    assertThat(oldImpl.diskStats().space()).isEqualTo(14);
    assertThat(oldImpl.getIfPresent("key")).isNull();
  }

  // TODO(dborowitz): Won't be necessary when we use a real StringSerializer in the server code.
  private enum StringSerializer implements CacheSerializer<String> {
    INSTANCE;

    @Override
    public byte[] serialize(String object) {
      return object.getBytes(UTF_8);
    }

    @Override
    public String deserialize(byte[] in) {
      // TODO(dborowitz): Consider using CharsetDecoder directly in the real implementation, to get
      // checked exceptions.
      return new String(in, UTF_8);
    }
  }

  private static <K, V> Cache<K, ValueHolder<V>> disableMemCache() {
    return CacheBuilder.newBuilder().maximumSize(0).build();
  }
}
