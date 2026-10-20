// Copyright (C) 2026 Monty Taylor
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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class IndexedChangeDependenciesSupportTest {
  @Test
  public void requiresRolloutFlagAndActiveSchemaWithParentField() {
    ChangeIndexCollection indexes = new ChangeIndexCollection();
    Config config = new Config();
    assertThat(new IndexedChangeDependenciesSupport(indexes, config).isEnabled()).isFalse();
    config.setBoolean("change", null, "enableIndexedDependencies", true);
    IndexedChangeDependenciesSupport support =
        new IndexedChangeDependenciesSupport(indexes, config);
    assertThat(support.isEnabled()).isFalse();

    ChangeIndex index = mock(ChangeIndex.class);
    when(index.getSchema()).thenReturn(ChangeSchemaDefinitions.INSTANCE.get(90));
    indexes.setSearchIndex(index, false);
    assertThat(support.isEnabled()).isFalse();
    when(index.getSchema()).thenReturn(ChangeSchemaDefinitions.INSTANCE.getLatest());
    assertThat(support.isEnabled()).isTrue();
    assertThat(new IndexedChangeDependenciesSupport(indexes, new Config()).isEnabled()).isFalse();
  }
}
