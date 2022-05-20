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
import com.google.gerrit.server.git.RefCache;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class PerThreadCacheTest extends GerritBaseTests {
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

      assertThat(cache.getWithLoader(key1, () -> "value1", null)).hasValue("value1");

      Supplier<String> neverCalled =
          () -> {
            throw new IllegalStateException("this method must not be called");
          };
      assertThat(cache.getWithLoader(key1, neverCalled, null)).hasValue("value1");
    }
  }

  @Test
  public void cleanUp() {
    PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class);
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      assertThat(cache.getWithLoader(key, () -> "value1", null)).hasValue("value1");
    }

    // Create a second cache and assert that it is not connected to the first one.
    // This ensures that the cleanup is actually working.
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      assertThat(cache.getWithLoader(key, () -> "value2", null)).hasValue("value2");
    }
  }

  @Test
  public void unloaderCalledUponCleanup() {
    AtomicBoolean unloaderCalled = new AtomicBoolean();
    PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class);
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      cache.getWithLoader(key, () -> "value1", v -> unloaderCalled.set(true));
      assertThat(unloaderCalled.get()).isFalse();
    }
    assertThat(unloaderCalled.get()).isTrue();
  }

  @Test
  public void doubleInstantiationFails() {
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      exception.expect(IllegalStateException.class);
      exception.expectMessage("called create() twice on the same request");
      PerThreadCache.create(null);
    }
  }

  @Test
  public void isAssociatedWithHttpReadonlyRequest() {
    HttpServletRequest getRequest = new FakeHttpServletRequest();
    try (PerThreadCache cache = PerThreadCache.create(getRequest)) {
      assertThat(cache.getHttpRequest()).hasValue(getRequest);
      assertThat(cache.hasReadonlyRequest()).isTrue();
      assertThat(cache.allowRefCache()).isTrue();
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
      assertThat(cache.getHttpRequest()).hasValue(putRequest);
      assertThat(cache.hasReadonlyRequest()).isFalse();
      assertThat(cache.allowRefCache()).isFalse();
    }
  }

  @Test
  public void isNotAssociatedWithHttpRequest() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.getHttpRequest()).isEmpty();
      assertThat(cache.hasReadonlyRequest()).isFalse();
      assertThat(cache.allowRefCache()).isFalse();
    }
  }

  @Test
  public void isAssociatedWithReadonlyRequest() {
    try (PerThreadCache cache = PerThreadCache.createReadOnly()) {
      assertThat(cache.allowRefCache()).isTrue();
    }
  }

  @Test
  public void openNestedTemporaryReadonlyWindows() throws Exception {

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.allowRefCache()).isFalse();

      try (ReadonlyRequestWindow outerWindow = PerThreadCache.openReadonlyRequestWindow()) {
        assertThat(cache.allowRefCache()).isTrue();

        try (ReadonlyRequestWindow innerWindow = PerThreadCache.openReadonlyRequestWindow()) {
          assertThat(cache.allowRefCache()).isTrue();
        }

        assertThat(cache.allowRefCache()).isTrue();
      }

      assertThat(cache.allowRefCache()).isFalse();
    }
  }

  static class TestCacheValue implements RefCache {
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

    public void checkStaleness() throws IllegalStateException {
      throw new IllegalStateException(STALENESS_FAILED_MESSAGE);
    }

    @Override
    public Optional<ObjectId> get(String refName) throws IOException {
      return Optional.empty();
    }

    @Override
    public void close() {}
  }

  @Test
  public void clearOutStaleEntriesAfterReadonlyWindow() throws Exception {
    Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");
    TestCacheValue cachedValue = new TestCacheValue("cached value");
    TestCacheValue updatedValue = new TestCacheValue("updated value");

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      try (ReadonlyRequestWindow outerWindow = PerThreadCache.openReadonlyRequestWindow()) {
        assertThat(PerThreadCache.get(key, () -> cachedValue, v -> {})).hasValue(cachedValue);
        assertThat(PerThreadCache.get(key, () -> updatedValue, v -> {})).hasValue(cachedValue);
      }

      assertThat(PerThreadCache.get(key, () -> updatedValue, v -> {})).hasValue(updatedValue);
    }
  }

  @Test
  public void checkForStaleEntriesAfterReadonlyWindow() {
    Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> {
                try (ReadonlyRequestWindow outerWindow =
                    PerThreadCache.openReadonlyRequestWindow()) {
                  PerThreadCache.get(
                      key, () -> new TestCacheValue(""), TestCacheValue::checkStaleness);
                }
              });
      assertThat(thrown).hasMessageThat().isEqualTo(TestCacheValue.STALENESS_FAILED_MESSAGE);
    }
  }

  @Test
  public void allowStaleEntriesAfterReadonlyWindow() {
    Key<TestCacheValue> key = PerThreadCache.Key.create(TestCacheValue.class, "key");
    TestCacheValue value = new TestCacheValue("");
    Optional<TestCacheValue> cachedValue;

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      try (ReadonlyRequestWindow window = PerThreadCache.openReadonlyRequestWindow()) {

        cachedValue = PerThreadCache.get(key, () -> value, null);
      }
    }

    assertThat(cachedValue).hasValue(value);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void disableCachingForSpecificTypes() {
    System.setProperty(
        PerThreadCache.PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY,
        String.class.getCanonicalName() + "," + Integer.class.getCanonicalName());
    try {
      try (PerThreadCache cache = PerThreadCache.createReadOnly()) {
        assertThat(
                PerThreadCache.get(
                    PerThreadCache.Key.create(String.class, "key"), () -> "oldValue", null))
            .isEmpty();
        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(String.class, "key"), () -> "newValue"))
            .isEqualTo("newValue");

        assertThat(
                PerThreadCache.get(PerThreadCache.Key.create(Integer.class, "key"), () -> 1, null))
            .isEmpty();
        assertThat(
                PerThreadCache.getOrCompute(
                    PerThreadCache.Key.create(Integer.class, "key"), () -> 2))
            .isEqualTo(2);

        assertThat(PerThreadCache.get(PerThreadCache.Key.create(Long.class, "key"), () -> 1L, null))
            .hasValue(1L);
        assertThat(
                PerThreadCache.getOrCompute(PerThreadCache.Key.create(Long.class, "key"), () -> 2L))
            .isEqualTo(1L);
      }
    } finally {
      System.setProperty(PerThreadCache.PER_THREAD_CACHE_DISABLED_TYPES_PROPERTY, "");
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void enforceMaxSize() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      fillTheCache(cache);

      // Assert that the value was not persisted
      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, 1000);
      assertThat(cache.getWithLoader(key, () -> "new value", null)).isEmpty();
      String value = cache.get(key, () -> "directly served");
      assertThat(value).isEqualTo("directly served");
    }
  }

  @Test
  public void returnEmptyWhenCacheIsFull() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      fillTheCache(cache);

      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, 1000);
      assertThat(cache.getWithLoader(key, () -> "new value", null)).isEmpty();
    }
  }

  @Test
  public void unloaderNotCalledUponCleanupIfCacheWasFull() {
    AtomicBoolean unloaderCalled = new AtomicBoolean();
    PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class);
    try (PerThreadCache ignored = PerThreadCache.create(null)) {
      PerThreadCache cache = PerThreadCache.get();
      fillTheCache(cache);

      cache.getWithLoader(key, () -> "value1", v -> unloaderCalled.set(true));
    }
    assertThat(unloaderCalled.get()).isFalse();
  }

  private void fillTheCache(PerThreadCache cache) {
    for (int i = 0; i < 50; i++) {
      PerThreadCache.Key<String> key = PerThreadCache.Key.create(String.class, i);
      cache.getWithLoader(key, () -> "cached value", null);
    }
  }
}
