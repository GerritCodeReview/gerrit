// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.PutTopic;

import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.lib.Config;

import org.junit.Test;

public class PutTopicIT extends AbstractDaemonTest {

  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void conflictingTopicInDrafts() throws Exception {
    setApiUser(admin);
    PushOneCommit.Result draft = createDraftChange();
    Change.Id num = draft.getChange().getId();
    setTopic(num.toString(), name("some-topic"), false, false);

    setApiUser(user);
    PushOneCommit push = pushFactory.create(db, user.getIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/drafts/master");
    result.assertOkStatus();
    String id = result.getChangeId();
    setTopic(id, name("some-topic"), false, true);
    setTopic(id, name("other-topic"), false, false);
    setTopic(id, name("some-topic"), true, false);
  }

  @Test
  public void conflictingOpenChangeInTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    createChange("refs/for/master/" + name("topic"));

    createBranch(new Branch.NameKey(project, "hidden"));
    PushOneCommit.Result hidden =
        createChange("refs/for/hidden/" + name("topic"));
    approve(hidden.getChangeId());
    blockRead("refs/heads/hidden");

    PushOneCommit.Result visible2 =
        createChange("refs/for/master");
    setTopic(visible2.getChangeId(), name("topic"), false, true);
    setTopic(visible2.getChangeId(), name("topic"), true, false);

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/for/master/" + name("topic"));
    result.assertErrorStatus("topic not fully visible");
  }

  private void setTopic(String changeId, String topic, boolean force,
      boolean expectFailure) throws Exception {
    PutTopic.Input in = new PutTopic.Input();
    in.topic = topic;
    in.force_topic = force;
    RestResponse r =
        adminRestSession.put("/changes/" + changeId + "/topic", in);
    if (!expectFailure) {
      r.assertOK();
      assertThat(newGson().fromJson(r.getReader(), String.class)).isEqualTo(
          in.topic);
    } else {
      r.assertForbidden();
    }
  }
}
