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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.checkout;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.util.Map;

public class SubmitByFastForwardIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.FAST_FORWARD_ONLY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    Git git = createProject();
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change.getCommitId());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertSubmitter(change.getChangeId(), 1);
  }

  @Test
  public void submitFastForwardNotPossible_Conflict() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "b.txt", "other content");

 // TODO(sbeller): reuse ActionsIT:approve ?
    RestResponse r = adminSession.post(
        "/changes/" + change2.getChangeId() + "/revisions/current/review",
        new ReviewInput().label("Code-Review", 2));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();

    // TODO(sbeller): reuse ActionsIT:getActions ?
    Map<String, ActionInfo> actions = newGson().fromJson(
        adminSession.get("/changes/"
            + change2.getChangeId()
            + "/revisions/current/actions").getReader(),
        new TypeToken<Map<String, ActionInfo>>() {}.getType());

    assertThat(actions).containsKey("submit");
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isFalse();

    submitWithConflict(change2.getChangeId());
    assertThat(getRemoteHead()).isEqualTo(oldHead);
    assertSubmitter(change.getChangeId(), 1);
  }
}
