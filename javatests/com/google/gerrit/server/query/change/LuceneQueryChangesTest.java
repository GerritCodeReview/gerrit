// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public abstract class LuceneQueryChangesTest extends AbstractQueryChangesTest {
  @Inject protected AllProjectsName allProjects;

  @Override
  protected Injector createInjector() {
    Config luceneConfig = new Config(config);
    InMemoryModule.setDefaults(luceneConfig);
    return Guice.createInjector(new InMemoryModule(luceneConfig));
  }

  @Test
  public void fullTextWithSpecialChars() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    repo = createAndOpenProject(project);
    RevCommit commit1 = repo.parseBody(repo.commit().message("foo_bar_foo").create());
    Change change1 = insert(project, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("one.two.three").create());
    Change change2 = insert(project, newChangeForCommit(repo, commit2));

    assertQuery("message:foo_ba");
    assertQuery("message:bar", change1);
    assertQuery("message:foo_bar", change1);
    assertQuery("message:foo bar", change1);
    assertQuery("message:two", change2);
    assertQuery("message:one.two", change2);
    assertQuery("message:one two", change2);
  }

  @Test
  public void byChangeId() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    repo = createAndOpenProject(project);
    RevCommit commit1 = repo.parseBody(repo.commit().message("foo_bar_foo").create());
    Change change1 = insert(project, newChangeForCommit(repo, commit1));

    assertQuery(String.format("change:%s", change1.getChangeId()), change1);
    assertQuery(String.format("change:%s", change1.getId()), change1);
    assertQuery(
        String.format("change:%s~%s", change1.getProject(), change1.getChangeId()), change1);
    assertQuery(
        String.format(
            "change:%s~%s~%s", change1.getProject(), change1.getDest().branch(), change1.getKey()),
        change1);
  }

  @Test
  public void invalidQuery() throws Exception {
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> newQuery("\\").get());
    assertThat(thrown).hasMessageThat().contains("Cannot create full-text query with value: \\");
  }

  @Test
  public void openAndClosedChanges() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    repo = createAndOpenProject(project);

    // create 3 closed changes
    Change change1 = insert(project, newChangeWithStatus(repo, Change.Status.MERGED));
    Change change2 = insert(project, newChangeWithStatus(repo, Change.Status.MERGED));
    Change change3 = insert(project, newChangeWithStatus(repo, Change.Status.MERGED));

    // create 3 new changes
    Change change4 = insert(project, newChangeWithStatus(repo, Change.Status.NEW));
    Change change5 = insert(project, newChangeWithStatus(repo, Change.Status.NEW));
    Change change6 = insert(project, newChangeWithStatus(repo, Change.Status.NEW));

    // Set queryLimit to 1
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 1))
        .update();

    Change[] expected = new Change[] {change6, change5, change4, change3, change2, change1};
    assertQuery(newQuery("project:repo").withNoLimit(), expected);
  }

  @Test
  public void skipChangesNotVisible() throws Exception {
    // create 1 new change on a repo
    Project.NameKey project = Project.nameKey("repo");
    repo = createAndOpenProject(project);
    Change visibleChange = insert(project, newChangeWithStatus(repo, Change.Status.NEW));
    Change[] expected = new Change[] {visibleChange};

    // pagination does not need to restart the datasource, the request is fulfilled
    assertQuery(newQuery("status:new").withLimit(1), expected);

    // create 2 new private changes
    Change invisibleChange1 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    Change invisibleChange2 =
        insert(project, newChangeWithStatus(repo, Change.Status.NEW), user.getAccountId());
    gApi.changes().id(invisibleChange1.getKey().get()).setPrivate(true, null);
    gApi.changes().id(invisibleChange2.getKey().get()).setPrivate(true, null);

    // Pagination should back-fill when the results skipped because of the visibility.
    // Use a non-admin user, since admins can always see all changes.
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    setRequestContextForUser(user2);
    assertQuery(newQuery("status:new").withLimit(1), expected);
  }
}
