// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.sshd.commands.ReviewCommand.DEPRECATE_USAGE_WITHOUT_PROJECT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import org.junit.Test;

@UseSsh
public class ChangeReviewIT extends AbstractDaemonTest {

  @Test
  public void testGerritReviewCommandWithShortNameBranch() throws Exception {
    PushOneCommit.Result r = createChange();
    adminSshSession.exec(
        "gerrit review --project "
            + r.getChange().change().getProject().get()
            + " --branch "
            + r.getChange().change().getDest().shortName()
            + " --code-review 1 "
            + r.getCommit().getName());
    adminSshSession.assertSuccess();
  }

  @Test
  public void testGerritReviewCommandWithoutProject() throws Exception {
    PushOneCommit.Result r = createChange();
    String response =
        adminSshSession.exec(
            "gerrit review"
                + " --branch "
                + r.getChange().change().getDest().shortName()
                + " --code-review 1 "
                + r.getCommit().getName());
    adminSshSession.assertSuccess();
    assertThat(response).contains(DEPRECATE_USAGE_WITHOUT_PROJECT);
  }

  @Test
  public void testGerritReviewCommandWithProject() throws Exception {
    PushOneCommit.Result r = createChange();
    String response =
        adminSshSession.exec(
            "gerrit review --project "
                + r.getChange().change().getProject().get()
                + " --code-review 1 "
                + r.getCommit().getName());
    adminSshSession.assertSuccess();
    assertThat(response).doesNotContain(DEPRECATE_USAGE_WITHOUT_PROJECT);
  }
}
