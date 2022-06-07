// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.util.function.Supplier;
import org.junit.Test;

public class PerThreadCacheTest {

  @SuppressWarnings("TruthIncompatibleType")
  @Test
  public void key_respectsClass() {
    assertThat(PerThreadCache.Key.create(String.class))
        .isEqualTo(PerThreadCache.Key.create(String.class));
    assertThat(PerThreadCache.Key.create(String.class))
        .isNotEqualTo(
            /* expected: Key<String>, actual: Key<Integer> */ PerThreadCache.Key.create(
                Integer.class));
  }

  @Test
  public void key_respectsIdentifiers() {
    assertThat(PerThreadCache.Key.create(String.class, "id1"))
        .isEqualTo(PerThreadCache.Key.create(String.class, "id1"));
    assertThat(PerThreadCache.Key.create(String.class, "id1"))
        .isNotEqualTo(PerThreadCache.Key.create(String.class, "id2"));
  }

  @Test
  public void endToEndCache() {
    try (PerThreadCache ignored = PerThreadCache.create()) {
      PerThreadCache cache = PerThreadCache.get();
      PerThreadCache.Key<String> key1 = PerThreadCache.Key.create(String.class);

      String value1 = cache.get(key1, () -> "value1");
      assertThat(value1).isEqualTo("value1");

      Supplier<String> neverCalled =
          () -> {
            throw new IllegalStateException("this method must not be called");
          };
      assertThat(cache.get(key1, neverCalled)).isEqualTo("value1");
    }
  }

  @Test
  public void cleanUp() {
    PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class);
    try (PerThreadCache ignored = PerThreadCache.create()) {
      PerThreadCache cache = PerThreadCache.get();
      String value1 = cache.get(key, () -> "value1");
      assertThat(value1).isEqualTo("value1");
    }

    // Create a second cache and assert that it is not connected to the first one.
    // This ensures that the cleanup is actually working.
    try (PerThreadCache ignored = PerThreadCache.create()) {
      PerThreadCache cache = PerThreadCache.get();
      String value1 = cache.get(key, () -> "value2");
      assertThat(value1).isEqualTo("value2");
    }
  }

  @Test
  public void doubleInstantiationFails() {
    try (PerThreadCache ignored = PerThreadCache.create()) {
      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> PerThreadCache.create());
      assertThat(thrown).hasMessageThat().contains("called create() twice on the same request");
    }
  }

  @Test
  public void enforceMaxSize() {
    try (PerThreadCache cache = PerThreadCache.create()) {
      // Fill the cache
      for (int i = 0; i < 50; i++) {
        PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, i);
        cache.get(key, () -> "cached value");
      }
      // Assert that the value was not persisted
      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, 1000);
      cache.get(key, () -> "new value");
      String value = cache.get(key, () -> "directly served");
      assertThat(value).isEqualTo("directly served");
    }
  }
}
