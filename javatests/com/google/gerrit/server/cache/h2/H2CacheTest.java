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
import org.junit.Before;
import org.junit.Test;

public class H2CacheTest {
  private static int dbCnt;

  private Cache<String, ValueHolder<String>> mem;
  private H2CacheImpl<String, String> impl;

  @Before
  public void setUp() {
    mem = CacheBuilder.newBuilder().build();

    TypeLiteral<String> keyType = new TypeLiteral<String>() {};
    SqlStore<String, String> store =
        new SqlStore<>(
            "jdbc:h2:mem:Test_" + (++dbCnt),
            keyType,
            StringSerializer.INSTANCE,
            StringSerializer.INSTANCE,
            1 << 20,
            0);
    impl = new H2CacheImpl<>(MoreExecutors.directExecutor(), store, keyType, mem);
  }

  @Test
  public void get() throws ExecutionException {
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
}
