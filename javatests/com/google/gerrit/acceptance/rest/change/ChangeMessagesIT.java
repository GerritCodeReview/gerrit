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
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class ChangeMessagesIT extends AbstractDaemonTest {
  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void messagesNotReturnedByDefault() throws Exception {
    String changeId = createChange().getChangeId();
    postMessage(changeId, "Some nits need to be fixed.");
    ChangeInfo c = info(changeId);
    assertThat(c.messages).isNull();
  }

  @Test
  public void defaultMessage() throws Exception {
    String changeId = createChange().getChangeId();
    ChangeInfo c = get(changeId, MESSAGES);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(1);
    assertThat(c.messages.iterator().next().message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void messagesReturnedInChronologicalOrder() throws Exception {
    String changeId = createChange().getChangeId();
    String firstMessage = "Some nits need to be fixed.";
    postMessage(changeId, firstMessage);
    String secondMessage = "I like this feature.";
    postMessage(changeId, secondMessage);
    ChangeInfo c = get(changeId, MESSAGES);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(3);
    Iterator<ChangeMessageInfo> it = c.messages.iterator();
    assertThat(it.next().message).isEqualTo("Uploaded patch set 1.");
    assertMessage(firstMessage, it.next().message);
    assertMessage(secondMessage, it.next().message);
  }

  @Test
  public void postMessageWithTag() throws Exception {
    String changeId = createChange().getChangeId();
    String tag = "jenkins";
    String msg = "Message with tag.";
    postMessage(changeId, msg, tag);
    ChangeInfo c = get(changeId, MESSAGES);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(2);
    Iterator<ChangeMessageInfo> it = c.messages.iterator();
    assertThat(it.next().message).isEqualTo("Uploaded patch set 1.");
    ChangeMessageInfo actual = it.next();
    assertMessage(msg, actual.message);
    assertThat(actual.tag).isEqualTo(tag);
  }

  @Test
  public void listChangeMessages() throws Exception {
    int changeNum = createOneChange();
    List<ChangeMessageInfo> messages1 = gApi.changes().id(changeNum).messages();
    List<ChangeMessageInfo> messages2 =
        new ArrayList<>(gApi.changes().id(changeNum).get().messages);
    assertThat(messages1).containsExactlyElementsIn(messages2).inOrder();
  }

  @Test
  public void getOneChangeMessage() throws Exception {
    int changeNum = createOneChange();
    List<ChangeMessageInfo> messages = new ArrayList<>(gApi.changes().id(changeNum).get().messages);

    for (ChangeMessageInfo messageInfo : messages) {
      String id = messageInfo.id;
      assertThat(gApi.changes().id(changeNum).message(id).get()).isEqualTo(messageInfo);
    }
  }

  private int createOneChange() throws Exception {
    // Creates the following commit history on the meta branch of the test change.

    setApiUser(user);
    // Commit 1: create a change.
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    // Commit 2: post a review with message "message 1".
    setApiUser(admin);
    addOneReview(changeId, "message 1");
    // Commit 3: amend a new patch set.
    setApiUser(user);
    amendChange(changeId);
    // Commit 4: post a review with message "message 2".
    addOneReview(changeId, "message 2");
    // Commit 5: amend a new patch set.
    amendChange(changeId);
    // Commit 6: approve the change.
    setApiUser(admin);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    // commit 7: submit the change.
    gApi.changes().id(changeId).current().submit();

    return result.getChange().getId().get();
  }

  private void addOneReview(String changeId, String changeMessage) throws Exception {
    ReviewInput.CommentInput c = new ReviewInput.CommentInput();
    c.line = 1;
    c.message = "comment 1";
    c.path = FILE_NAME;

    ReviewInput reviewInput = new ReviewInput().label("Code-Review", 1);
    reviewInput.comments = ImmutableMap.of(c.path, Lists.newArrayList(c));
    reviewInput.message = changeMessage;

    gApi.changes().id(changeId).current().review(reviewInput);
  }

  private static void assertMessage(String expected, String actual) {
    assertThat(actual).isEqualTo("Patch Set 1:\n\n" + expected);
  }

  private void postMessage(String changeId, String msg) throws Exception {
    postMessage(changeId, msg, null);
  }

  private void postMessage(String changeId, String msg, String tag) throws Exception {
    ReviewInput in = new ReviewInput();
    in.message = msg;
    in.tag = tag;
    gApi.changes().id(changeId).current().review(in);
  }
}
