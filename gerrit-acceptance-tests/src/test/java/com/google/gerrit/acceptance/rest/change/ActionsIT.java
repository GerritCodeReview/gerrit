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
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.util.Map;

public class ActionsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void revisionActionsOneChangePerTopicUnapproved() throws Exception {
    String changeId = createChangeWithTopic(name("foo1")).getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("rebase");
    assertThat(actions).hasSize(2);
  }

  @Test
  public void revisionActionsOneChangePerTopic() throws Exception {
    String changeId = createChangeWithTopic(name("foo1")).getChangeId();
    approve(changeId);
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    // We want to treat a single change in a topic not as a whole topic,
    // so regardless of how submitWholeTopic is configured:
    noSubmitWholeTopicAssertions(actions);
  }

  @Test
  public void revisionActionsTwoChangeChangesInTopic() throws Exception {
    String changeId = createChangeWithTopic(name("foo2")).getChangeId();
    approve(changeId);
    // create another change with the same topic
    createChangeWithTopic(name("foo2")).getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isNull();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title).isEqualTo("Other changes in this topic are not ready");
    } else {
      noSubmitWholeTopicAssertions(actions);
    }
  }

  @Test
  public void revisionActionsTwoChangeChangesInTopicReady() throws Exception {
    String changeId = createChangeWithTopic(name("foo2")).getChangeId();
    approve(changeId);
    // create another change with the same topic
    String changeId2 = createChangeWithTopic(name("foo2")).getChangeId();
    approve(changeId2);
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isTrue();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title).isEqualTo("Submit all 2 changes of the same topic");
    } else {
      noSubmitWholeTopicAssertions(actions);
    }
  }

  private void noSubmitWholeTopicAssertions(Map<String, ActionInfo> actions) {
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isTrue();
    assertThat(info.label).isEqualTo("Submit");
    assertThat(info.method).isEqualTo("POST");
    assertThat(info.title).isEqualTo("Submit patch set 1 into master");
  }

  private void commonActionsAssertions(Map<String, ActionInfo> actions) {
    assertThat(actions).hasSize(3);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("submit");
    assertThat(actions).containsKey("rebase");
  }

  private PushOneCommit.Result createChangeWithTopic(String topic)
      throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    assertThat(topic).isNotEmpty();
    return push.to("refs/for/master/" + topic);
  }
}
