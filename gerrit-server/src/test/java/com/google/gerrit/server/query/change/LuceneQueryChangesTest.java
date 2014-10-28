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

import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class LuceneQueryChangesTest extends AbstractQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config luceneConfig = new Config(config);
    InMemoryModule.setDefaults(luceneConfig);
    return Guice.createInjector(new InMemoryModule(luceneConfig));
  }

  @Test
  public void fullTextWithSpecialChars() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit1 =
        repo.parseBody(repo.commit().message("foo_bar_foo").create());
    Change change1 = newChange(repo, commit1, null, null, null).insert();
    RevCommit commit2 =
        repo.parseBody(repo.commit().message("one.two.three").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertTrue(query("message:foo_ba").isEmpty());
    assertResultEquals(change1, queryOne("message:bar"));
    assertResultEquals(change1, queryOne("message:foo_bar"));
    assertResultEquals(change1, queryOne("message:foo bar"));
    assertResultEquals(change2, queryOne("message:two"));
    assertResultEquals(change2, queryOne("message:one.two"));
    assertResultEquals(change2, queryOne("message:one two"));
  }
}
