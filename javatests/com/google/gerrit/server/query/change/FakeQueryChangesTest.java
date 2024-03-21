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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.junit.Assume.assumeFalse;

import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.config.AllProjectsName;
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
  public void queryRightNumberOfTimes() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    // create 1 visible change
    Change visibleChange1 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW));

    // create 4 private changes
    Change invisibleChange2 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW), user2);
    Change invisibleChange3 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW), user2);
    Change invisibleChange4 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW), user2);
    Change invisibleChange5 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW), user2);
    gApi.changes().id(invisibleChange2.getChangeId()).setPrivate(true, null);
    gApi.changes().id(invisibleChange3.getChangeId()).setPrivate(true, null);
    gApi.changes().id(invisibleChange4.getChangeId()).setPrivate(true, null);
    gApi.changes().id(invisibleChange5.getChangeId()).setPrivate(true, null);

    AbstractFakeIndex idx = (AbstractFakeIndex) changeIndexCollection.getSearchIndex();
    int queriesBeforeExecution = idx.getQueryCount();
    List<ChangeInfo> queryResult = newQuery("status:new").withLimit(2).get();
    assertThat(queryResult).hasSize(1);
    assertThat(queryResult.get(0).changeId).isEqualTo(visibleChange1.getKey().get());

    // Since the limit of the query (i.e. 2), 2 index searches are expected in fact:
    // 1: The first query will return invisibleChange5, invisibleChange4 and invisibleChange3,
    // 2: Another query is needed to back-fill the limit requested by the user.
    // even if one result in the second query is skipped because it is not visible,
    // there are no more results to query.
    assertThat(idx.getQueryCount() - queriesBeforeExecution).isEqualTo(2);
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void noLimitQueryPaginates() throws Exception {
    assumeFalse(PaginationType.NONE == getCurrentPaginationType());
    TestRepository<InMemoryRepositoryManager.Repo> testRepo = createProject("repo");
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

    AbstractFakeIndex idx = (AbstractFakeIndex) changeIndexCollection.getSearchIndex();

    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    newQuery("status:new").withNoLimit().get();
    assertThat(idx.getQueryCount()).isEqualTo(2);
  }

  @Test
  @UseClockStep
  @SuppressWarnings("unchecked")
  public void internalQueriesPaginate() throws Exception {
    assumeFalse(PaginationType.NONE == getCurrentPaginationType());
    // create 4 changes
    TestRepository<InMemoryRepositoryManager.Repo> testRepo = createProject("repo");
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

    AbstractFakeIndex idx = (AbstractFakeIndex) changeIndexCollection.getSearchIndex();

    // 2 index searches are expected. The first index search will run with size 3 (i.e.
    // the configured query-limit+1), and then we will paginate to get the remaining
    // changes with the second index search.
    queryProvider.get().query(queryBuilderProvider.get().parse("status:new"));
    assertThat(idx.getQueryCount()).isEqualTo(2);
  }
}
