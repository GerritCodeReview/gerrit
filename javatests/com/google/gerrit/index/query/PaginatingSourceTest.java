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

package com.google.gerrit.index.query;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.testing.ConfigSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class PaginatingSourceTest extends PredicateTest {

  @ConfigSuite.Parameter public Config config;

  private static class TestPaginatedSource implements DataSource<String>, Paginated<String> {
    private final List<String> allItems;
    private final QueryOptions options;
    private int restartCount = 0;
    private int readCount = 0;

    TestPaginatedSource(List<String> allItems, QueryOptions options) {
      this.allItems = allItems;
      this.options = options;
    }

    @Override
    public QueryOptions getOptions() {
      return options;
    }

    @Override
    public ResultSet<String> read() {
      readCount++;
      return getSlice(0, options.pageSize());
    }

    @Override
    public ResultSet<String> restart(int start) {
      return restart(start, options.pageSize());
    }

    @Override
    public ResultSet<String> restart(int start, int pageSize) {
      restartCount++;
      return getSlice(start, pageSize);
    }

    @Override
    public ResultSet<String> restart(Object searchAfter, int pageSize) {
      restartCount++;
      int start = searchAfter == null ? 0 : ((Integer) searchAfter) + 1;
      return getSlice(start, pageSize);
    }

    private ResultSet<String> getSlice(int start, int pageSize) {
      if (start >= allItems.size()) {
        return new ListResultSet<>(ImmutableList.of());
      }
      int end = Math.min(start + pageSize, allItems.size());
      List<String> slice = new ArrayList<>(allItems.subList(start, end));
      return new ListResultSet<String>(slice) {
        @Override
        public Object searchAfter() {
          return end - 1;
        }
      };
    }

    @Override
    public int getCardinality() {
      return allItems.size();
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      throw new UnsupportedOperationException();
    }
  }

  private QueryOptions createOptions(IndexConfig indexConfig, int pageSize, int limit) {
    return QueryOptions.create(
        indexConfig,
        0,
        null,
        pageSize,
        indexConfig.pageSizeMultiplier(),
        limit,
        /* allowIncompleteResults= */ false,
        ImmutableSet.of());
  }

  @Test
  public void read_doesNotRestartWhenFirstPageMeetsLimit() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    List<String> items =
        IntStream.range(0, 100).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    // limit 26 (e.g. 25 + 1 probe), pageSize 26
    QueryOptions options = createOptions(indexConfig, 26, 26);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    PaginatingSource<String> paginatingSource = new PaginatingSource<>(source, 0, indexConfig);
    ImmutableList<String> results = paginatingSource.read().toList();

    assertThat(results).hasSize(26);
    assertThat(source.readCount).isEqualTo(1);
    // Crucial check: no second query issued since the first query already returned 26 items
    assertThat(source.restartCount).isEqualTo(0);
  }

  @Test
  public void read_doesNotRestartWhenResultsFewerThanLimit() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    List<String> items =
        IntStream.range(0, 10).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    QueryOptions options = createOptions(indexConfig, 26, 26);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    PaginatingSource<String> paginatingSource = new PaginatingSource<>(source, 0, indexConfig);
    ImmutableList<String> results = paginatingSource.read().toList();

    assertThat(results).hasSize(10);
    assertThat(source.readCount).isEqualTo(1);
    assertThat(source.restartCount).isEqualTo(0);
  }

  @Test
  public void read_restartsToBackfillWhenVisibleResultsLessThanLimit() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    // 100 items, only even-indexed items match
    List<String> items =
        IntStream.range(0, 100).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    // pageSize 10, limit 15
    QueryOptions options = createOptions(indexConfig, 10, 15);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    Predicate<String> filter = item -> Integer.parseInt(item.substring("item-".length())) % 2 == 0;

    PaginatingSource<String> paginatingSource =
        new PaginatingSource<>(source, 0, indexConfig) {
          @Override
          protected boolean match(String object) {
            return filter.test(object);
          }
        };

    ImmutableList<String> results = paginatingSource.read().toList();

    // Page 1: 10 items (0..9) -> 5 match (item-0, 2, 4, 6, 8)
    // Page 2: 10 items (10..19) -> 5 match (item-10, 12, 14, 16, 18) [total: 10]
    // Page 3: 10 items (20..29) -> 5 match (item-20, 22, 24, 26, 28) [total: 15] -> limit reached!
    assertThat(results).hasSize(15);
    assertThat(source.readCount).isEqualTo(1);
    // Restarted exactly 2 times to reach 15 items, never issued an unnecessary 3rd restart
    assertThat(source.restartCount).isEqualTo(2);
  }

  @Test
  public void read_breaksEarlyOnSubsequentPageWhenLimitReached() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    List<String> items =
        IntStream.range(0, 100).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    // pageSize 10, limit 15 (page 1 gives 10 items, page 2 only needs to yield 5 items)
    QueryOptions options = createOptions(indexConfig, 10, 15);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    PaginatingSource<String> paginatingSource = new PaginatingSource<>(source, 0, indexConfig);
    ImmutableList<String> results = paginatingSource.read().toList();

    assertThat(results).hasSize(15);
    assertThat(source.readCount).isEqualTo(1);
    assertThat(source.restartCount).isEqualTo(1);
  }

  @Test
  public void read_withStartOffsetDoesNotRestartWhenLimitMet() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    List<String> items =
        IntStream.range(0, 100).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    // start=10, limit=25 + 1 probe = 26. convertForBackend gives limit=36, pageSize=36
    QueryOptions options = createOptions(indexConfig, 36, 36);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    PaginatingSource<String> paginatingSource = new PaginatingSource<>(source, 10, indexConfig);
    ImmutableList<String> results = paginatingSource.read().toList();

    // 36 items read, start=10 dropped, leaving 26 items
    assertThat(results).hasSize(26);
    assertThat(source.readCount).isEqualTo(1);
    assertThat(source.restartCount).isEqualTo(0);
  }

  @Test
  public void read_noLimitQueryPaginatesUntilExhaustion() {
    IndexConfig indexConfig = IndexConfig.fromConfig(config).build();
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return;
    }

    List<String> items =
        IntStream.range(0, 30).mapToObj(i -> "item-" + i).collect(Collectors.toList());
    // pageSize 10, limit Integer.MAX_VALUE
    QueryOptions options = createOptions(indexConfig, 10, Integer.MAX_VALUE);
    TestPaginatedSource source = new TestPaginatedSource(items, options);

    PaginatingSource<String> paginatingSource = new PaginatingSource<>(source, 0, indexConfig);
    ImmutableList<String> results = paginatingSource.read().toList();

    assertThat(results).hasSize(30);
    assertThat(source.readCount).isEqualTo(1);
    // Page 1: 0..9 (10 items), restart 1: 10..19 (10 items), restart 2: 20..29 (10 items), restart
    // 3: empty (0 items)
    assertThat(source.restartCount).isEqualTo(3);
  }
}
