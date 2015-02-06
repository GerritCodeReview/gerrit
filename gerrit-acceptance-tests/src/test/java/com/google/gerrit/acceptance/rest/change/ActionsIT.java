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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class ActionsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void initialRevisionActions() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).hasSize(1);
  }

  @Test
  public void approvedRevisionActions() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    approve(changeId);
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("submit");
    assertThat(actions).hasSize(2);
  }

  private Map<String, ActionInfo> getActions(String changeId)
      throws IOException {
    return newGson().fromJson(
        adminSession.get("/changes/"
            + changeId
            + "/revisions/1/actions").getReader(),
        new TypeToken<Map<String, ActionInfo>>() {}.getType());
  }

  private PushOneCommit.Result createChangeWithTopic() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master%topic=foo");
  }

  private void approve(String changeId) throws IOException {
    RestResponse r = adminSession.post(
        "/changes/" + changeId + "/revisions/current/review",
        new ReviewInput().label("Code-Review", 2));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();
  }
}
