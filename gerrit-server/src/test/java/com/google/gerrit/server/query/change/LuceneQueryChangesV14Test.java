// Copyright (C) 2015 The Android Open Source Project
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.junit.Test;

public class LuceneQueryChangesV14Test extends LuceneQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config luceneConfig = new Config(config);
    InMemoryModule.setDefaults(luceneConfig);
    // Latest version with a Lucene 4 index.
    luceneConfig.setInt("index", "lucene", "testVersion", 14);
    return Guice.createInjector(new InMemoryModule(luceneConfig));
  }

  @Override
  @Ignore
  @Test
  public void byCommentBy() {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void byFrom() {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void byTopic() {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void reviewedBy() throws Exception {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void prepopulatedFields() throws Exception {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void prepopulateOnlyRequestedFields() throws Exception {
    // Ignore.
  }

  @Test
  public void isReviewed() throws Exception {
    clockStepMs = MILLISECONDS.convert(2, MINUTES);
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(newChange(repo, null, null, null, null));
    Change change2 = insert(newChange(repo, null, null, null, null));
    Change change3 = insert(newChange(repo, null, null, null, null));

    gApi.changes()
      .id(change1.getId().get())
      .current()
      .review(new ReviewInput().message("comment"));

    Account.Id user2 = accountManager
        .authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId();
    requestContext.setContext(newRequestContext(user2));

    gApi.changes()
        .id(change2.getId().get())
        .current()
        .review(ReviewInput.recommend());

    PatchSet.Id ps3_1 = change3.currentPatchSetId();
    change3 = newPatchSet(repo, change3);
    assertThat(change3.currentPatchSetId()).isNotEqualTo(ps3_1);
    // Nonzero score on previous patch set does not count.
    gApi.changes()
        .id(change3.getId().get())
        .revision(ps3_1.get())
        .review(ReviewInput.recommend());

    assertQuery("is:reviewed", change2);
    assertQuery("-is:reviewed", change3, change1);
  }
}
