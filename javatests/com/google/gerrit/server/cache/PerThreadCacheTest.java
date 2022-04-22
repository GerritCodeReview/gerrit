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

import com.google.gerrit.server.cache.PerThreadCache.Key;
import com.google.gerrit.server.cache.PerThreadCache.ReadonlyRequestWindow;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.junit.Test;

public class PerThreadCacheTest {
  @Test
  public void key_respectsClass() {
    assertThat(PerThreadCache.Key.create(String.class))
        .isEqualTo(PerThreadCache.Key.create(String.class));
    assertThat(PerThreadCache.Key.create(String.class))
        .isNotEqualTo(PerThreadCache.Key.create(Integer.class));
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
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
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
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      String value1 = cache.get(key, () -> "value1");
      assertThat(value1).isEqualTo("value1");
    }

    // Create a second cache and assert that it is not connected to the first one.
    // This ensures that the cleanup is actually working.
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      String value1 = cache.get(key, () -> "value2");
      assertThat(value1).isEqualTo("value2");
    }
  }

  @Test
  public void doubleInstantiationFails() {
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> PerThreadCache.create(null));
      assertThat(thrown).hasMessageThat().contains("called create() twice on the same request");
    }
  }

  @Test
  public void isAssociatedWithHttpReadonlyRequest() {
    HttpServletRequest getRequest = new FakeHttpServletRequest();
    try (PerThreadCache cache = PerThreadCache.create(getRequest)) {
      assertThat(cache.allowRepoRefsCache()).isTrue();
    }
  }

  @Test
  public void isAssociatedWithHttpWriteRequest() {
    HttpServletRequest putRequest =
        new HttpServletRequestWrapper(new FakeHttpServletRequest()) {
          @Override
          public String getMethod() {
            return "PUT";
          }
        };
    try (PerThreadCache cache = PerThreadCache.create(putRequest)) {
      assertThat(cache.allowRepoRefsCache()).isFalse();
    }
  }

  @Test
  public void isNotAssociatedWithHttpRequest() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.allowRepoRefsCache()).isFalse();
    }
  }

  @Test
  public void isAssociatedWithReadonlyRequest() {
    try (PerThreadCache cache = PerThreadCache.createReadOnly()) {
      assertThat(cache.allowRepoRefsCache()).isTrue();
    }
  }

  @Test
  public void openNestedTemporaryReadonlyWindows() throws Exception {

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.allowRepoRefsCache()).isFalse();

      try (ReadonlyRequestWindow outerWindow = PerThreadCache.openReadonlyRequestWindow()) {
        assertThat(cache.allowRepoRefsCache()).isTrue();

        try (ReadonlyRequestWindow innerWindow = PerThreadCache.openReadonlyRequestWindow()) {
          assertThat(cache.allowRepoRefsCache()).isTrue();
        }

        assertThat(cache.allowRepoRefsCache()).isTrue();
      }

      assertThat(cache.allowRepoRefsCache()).isFalse();
    }
  }

  static class TestCacheValue implements PerThreadCache.CacheStalenessCheck {
    public static final String STALENESS_FAILED_MESSAGE = "Staleness check failed";

    private final String value;

    public TestCacheValue(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      return obj != null
          && (obj instanceof TestCacheValue)
          && ((TestCacheValue) obj).value.equals(value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public void checkStaleness() throws IllegalStateException {
      throw new IllegalStateException(STALENESS_FAILED_MESSAGE);
    }
  }

  @Test
  public void clearOutStaleEntriesAfterReadonlyWindow() throws Exception {
    Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");
    TestCacheValue cachedValue = new TestCacheValue("cached value");
    TestCacheValue updatedValue = new TestCacheValue("updated value");

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      try (ReadonlyRequestWindow outerWindow = PerThreadCache.openReadonlyRequestWindow()) {
        assertThat(PerThreadCache.getOrCompute(key, () -> cachedValue)).isEqualTo(cachedValue);
        assertThat(PerThreadCache.getOrCompute(key, () -> updatedValue)).isEqualTo(cachedValue);
      }

      assertThat(PerThreadCache.getOrCompute(key, () -> updatedValue)).isEqualTo(updatedValue);
    }
  }

  @Test
  public void checkForStaleEntriesAfterReadonlyWindow() {
    try {
      System.setProperty(PerThreadCache.PER_THREAD_CACHE_CHECK_STALE_ENTRIES_PROPERTY, "true");
      Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");

      try (PerThreadCache cache = PerThreadCache.create(null)) {
        IllegalStateException thrown =
            assertThrows(
                IllegalStateException.class,
                () -> {
                  try (ReadonlyRequestWindow outerWindow =
                      PerThreadCache.openReadonlyRequestWindow()) {
                    PerThreadCache.getOrCompute(key, () -> new TestCacheValue(""));
                  }
                });
        assertThat(thrown).hasMessageThat().isEqualTo(TestCacheValue.STALENESS_FAILED_MESSAGE);
      }
    } finally {
      System.setProperty(PerThreadCache.PER_THREAD_CACHE_CHECK_STALE_ENTRIES_PROPERTY, "false");
    }
  }

  @Test
  public void allowStaleEntriesAfterReadonlyWindow() {
    Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");
    TestCacheValue value = new TestCacheValue("");
    TestCacheValue cachedValue;

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      try (ReadonlyRequestWindow window = PerThreadCache.openReadonlyRequestWindow()) {

        cachedValue = PerThreadCache.getOrCompute(key, () -> value);
      }
    }

    assertThat(cachedValue).isSameInstanceAs(value);
  }

  @Test
  public void disableCachingForSpecificTypes() {
    System.setProperty(
        PerThreadCache.PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY,
        String.class.getCanonicalName() + "," + Integer.class.getCanonicalName());
    try {
      try (PerThreadCache cache = PerThreadCache.createReadOnly()) {
        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(String.class, "key"), () -> "oldValue"))
            .isEqualTo("oldValue");
        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(String.class, "key"), () -> "newValue"))
            .isEqualTo("newValue");

        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(Integer.class, "key"), () -> 1))
            .isEqualTo(1);
        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(Integer.class, "key"), () -> 2))
            .isEqualTo(2);

        assertThat(
                PerThreadCache.getOrCompute(PerThreadCache.Key.create(Long.class, "key"), () -> 1L))
            .isEqualTo(1L);
        assertThat(
                PerThreadCache.getOrCompute(PerThreadCache.Key.create(Long.class, "key"), () -> 2L))
            .isEqualTo(1L);
      }
    } finally {
      System.setProperty(PerThreadCache.PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY, "");
    }
  }

  @Test
  public void enforceMaxSize() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
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
