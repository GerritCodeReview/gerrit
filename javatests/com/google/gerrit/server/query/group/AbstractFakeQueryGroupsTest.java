// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.query.group;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractFakeQueryGroupsTest extends AbstractQueryGroupsTest {

  @Inject private GroupIndexCollection groupIndexCollection;

  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config(config);
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }

  @Before
  public void resetQueryCount() {
    ((AbstractFakeIndex<?, ?, ?>) groupIndexCollection.getSearchIndex()).resetQueryCount();
  }

  @Test
  public void internalQueriesDoNotPaginateWithNonePaginationType() throws Exception {
    assumeTrue(PaginationType.NONE == getCurrentPaginationType());

    final int GROUPS_CREATED_SIZE = 2;
    List<GroupInfo> groupsCreated = new ArrayList<>();
    for (int i = 0; i < GROUPS_CREATED_SIZE; i++) {
      groupsCreated.add(createGroupThatIsVisibleToAll(name("group-" + i)));
    }

    List<GroupInfo> result = assertQuery(newQuery("is:visibletoall"), groupsCreated);
    assertThat(result.size()).isEqualTo(GROUPS_CREATED_SIZE);
    assertThat(result.get(result.size() - 1)._moreGroups).isNull();
    assertThatSearchQueryWasNotPaginated();
  }

  PaginationType getCurrentPaginationType() {
    return config.getEnum("index", null, "paginationType", PaginationType.OFFSET);
  }

  private void assertThatSearchQueryWasNotPaginated() {
    assertThat(getQueryCount()).isEqualTo(1);
  }

  private int getQueryCount() {
    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) groupIndexCollection.getSearchIndex();
    return idx.getQueryCount();
  }
}
