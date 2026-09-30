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
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.query.change.AndChangeSource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import org.junit.Test;

public class AndChangeSourceTest {
  @Test
  public void hasChangeWhenSelectedSourceHasChange() {
    AndChangeSource source =
        new AndChangeSource(
            ImmutableList.of(new TestSource("a", 10, false), new TestSource("b", 1, true)),
            IndexConfig.createDefault());

    assertThat(source.hasChange()).isTrue();
  }

  @Test
  public void noChangeWhenSelectedSourceHasNoChange() {
    AndChangeSource source =
        new AndChangeSource(
            ImmutableList.of(new TestSource("a", 1, false), new TestSource("b", 10, true)),
            IndexConfig.createDefault());

    assertThat(source.hasChange()).isFalse();
  }

  private static class TestSource extends OperatorPredicate<ChangeData>
      implements ChangeDataSource {
    private final int cardinality;
    private final boolean hasChange;

    TestSource(String value, int cardinality, boolean hasChange) {
      super("test", value);
      this.cardinality = cardinality;
      this.hasChange = hasChange;
    }

    @Override
    public int getCardinality() {
      return cardinality;
    }

    @Override
    public boolean hasChange() {
      return hasChange;
    }

    @Override
    public ResultSet<ChangeData> read() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      throw new UnsupportedOperationException();
    }
  }
}
