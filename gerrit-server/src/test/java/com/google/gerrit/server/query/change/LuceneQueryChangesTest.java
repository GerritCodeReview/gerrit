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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class LuceneQueryChangesTest extends AbstractQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config luceneConfig = new Config(config);
    InMemoryModule.setDefaults(luceneConfig);
    return Guice.createInjector(new InMemoryModule(luceneConfig, notesMigration));
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
}
