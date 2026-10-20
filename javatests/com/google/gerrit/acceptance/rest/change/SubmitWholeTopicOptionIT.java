// Copyright (C) 2026 The Android Open Source Project
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
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.junit.Test;

/**
 * Exercises the per-operation {@link SubmitInput#submitWholeTopic} option.
 *
 * <p>The server default is left off (no {@code change.submitWholeTopic} config), so these tests
 * prove that a single submit can pull in the whole topic on demand. The scenarios are adapted from
 * the test that accompanied the dedicated {@code submit.topic} endpoint, but drive the behavior
 * through the existing submit endpoint plus the new input field.
 */
public class SubmitWholeTopicOptionIT extends AbstractDaemonTest {

  @Test
  public void submitsWholeTopicWhenOptionSet() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());

    SubmitInput in = new SubmitInput();
    in.submitWholeTopic = true;
    gApi.changes().id(change1.getChangeId()).current().submit(in);

    assertMerged(change1.getChangeId());
    assertMerged(change2.getChangeId());
    assertMerged(change3.getChangeId());
  }

  @Test
  public void returnsSubmittedChangeInfo() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    SubmitInput in = new SubmitInput();
    in.submitWholeTopic = true;
    ChangeInfo info = gApi.changes().id(change1.getChangeId()).current().submit(in);

    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.changeId).isEqualTo(change1.getChangeId());
    assertMerged(change2.getChangeId());
  }

  @Test
  public void doesNotSubmitTopicWhenOptionUnset() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    // change.submitWholeTopic is off and the option is not set: only change1 is submitted.
    gApi.changes().id(change1.getChangeId()).current().submit(new SubmitInput());

    assertMerged(change1.getChangeId());
    assertThat(gApi.changes().id(change2.getChangeId()).get().status).isEqualTo(ChangeStatus.NEW);
  }

  private PushOneCommit.Result createChange(
      String subject, String fileName, String content, String topic) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master%topic=" + name(topic));
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }
}
