// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Test against {@link com.google.gerrit.index.testing.AbstractFakeIndex}. This test might seem
 * obsolete, but it makes sure that the fake index implementation used in tests gives the same
 * results as production indices.
 */
public abstract class FakeQueryChangesTest extends AbstractQueryChangesTest {
  @Inject private ChangeIndexCollection changeIndexCollection;

  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config(config);
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void stopQueryIfNoMoreResults() throws Exception {
    // create 2 visible changes
    TestRepository<InMemoryRepositoryManager.Repo> testRepo = createProject("repo");
    insert(testRepo, newChange(testRepo));
    insert(testRepo, newChange(testRepo));

    // create 2 invisible changes
    TestRepository<Repo> hiddenProject = createProject("hiddenProject");
    insert(hiddenProject, newChange(hiddenProject));
    insert(hiddenProject, newChange(hiddenProject));
    projectOperations
        .project(Project.nameKey("hiddenProject"))
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    AbstractFakeIndex idx = (AbstractFakeIndex) changeIndexCollection.getSearchIndex();
    newQuery("status:new").withLimit(5).get();
    // Since the limit of the query (i.e. 5) is more than the total number of changes (i.e. 4),
    // only 1 index search is expected.
    assertThat(idx.getQueryCount()).isEqualTo(1);
  }
}
