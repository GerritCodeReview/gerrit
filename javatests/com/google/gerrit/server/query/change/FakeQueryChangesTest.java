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

import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test against {@link com.google.gerrit.index.testing.AbstractFakeIndex}. This test might seem
 * obsolete, but it makes sure that the fake index implementation used in tests gives the same
 * results as production indices.
 */
public abstract class FakeQueryChangesTest extends AbstractQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config(config);
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }

  @Ignore
  @Test
  @Override
  public void byDefault() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.byDefault();
  }

  @Ignore
  @Test
  @Override
  public void bySize() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.bySize();
  }

  @Ignore
  @Test
  @Override
  public void byDraftBy() throws Exception {
    // TODO(hiesel): Fix NPE in ChangeData#draftRefs
    super.byDraftBy();
  }

  @Ignore
  @Test
  @Override
  public void byDraftByExcludesZombieDrafts() throws Exception {
    // TODO(hiesel): Fix NPE in ChangeData#draftRefs
    super.byDraftByExcludesZombieDrafts();
  }

  @Ignore
  @Test
  @Override
  public void byMergedBefore() throws Exception {
    // TODO(hiesel): Used predicate is not a matchable. Fix.
    super.byMergedBefore();
  }

  @Ignore
  @Test
  @Override
  public void reviewerAndCcByEmail() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.reviewerAndCcByEmail();
  }

  @Ignore
  @Test
  @Override
  public void byMessageExact() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byMessageExact();
  }

  @Ignore
  @Test
  @Override
  public void fullTextWithNumbers() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.fullTextWithNumbers();
  }

  @Ignore
  @Test
  @Override
  public void byTriplet() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.byTriplet();
  }

  @Ignore
  @Test
  @Override
  public void byAge() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byAge();
  }

  @Ignore
  @Test
  @Override
  public void dashboardHasUnpublishedDrafts() throws Exception {
    // TODO(hiesel): Fix NPE in ChangeData#draftRefs
    super.dashboardHasUnpublishedDrafts();
  }

  @Ignore
  @Test
  @Override
  public void byMessageSubstring() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byMessageSubstring();
  }

  @Ignore
  @Test
  @Override
  public void byBeforeUntil() throws Exception {
    // TODO(hiesel): Used predicate is not a matchable. Fix.
    super.byBeforeUntil();
  }

  @Ignore
  @Test
  @Override
  public void byLabel() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.byLabel();
  }

  @Ignore
  @Test
  @Override
  public void byTopic() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byTopic();
  }

  @Ignore
  @Test
  @Override
  public void userQuery() throws Exception {
    // TODO(hiesel): Account name predicate is always returning true in #match. Fix.
    super.userQuery();
  }

  @Ignore
  @Test
  @Override
  public void visible() throws Exception {
    // TODO(hiesel): Account name predicate is always returning true in #match. Fix.
    super.visible();
  }

  @Ignore
  @Test
  @Override
  public void userDestination() throws Exception {
    // TODO(hiesel): Account name predicate is always returning true in #match. Fix.
    super.userDestination();
  }

  @Ignore
  @Test
  @Override
  public void byAfterSince() throws Exception {
    // TODO(hiesel): Used predicate is not a matchable. Fix.
    super.byAfterSince();
  }

  @Ignore
  @Test
  @Override
  public void byMessageMixedCase() throws Exception {
    // TODO(hiesel): Used predicate is not a matchable. Fix.
    super.byMessageMixedCase();
  }

  @Ignore
  @Test
  @Override
  public void reindexIfStale() throws Exception {
    // TODO(hiesel): Fix bug in predicate and remove @Ignore.
    super.reindexIfStale();
  }

  @Ignore
  @Test
  @Override
  public void byCommit() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byCommit();
  }

  @Ignore
  @Test
  @Override
  public void byComment() throws Exception {
    // TODO(hiesel): Existing #match function uses the index causing a StackOverflowError. Fix.
    super.byComment();
  }

  @Ignore
  @Test
  @Override
  public void byMergedAfter() throws Exception {
    // TODO(hiesel): Used predicate is not a matchable. Fix.
    super.byMergedAfter();
  }

  @Ignore
  @Test
  @Override
  public void byOwnerInvalidQuery() throws Exception {
    // TODO(hiesel): Account name predicate is always returning true in #match. Fix.
    super.byMergedAfter();
  }
}
