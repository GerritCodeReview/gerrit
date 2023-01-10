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

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
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
    TestRepository<Repository> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("foo_bar_foo").create());
    Change change1 = insert("repo", newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("one.two.three").create());
    Change change2 = insert("repo", newChangeForCommit(repo, commit2));

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
    TestRepository<Repository> repo = createProject("repo");
    Change change1 = insert("repo", newChange(repo), userId);
    String nameEmail = user.asIdentifiedUser().getNameEmail();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> assertQuery("owner: \"" + nameEmail + "\"\\", change1));
    assertThat(thrown).hasMessageThat().contains("Cannot create full-text query with value: \\");
  }

  @Test
  public void openAndClosedChanges() throws Exception {
    TestRepository<Repository> repo = createProject("repo");

    // create 3 closed changes
    Change change1 = insert("repo", newChangeWithStatus(repo, Change.Status.MERGED));
    Change change2 = insert("repo", newChangeWithStatus(repo, Change.Status.MERGED));
    Change change3 = insert("repo", newChangeWithStatus(repo, Change.Status.MERGED));

    // create 3 new changes
    Change change4 = insert("repo", newChangeWithStatus(repo, Change.Status.NEW));
    Change change5 = insert("repo", newChangeWithStatus(repo, Change.Status.NEW));
    Change change6 = insert("repo", newChangeWithStatus(repo, Change.Status.NEW));

    // Set queryLimit to 1
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 1))
        .update();

    Change[] expected = new Change[] {change6, change5, change4, change3, change2, change1};
    assertQuery(newQuery("project:repo").withNoLimit(), expected);
  }
}
