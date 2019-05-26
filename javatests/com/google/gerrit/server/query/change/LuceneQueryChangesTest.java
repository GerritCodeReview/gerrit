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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public abstract class LuceneQueryChangesTest extends AbstractQueryChangesTest {
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
}
