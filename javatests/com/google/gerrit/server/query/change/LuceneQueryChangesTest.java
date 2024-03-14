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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.junit.TestRepository;
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
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("foo_bar_foo").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("one.two.three").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("message:foo_ba");
    assertQuery("message:bar", change1);
    assertQuery("message:foo_bar", change1);
    assertQuery("message:foo bar", change1);
    assertQuery("message:two", change2);
    assertQuery("message:one.two", change2);
    assertQuery("message:one two", change2);
  }

  @Test
  @Override
  public void byOwnerInvalidQuery() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    String nameEmail = user.asIdentifiedUser().getNameEmail();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> assertQuery("owner: \"" + nameEmail + "\"\\", change1));
    assertThat(thrown).hasMessageThat().contains("Cannot create full-text query with value: \\");
  }

  @Test
  public void openAndClosedChanges() throws Exception {
    TestRepository<Repo> repo = createProject("repo");

    // create 3 closed changes
    Change change1 = insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));
    Change change2 = insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));
    Change change3 = insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    // create 3 new changes
    Change change4 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW));
    Change change5 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW));
    Change change6 = insert(repo, newChangeWithStatus(repo, Change.Status.NEW));

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
    TestRepository<Repo> publicRepo = createProject("publicRepo");
    Change pubChange1 = insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW));

    // create 5 new private changes
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();

    Change priChange1 =
        insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW), user2);
    Change priChange2 =
        insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW), user2);
    Change priChange3 =
        insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW), user2);
    Change priChange4 =
        insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW), user2);
    Change priChange5 =
        insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW), user2);
    gApi.changes().id(priChange1.getChangeId()).setPrivate(true, null);
    gApi.changes().id(priChange2.getChangeId()).setPrivate(true, null);
    gApi.changes().id(priChange3.getChangeId()).setPrivate(true, null);
    gApi.changes().id(priChange4.getChangeId()).setPrivate(true, null);
    gApi.changes().id(priChange5.getChangeId()).setPrivate(true, null);

    // pagination should back-fill the results skipped because of the visibility
    Change[] expected = new Change[] {pubChange1};
    assertQuery(newQuery("status:new").withLimit(1), expected);
  }

  @Test
  public void skipChangesNotVisibleForAnonymousUser() throws Exception {

    // create 1 new change on a public repo
    TestRepository<Repo> publicRepo = createProject("publicRepo");
    Change pubChange1 = insert(publicRepo, newChangeWithStatus(publicRepo, Change.Status.NEW));

    // create 5 new changes on a private repo
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    TestRepository<Repo> privateRepo = createProject("privateRepo");
    insert(privateRepo, newChangeWithStatus(privateRepo, Change.Status.NEW), user2);
    insert(privateRepo, newChangeWithStatus(privateRepo, Change.Status.NEW), user2);
    insert(privateRepo, newChangeWithStatus(privateRepo, Change.Status.NEW), user2);
    insert(privateRepo, newChangeWithStatus(privateRepo, Change.Status.NEW), user2);
    insert(privateRepo, newChangeWithStatus(privateRepo, Change.Status.NEW), user2);

    projectOperations
        .project(Project.NameKey.parse("privateRepo"))
        .forUpdate()
        .add(deny(Permission.READ).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(deny(Permission.READ).ref("refs/*").group(ANONYMOUS_USERS))
        .update();

    // pagination should back-fill the results skipped because of the visibility
    requestContext.setContext(anonymousUserProvider::get);
    Change[] expected = new Change[] {pubChange1};
    assertQuery(newQuery("status:new").withLimit(1), expected);
  }
}
