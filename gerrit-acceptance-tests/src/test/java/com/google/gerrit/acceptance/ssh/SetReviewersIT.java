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

import static com.google.common.truth.Truth.assert_;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import org.junit.Test;

@UseSsh
public class SetReviewersIT extends AbstractDaemonTest {

  @Test
  public void addByCommitHash() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec(
        "gerrit set-reviewers -a "
            + user.email
            + " "
            + change.getCommit().getId().toString().split("\\s+")[1]);
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertTrue(change.getChange().getReviewers().all().contains(user.id));
  }

  @Test
  public void addByChangeID() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec("gerrit set-reviewers -a " + user.email + " " + change.getChangeId());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertTrue(change.getChange().getReviewers().all().contains(user.id));
  }

  @Test
  public void removeReviewer() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec("gerrit set-reviewers -a " + user.email + " " + change.getChangeId());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertTrue(change.getChange().getReviewers().all().contains(user.id));
    adminSshSession.exec("gerrit set-reviewers -r " + user.email + " " + change.getChangeId());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertTrue(change.getChange().getReviewers().all().asList().isEmpty());
  }
}
