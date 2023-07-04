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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
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
  @Inject protected AllProjectsName allProjects;

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

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void noLimitQueryPaginates() throws Exception {
    skipIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();
    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    newQuery("status:new").withNoLimit().get();
    assertThat(idx.getQueryCount()).isEqualTo(2);
  }

  @Test
  @UseClockStep
  public void noLimitQueryDoNotPaginatesWithNonePaginationType() throws Exception {
    runIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();
    newQuery("status:new").withNoLimit().get();
    // 1 index search is expected since we are not paginating.
    assertThat(idx.getQueryCount()).isEqualTo(1);
  }

  @Test
  @UseClockStep
  public void unreachableChangesNotPaginatedWithoutPagination() throws Exception {
    runIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();

    projectOperations
        .project(allProjectsName)
        .forUpdate()
        .removeAllAccessSections()
        .add(allow(Permission.READ).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    // Set queryLimit to 3
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(ANONYMOUS_USERS).range(0, 3))
        .update();

    requestContext.setContext(anonymousUserProvider::get);
    List<ChangeInfo> result = newQuery("status:new").withLimit(3).get();
    assertThat(result.size()).isEqualTo(0);
    assertThat(idx.getQueryCount()).isEqualTo(1);
    assertThat(idx.getResultsSize().get(0)).isEqualTo(4);
  }

  @Test
  @UseClockStep
  public void unreachableChangesPaginatedWithPagination() throws Exception {
    skipIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();

    projectOperations
        .project(allProjectsName)
        .forUpdate()
        .removeAllAccessSections()
        .add(allow(Permission.READ).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    // Set queryLimit to 3
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(ANONYMOUS_USERS).range(0, 3))
        .update();

    requestContext.setContext(anonymousUserProvider::get);
    List<ChangeInfo> result = newQuery("status:new").withLimit(3).get();
    assertThat(result.size()).isEqualTo(0);
    assertThat(idx.getQueryCount()).isEqualTo(2);
    assertThat(idx.getResultsSize().get(0)).isEqualTo(4); // First query size (limit (3) + 1)
    assertThat(idx.getResultsSize().get(1)).isEqualTo(0); // Second query size
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void internalQueriesPaginate() throws Exception {
    skipIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();
    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    queryProvider.get().query(queryBuilderProvider.get().parse("status:new"));
    assertThat(idx.getQueryCount()).isEqualTo(2);
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void internalQueriesDoNotPaginateWithNonePaginationType() throws Exception {
    runIfPaginationTypeNone();

    AbstractFakeIndex idx = getAbstractFakeIndex();
    // 1 index search is expected since we are not paginating.
    queryProvider.get().query(queryBuilderProvider.get().parse("status:new"));
    assertThat(idx.getQueryCount()).isEqualTo(1);
  }

  private AbstractFakeIndex getAbstractFakeIndex() throws Exception {
    TestRepository<Repo> testRepo = createProject("repo");
    // create 4 changes
    insert(testRepo, newChange(testRepo));
    insert(testRepo, newChange(testRepo));
    insert(testRepo, newChange(testRepo));
    insert(testRepo, newChange(testRepo));

    // Set queryLimit to 2
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 2))
        .update();

    return (AbstractFakeIndex) changeIndexCollection.getSearchIndex();
  }
}
