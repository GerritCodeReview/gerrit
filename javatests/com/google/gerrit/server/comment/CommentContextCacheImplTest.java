// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.comment;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.Project;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommentContextCacheImplTest {

  private static final Project.NameKey PROJECT = Project.nameKey("my-project");
  private static final Change.Id CHANGE_ID = Change.id(123);

  private CommentContextKey createKey(String id, String path, int patchset, int padding) {
    return CommentContextKey.builder()
        .project(PROJECT)
        .changeId(CHANGE_ID)
        .id(id)
        .path(path)
        .patchset(patchset)
        .contextPadding(padding)
        .build();
  }

  @Test
  public void getAll_deduplicatesInputKeys() {
    CommentContextKey key1 = createKey("c1", "FileA.java", 1, 3);
    CommentContextKey key1Duplicate = createKey("c1", "FileA.java", 1, 3);
    CommentContextKey key2 = createKey("c2", "FileB.java", 1, 3);

    CommentContext ctx1 = CommentContext.create(ImmutableMap.of(10, "line 10"), "text/x-java");
    CommentContext ctx2 = CommentContext.create(ImmutableMap.of(20, "line 20"), "text/x-java");

    List<CommentContextKey> requestedKeys = new ArrayList<>();
    LoadingCache<CommentContextKey, CommentContext> loadingCache =
        new AbstractLoadingCache<CommentContextKey, CommentContext>() {
          @Override
          public CommentContext get(CommentContextKey key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CommentContext getIfPresent(Object key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ImmutableMap<CommentContextKey, CommentContext> getAll(
              Iterable<? extends CommentContextKey> keys) {
            ImmutableMap.Builder<CommentContextKey, CommentContext> builder =
                ImmutableMap.builder();
            for (CommentContextKey k : keys) {
              requestedKeys.add(k);
              if (k.id().equals("c1")) {
                builder.put(k, ctx1);
              } else if (k.id().equals("c2")) {
                builder.put(k, ctx2);
              }
            }
            return builder.build();
          }
        };

    CommentContextCacheImpl cache = new CommentContextCacheImpl(loadingCache);
    ImmutableMap<CommentContextKey, CommentContext> result =
        cache.getAll(ImmutableList.of(key1, key1Duplicate, key2));

    // Verify cache was queried with only 2 unique keys
    assertThat(requestedKeys).hasSize(2);

    assertThat(result).hasSize(2);
    assertThat(result.get(key1)).isEqualTo(ctx1);
    assertThat(result.get(key2)).isEqualTo(ctx2);
  }

  @Test
  public void getAll_handlesMissingCacheEntries() {
    CommentContextKey key1 = createKey("c1", "FileA.java", 1, 3);
    CommentContextKey key2 = createKey("c2", "FileB.java", 1, 3);

    CommentContext ctx1 = CommentContext.create(ImmutableMap.of(10, "line 10"), "text/x-java");

    LoadingCache<CommentContextKey, CommentContext> loadingCache =
        new AbstractLoadingCache<CommentContextKey, CommentContext>() {
          @Override
          public CommentContext get(CommentContextKey key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CommentContext getIfPresent(Object key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ImmutableMap<CommentContextKey, CommentContext> getAll(
              Iterable<? extends CommentContextKey> keys) {
            ImmutableMap.Builder<CommentContextKey, CommentContext> builder =
                ImmutableMap.builder();
            for (CommentContextKey k : keys) {
              if (k.id().equals("c1")) {
                builder.put(k, ctx1);
              }
            }
            return builder.build();
          }
        };

    CommentContextCacheImpl cache = new CommentContextCacheImpl(loadingCache);
    ImmutableMap<CommentContextKey, CommentContext> result =
        cache.getAll(ImmutableList.of(key1, key2));

    assertThat(result).hasSize(1);
    assertThat(result.get(key1)).isEqualTo(ctx1);
    assertThat(result.containsKey(key2)).isFalse();
  }

  @Test
  public void getAll_adjustsNegativeContextPaddingToZero() {
    CommentContextKey key = createKey("c1", "FileA.java", 1, -5);

    List<CommentContextKey> requestedKeys = new ArrayList<>();
    LoadingCache<CommentContextKey, CommentContext> loadingCache =
        new AbstractLoadingCache<CommentContextKey, CommentContext>() {
          @Override
          public CommentContext get(CommentContextKey key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CommentContext getIfPresent(Object key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ImmutableMap<CommentContextKey, CommentContext> getAll(
              Iterable<? extends CommentContextKey> keys) {
            Iterables.addAll(requestedKeys, keys);
            return ImmutableMap.of();
          }
        };

    CommentContextCacheImpl cache = new CommentContextCacheImpl(loadingCache);
    ImmutableMap<CommentContextKey, CommentContext> result = cache.getAll(ImmutableList.of(key));

    assertThat(result).isEmpty();
    assertThat(requestedKeys).hasSize(1);
    assertThat(requestedKeys.get(0).contextPadding()).isEqualTo(0);
  }

  @Test
  public void getAll_adjustsExcessiveContextPaddingToMax() {
    CommentContextKey key =
        createKey("c1", "FileA.java", 1, CommentContextCacheImpl.MAX_CONTEXT_PADDING + 20);

    List<CommentContextKey> requestedKeys = new ArrayList<>();
    LoadingCache<CommentContextKey, CommentContext> loadingCache =
        new AbstractLoadingCache<CommentContextKey, CommentContext>() {
          @Override
          public CommentContext get(CommentContextKey key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CommentContext getIfPresent(Object key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ImmutableMap<CommentContextKey, CommentContext> getAll(
              Iterable<? extends CommentContextKey> keys) {
            Iterables.addAll(requestedKeys, keys);
            return ImmutableMap.of();
          }
        };

    CommentContextCacheImpl cache = new CommentContextCacheImpl(loadingCache);
    ImmutableMap<CommentContextKey, CommentContext> result = cache.getAll(ImmutableList.of(key));

    assertThat(result).isEmpty();
    assertThat(requestedKeys).hasSize(1);
    assertThat(requestedKeys.get(0).contextPadding())
        .isEqualTo(CommentContextCacheImpl.MAX_CONTEXT_PADDING);
  }

  @Test
  public void get_singleKey() {
    CommentContextKey key = createKey("c1", "FileA.java", 1, 3);
    CommentContext ctx = CommentContext.create(ImmutableMap.of(5, "code line"), "text/x-java");

    LoadingCache<CommentContextKey, CommentContext> loadingCache =
        new AbstractLoadingCache<CommentContextKey, CommentContext>() {
          @Override
          public CommentContext get(CommentContextKey key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CommentContext getIfPresent(Object key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ImmutableMap<CommentContextKey, CommentContext> getAll(
              Iterable<? extends CommentContextKey> keys) {
            ImmutableMap.Builder<CommentContextKey, CommentContext> builder =
                ImmutableMap.builder();
            for (CommentContextKey k : keys) {
              builder.put(k, ctx);
            }
            return builder.build();
          }
        };

    CommentContextCacheImpl cache = new CommentContextCacheImpl(loadingCache);
    CommentContext result = cache.get(key);
    assertThat(result).isEqualTo(ctx);
  }

  @Test
  public void commentContextSerializer_roundTrip_multiLineContext() {
    CommentContext original =
        CommentContext.create(
            ImmutableMap.of(
                1, "public class Foo {",
                2, "  public void bar() {",
                3, "    return;",
                4, "  }",
                5, "}"),
            "text/x-java");

    byte[] serialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.serialize(original);
    assertThat(serialized).isNotEmpty();

    CommentContext deserialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.deserialize(serialized);
    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.lines()).isEqualTo(original.lines());
    assertThat(deserialized.contentType()).isEqualTo("text/x-java");
  }

  @Test
  public void commentContextSerializer_roundTrip_emptyContext() {
    CommentContext original = CommentContext.empty();

    byte[] serialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.serialize(original);
    assertThat(serialized).isNotNull();

    CommentContext deserialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.deserialize(serialized);
    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.lines()).isEmpty();
    assertThat(deserialized.contentType()).isEmpty();
  }

  @Test
  public void commentContextSerializer_roundTrip_emptyLinesAndWhitespace() {
    CommentContext original =
        CommentContext.create(
            ImmutableMap.of(
                10, "",
                11, "   ",
                12, "\t\t",
                13, "non-empty line"),
            "text/plain");

    byte[] serialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.serialize(original);
    assertThat(serialized).isNotEmpty();

    CommentContext deserialized =
        CommentContextCacheImpl.CommentContextSerializer.INSTANCE.deserialize(serialized);
    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.lines()).isEqualTo(original.lines());
    assertThat(deserialized.contentType()).isEqualTo("text/plain");
  }
}
