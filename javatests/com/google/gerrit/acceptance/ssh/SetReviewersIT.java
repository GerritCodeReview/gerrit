// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.reviewdb.client.Account;
import org.junit.Before;
import org.junit.Test;

@UseSsh
@NoHttpd
public class SetReviewersIT extends AbstractDaemonTest {
  PushOneCommit.Result change;

  @Before
  public void setUp() throws Exception {
    change = createChange();
  }

  @Test
  public void byCommitHash() throws Exception {
    String id = change.getCommit().getId().toString().split("\\s+")[1];
    addReviewer(id);
    removeReviewer(id);
  }

  @Test
  public void byChangeID() throws Exception {
    addReviewer(change.getChangeId());
    removeReviewer(change.getChangeId());
  }

  private void setReviewer(boolean add, String id) throws Exception {
    adminSshSession.exec(
        String.format("gerrit set-reviewers -%s %s %s", add ? "a" : "r", user.email(), id));
    adminSshSession.assertSuccess();
    ImmutableSet<Account.Id> reviewers = change.getChange().getReviewers().all();
    if (add) {
      assertThat(reviewers).contains(user.id());
    } else {
      assertThat(reviewers).doesNotContain(user.id());
    }
  }

  private void addReviewer(String id) throws Exception {
    setReviewer(true, id);
  }

  private void removeReviewer(String id) throws Exception {
    setReviewer(false, id);
  }
}
