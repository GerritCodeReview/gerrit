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

package com.google.gerrit.server.index.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class IndexedChangeQueryTest {
  @Test
  public void keepsOnlyChangesOfLatestPageForMatchShortcut() throws Exception {
    ChangeData c1 = change(1);
    ChangeData c2 = change(2);
    ChangeData c3 = change(3);
    PagedIndex index = new PagedIndex(ImmutableList.of(c1, c2, c3));
    IndexedChangeQuery q =
        new IndexedChangeQuery(
            index,
            ChangeStatusPredicate.forStatus(Change.Status.MERGED),
            IndexedChangeQuery.createOptions(IndexConfig.createDefault(), 0, 2, ImmutableSet.of()));

    assertThat(q.read().toList()).containsExactly(c1, c2).inOrder();
    assertThat(q.fromSourceSize()).isEqualTo(2);

    ImmutableList.Builder<ChangeData> page2 = ImmutableList.builder();
    q.restart(2, 2).forEach(page2::add);
    assertThat(page2.build()).containsExactly(c3);
    assertThat(q.fromSourceSize()).isEqualTo(1);

    // Matching c3 would lazy load the change and fail if the shortcut was not taken.
    assertThat(q.match(c3)).isTrue();
  }

  private static ChangeData change(int id) {
    return ChangeData.createForTest(
        Project.nameKey("project"), Change.id(id), 1, ObjectId.zeroId());
  }

  private static class PagedIndex extends FakeChangeIndex {
    private final List<ChangeData> changes;

    PagedIndex(List<ChangeData> changes) {
      super(FakeChangeIndex.V2);
      this.changes = changes;
    }

    @Override
    public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts) {
      List<ChangeData> page =
          changes.subList(
              Math.min(opts.start(), changes.size()),
              Math.min(opts.start() + opts.pageSize(), changes.size()));
      return new ChangeDataSource() {
        @Override
        public int getCardinality() {
          return page.size();
        }

        @Override
        public boolean hasChange() {
          return false;
        }

        @Override
        public ResultSet<ChangeData> read() {
          return new ListResultSet<>(page);
        }

        @Override
        public ResultSet<FieldBundle> readRaw() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}
