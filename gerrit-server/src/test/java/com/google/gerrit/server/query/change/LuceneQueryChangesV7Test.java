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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

public class LuceneQueryChangesV7Test extends AbstractQueryChangesTest {
  protected Injector createInjector() {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setInt("index", "lucene", "testVersion", 7);
    return Guice.createInjector(new InMemoryModule(cfg));
  }

  // Tests for features not supported in V7.
  @Ignore
  @Override
  @Test
  public void byProjectPrefix() {}

  @Ignore
  @Override
  @Test
  public void byDefault() {}
  // End tests for features not supported in V7.

  @Test
  public void pagination() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    List<Change> changes = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      changes.add(newChange(repo, null, null, null, null).insert());
    }

    // Page forward and back through 3 pages of results.
    QueryChanges q;
    List<ChangeInfo> results;
    results = query("status:new limit:2");
    assertEquals(2, results.size());
    assertResultEquals(changes.get(4), results.get(0));
    assertResultEquals(changes.get(3), results.get(1));

    q = newQuery("status:new limit:2");
    q.setSortKeyBefore(results.get(1)._sortkey);
    results = query(q);
    assertEquals(2, results.size());
    assertResultEquals(changes.get(2), results.get(0));
    assertResultEquals(changes.get(1), results.get(1));

    q = newQuery("status:new limit:2");
    q.setSortKeyBefore(results.get(1)._sortkey);
    results = query(q);
    assertEquals(1, results.size());
    assertResultEquals(changes.get(0), results.get(0));

    q = newQuery("status:new limit:2");
    q.setSortKeyAfter(results.get(0)._sortkey);
    results = query(q);
    assertEquals(2, results.size());
    assertResultEquals(changes.get(2), results.get(0));
    assertResultEquals(changes.get(1), results.get(1));

    q = newQuery("status:new limit:2");
    q.setSortKeyAfter(results.get(0)._sortkey);
    results = query(q);
    assertEquals(2, results.size());
    assertResultEquals(changes.get(4), results.get(0));
    assertResultEquals(changes.get(3), results.get(1));
  }

  @Override
  @Test
  public void updatedOrderWithSubMinuteResolution() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertTrue(lastUpdatedMs(change1) < lastUpdatedMs(change2));

    List<ChangeInfo> results;
    results = query("status:new");
    assertEquals(2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertTrue(lastUpdatedMs(change1) > lastUpdatedMs(change2));
    assertTrue(lastUpdatedMs(change1) - lastUpdatedMs(change2)
        < MILLISECONDS.convert(1, MINUTES));

    results = query("status:new");
    assertEquals(2, results.size());
    // Same order as before change1 was modified.
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void sortKeyBreaksTiesOnChangeId() throws Exception {
    clockStepMs = 0;
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertEquals(change1.getLastUpdatedOn(), change2.getLastUpdatedOn());

    List<ChangeInfo> results = query("status:new");
    assertEquals(2, results.size());
    // Updated at the same time, 2 > 1.
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }
}
