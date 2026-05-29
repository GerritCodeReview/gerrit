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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
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
  public void stopQueryIfNoMoreResults() throws Exception {
    // create 2 visible changes
    Project.NameKey project = Project.nameKey("repo");
    try (TestRepository<Repository> testRepo = createAndOpenProject(project)) {
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
    }

    // create 2 invisible changes
    Project.NameKey hiddenProject = Project.nameKey("hiddenProject");
    try (TestRepository<Repository> hiddenRepo = createAndOpenProject(hiddenProject)) {
      insert(hiddenProject, newChange(hiddenRepo));
      insert(hiddenProject, newChange(hiddenRepo));
      projectOperations
          .project(hiddenProject)
          .forUpdate()
          .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
          .update();
    }

    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();

    @SuppressWarnings("unused")
    var unused = newQuery("status:new").withLimit(5).get();

    // Since the limit of the query (i.e. 5) is more than the total number of changes (i.e. 4),
    // only 1 index search is expected.
    assertThatSearchQueryWasNotPaginated(idx.getQueryCount());
  }

  @Test
  @UseClockStep
  public void queryRightNumberOfTimes() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repository> repo = createAndOpenProject(project);
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    // create 1 visible change
    Change visibleChange1 = insert(project, newChangeWithStatus(repo, Change.Status.NEW));

    // create 4 private changes
    Change invisibleChange2 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    Change invisibleChange3 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    Change invisibleChange4 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    Change invisibleChange5 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    gApi.changes().id(invisibleChange2.getKey().get()).setPrivate(true, null);
    gApi.changes().id(invisibleChange3.getKey().get()).setPrivate(true, null);
    gApi.changes().id(invisibleChange4.getKey().get()).setPrivate(true, null);
    gApi.changes().id(invisibleChange5.getKey().get()).setPrivate(true, null);

    // Use a non-admin user, since admins can always see all changes.
    setRequestContextForUser(user2);

    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();
    idx.resetQueryCount();
    List<ChangeInfo> queryResult = newQuery("status:new").withLimit(2).get();
    assertThat(queryResult).hasSize(1);
    assertThat(queryResult.get(0).changeId).isEqualTo(visibleChange1.getKey().get());

    // Since the limit of the query (i.e. 2), 2 index searches are expected in fact:
    // 1: The first query will return invisibleChange5, invisibleChange4 and invisibleChange3,
    // 2: Another query is needed to back-fill the limit requested by the user.
    // even if one result in the second query is skipped because it is not visible,
    // there are no more results to query.
    assertThatSearchQueryWasPaginated(idx.getQueryCount(), 2);
  }

  @Test
  @UseClockStep
  public void noLimitQueryPaginates() throws Exception {
    assumeFalse(PaginationType.NONE == getCurrentPaginationType());

    Project.NameKey project = Project.nameKey("repo");
    try (TestRepository<Repository> testRepo = createAndOpenProject(project)) {
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
    }
    // Set queryLimit to 2
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 2))
        .update();

    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();

    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    @SuppressWarnings("unused")
    var unused = newQuery("status:new").withNoLimit().get();

    assertThatSearchQueryWasPaginated(idx.getQueryCount(), 2);
  }

  @Test
  @UseClockStep
  public void noLimitQueryDoesNotPaginatesWithNonePaginationType() throws Exception {
    assumeTrue(PaginationType.NONE == getCurrentPaginationType());

    AbstractFakeIndex<?, ?, ?> idx = setupRepoWithFourChanges();

    @SuppressWarnings("unused")
    var unused = newQuery("status:new").withNoLimit().get();

    assertThatSearchQueryWasNotPaginated(idx.getQueryCount());
  }

  @Test
  @UseClockStep
  public void invisibleChangesPaginatedWithPagination() throws Exception {
    AbstractFakeIndex<?, ?, ?> idx = setupRepoWithFourChanges();
    final int LIMIT = 3;

    projectOperations
        .project(allProjectsName)
        .forUpdate()
        .removeAllAccessSections()
        .add(allow(Permission.READ).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(ANONYMOUS_USERS).range(0, LIMIT))
        .update();

    @SuppressWarnings("unused")
    var unused = requestContext.setContext(anonymousUserProvider::get);

    List<ChangeInfo> result = newQuery("status:new").withLimit(LIMIT).get();
    assertThat(result.size()).isEqualTo(0);
    assertThatSearchQueryWasPaginated(idx.getQueryCount(), 2);
    assertThat(idx.getResultsSizes().get(0)).isEqualTo(LIMIT + 1);
    assertThat(idx.getResultsSizes().get(1)).isEqualTo(0); // Second query size
  }

  @Test
  @UseClockStep
  public void internalQueriesPaginate() throws Exception {
    assumeFalse(PaginationType.NONE == getCurrentPaginationType());
    final int LIMIT = 2;

    Project.NameKey project = Project.nameKey("repo");
    try (TestRepository<Repository> testRepo = createAndOpenProject(project)) {
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
    }
    // Set queryLimit to 2
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, LIMIT))
        .update();
    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();
    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    executeQuery("status:new");
    assertThat(idx.getQueryCount()).isEqualTo(LIMIT);
  }

  @Test
  @UseClockStep
  public void internalQueriesDoNotPaginateWithNonePaginationType() throws Exception {
    assumeTrue(PaginationType.NONE == getCurrentPaginationType());

    AbstractFakeIndex<?, ?, ?> idx = setupRepoWithFourChanges();
    // 1 index search is expected since we are not paginating.
    executeQuery("status:new");
    assertThatSearchQueryWasNotPaginated(idx.getQueryCount());
  }

  @SuppressWarnings("unused")
  private void executeQuery(String query) throws QueryParseException {
    ImmutableList<ChangeData> unused =
        queryProvider.get().query(queryBuilderProvider.get().parse(query));
  }

  private void assertThatSearchQueryWasNotPaginated(int queryCount) {
    assertThat(queryCount).isEqualTo(1);
  }

  private void assertThatSearchQueryWasPaginated(int queryCount, int expectedPages) {
    assertThat(queryCount).isEqualTo(expectedPages);
  }

  private AbstractFakeIndex<?, ?, ?> setupRepoWithFourChanges() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    try (TestRepository<Repository> testRepo = createAndOpenProject(project)) {
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
      insert(project, newChange(testRepo));
    }

    // Set queryLimit to 2
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 2))
        .update();

    return (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();
  }
}
