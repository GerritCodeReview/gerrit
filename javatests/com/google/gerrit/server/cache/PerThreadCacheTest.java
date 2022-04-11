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

import com.github.dockerjava.api.model.Repository;
import com.google.gerrit.server.cache.PerThreadCache.Key;
import com.google.gerrit.server.cache.PerThreadCache.ReadonlyRequestWindow;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PerThreadCacheTest {
  @Rule public ExpectedException exception = ExpectedException.none();

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
      exception.expect(IllegalStateException.class);
      exception.expectMessage("called create() twice on the same request");
      PerThreadCache.create(null);
    }
  }

  @Test
  public void isAssociatedWithHttpReadonlyRequest() {
    HttpServletRequest getRequest = new FakeHttpServletRequest();
    try (PerThreadCache cache = PerThreadCache.create(getRequest)) {
      assertThat(cache.isReadonlyRequest(Repository.class)).isTrue();
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
      assertThat(cache.isReadonlyRequest(Repository.class)).isFalse();
    }
  }

  @Test
  public void isNotAssociatedWithHttpRequest() {
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.isReadonlyRequest(Repository.class)).isFalse();
    }
  }

  @Test
  public void isAssociatedWithReadonlyRequest() {
    try (PerThreadCache cache = PerThreadCache.createReadOnly()) {
      assertThat(cache.isReadonlyRequest(Repository.class)).isTrue();
    }
  }

  @Test
  public void openTemporaryReadonlyRequestWindowForSpecificKeyType() throws Exception {
    Class<?> readOnlyKeyType = Repository.class;
    Class<?> otherKeyType = ObjectId.class;
    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isFalse();
      assertThat(cache.isReadonlyRequest(otherKeyType)).isFalse();

      try (ReadonlyRequestWindow readOnlyWindow =
          PerThreadCache.openReadonlyRequestWindow(readOnlyKeyType)) {
        assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isTrue();
        assertThat(cache.isReadonlyRequest(otherKeyType)).isFalse();
      }

      assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isFalse();
      assertThat(cache.isReadonlyRequest(otherKeyType)).isFalse();
    }
  }

  @Test
  public void openNestedTemporaryReadonlyWindows() throws Exception {
    Class<?> readOnlyKeyType = Repository.class;

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isFalse();

      try (ReadonlyRequestWindow outerWindow =
          PerThreadCache.openReadonlyRequestWindow(readOnlyKeyType)) {
        assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isTrue();

        try (ReadonlyRequestWindow innerWindow =
            PerThreadCache.openReadonlyRequestWindow(readOnlyKeyType)) {
          assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isTrue();
        }

        assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isTrue();
      }

      assertThat(cache.isReadonlyRequest(readOnlyKeyType)).isFalse();
    }
  }

  @Test
  public void clearOutStaleEntriesAfterReadonlyWindow() throws Exception {
    Class<String> keyType = String.class;
    Key<String> key = PerThreadCache.Key.create(keyType, "key");

    try (PerThreadCache cache = PerThreadCache.create(null)) {
      try (ReadonlyRequestWindow outerWindow = PerThreadCache.openReadonlyRequestWindow(keyType)) {
        assertThat(PerThreadCache.getOrCompute(key, () -> "cached value"))
            .isEqualTo("cached value");
        assertThat(PerThreadCache.getOrCompute(key, () -> "updated value"))
            .isEqualTo("cached value");
      }

      assertThat(PerThreadCache.getOrCompute(key, () -> "updated value"))
          .isEqualTo("updated value");
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
