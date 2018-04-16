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
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.restapi.change.DeleteChangeMessage.getNewChangeMessage;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
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
  public void deleteCannotBeAppliedWithoutAdministrateServerCapability() throws Exception {
    setApiUser(user);
    String changeId = createChange().getChangeId();
    exception.expect(AuthException.class);
    exception.expectMessage("administrate server not permitted");
    gApi.changes().id(changeId).deleteChangeMessage(createDeleteInput(changeId));
  }

  @Test
  public void deleteCanBeAppliedWithAdministrateServerCapability() throws Exception {
    setApiUser(user);
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ADMINISTRATE_SERVER);

    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).deleteChangeMessage(createDeleteInput(changeId));
  }

  @Test
  public void deleteCannotBeAppliedWithEmptyChangeMessageUuid() throws Exception {
    String changeId = createChange().getChangeId();

    exception.expect(BadRequestException.class);
    exception.expectMessage("change message uuid is required");
    gApi.changes().id(changeId).deleteChangeMessage(new DeleteChangeMessageInput("", "spam"));
  }

  @Test
  public void deleteCannotBeAppliedWithNonExistingChangeMessageUuid() throws Exception {
    String changeId = createChange().getChangeId();
    DeleteChangeMessageInput input = new DeleteChangeMessageInput();
    input.uuid = "8473b95934b5732ac55d26311a706c9c2bde9941";

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(String.format("change message %s not found", input.uuid));
    gApi.changes().id(changeId).deleteChangeMessage(input);
  }

  @Test
  public void deleteWithoutProvidingReason() throws Exception {
    String changeId = createChange().getChangeId();
    List<ChangeMessageInfo> messagesBefore = getChangeMessage(changeId);

    DeleteChangeMessageInput input = new DeleteChangeMessageInput();
    input.uuid = messagesBefore.get(0).id;
    gApi.changes().id(changeId).deleteChangeMessage(input);

    List<ChangeMessageInfo> messagesAfter = getChangeMessage(changeId);
    assertMessagesAfterDeletion(
        messagesBefore, messagesAfter, 0, admin, getNewChangeMessage(admin.username));
  }

  @Test
  public void deleteChangeMessageOfMiddleCommit() throws Exception {
    // Commit 1.
    String changeId = createChange().getChangeId();
    // Commit 2.
    addReview(changeId, "message 1");
    // Commit 3.
    amendChange(changeId);
    // Commit 4.
    addReview(changeId, "message 2");
    // Commit 5.
    amendChange(changeId);
    // Commit 6.
    merge(changeId);

    deleteChangeMessage(changeId, 4, "spam");
  }

  @Test
  public void deleteChangeMessageOfTipCommit() throws Exception {}

  @Test
  public void deleteSameChangeMessageTwice() throws Exception {
    String changeId = createChange().getChangeId();
    DeleteChangeMessageInput input = new DeleteChangeMessageInput();
    input.uuid = "8473b95934b5732ac55d26311a706c9c2bde9941";
    gApi.changes().id(changeId).deleteChangeMessage(input);
  }

  @Test
  public void deleteMultipleChangeMessages() throws Exception {
    String changeId = createChange().getChangeId();
    DeleteChangeMessageInput input = new DeleteChangeMessageInput();
    input.uuid = "8473b95934b5732ac55d26311a706c9c2bde9941";
    gApi.changes().id(changeId).deleteChangeMessage(input);
  }

  private String createChangeForDelete() throws Exception {
    // Commit 1.
    String changeId = createChange().getChangeId();
    // Commit 2.
    addReview(changeId, "message 1");
    // Commit 3.
    amendChange(changeId);
    // Commit 4.
    addReview(changeId, "message 2");
    // Commit 5.
    amendChange(changeId);
    // Commit 6.
    merge(changeId);

    deleteChangeMessage(changeId, 4, "spam");

    return changeId;
  }

  private void deleteChangeMessage(String changeId, int changeMessageIndex, String reason)
      throws Exception {
    List<ChangeMessageInfo> messagesBefore = getChangeMessage(changeId);

    DeleteChangeMessageInput input =
        new DeleteChangeMessageInput(messagesBefore.get(changeMessageIndex).id, reason);
    gApi.changes().id(changeId).deleteChangeMessage(input);

    List<ChangeMessageInfo> messagesAfter = getChangeMessage(changeId);
    assertMessagesAfterDeletion(
        messagesBefore,
        messagesAfter,
        changeMessageIndex,
        admin,
        getNewChangeMessage(admin.username, reason));
  }

  private void addReview(String changeId, String changeMessage) throws Exception {
    ReviewInput.CommentInput comment1 = new ReviewInput.CommentInput();
    comment1.line = 1;
    comment1.message = "comment 1";
    comment1.path = "file1.txt";
    ReviewInput.CommentInput comment2 = new ReviewInput.CommentInput();
    comment1.line = 2;
    comment1.message = "comment 2";
    comment1.path = "file2.txt";

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments =
        ImmutableMap.of(
            comment1.path,
            Lists.newArrayList(comment1),
            comment2.path,
            Lists.newArrayList(comment2));
    reviewInput.message = changeMessage;

    gApi.changes().id(changeId).current().review(reviewInput);
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

  private ChangeMessageInfo getChangeMessage(String changeId, int index) throws Exception {
    List<ChangeMessageInfo> messages = getChangeMessage(changeId);
    assertThat(messages.size()).isAtLeast(index + 1);
    return getChangeMessage(changeId).get(0);
  }

  private List<ChangeMessageInfo> getChangeMessage(String changeId) throws Exception {
    return new ArrayList<>(gApi.changes().id(changeId).get().messages);
  }

  private DeleteChangeMessageInput createDeleteInput(String changeId) throws Exception {
    String uuid = getChangeMessage(changeId, 0).id;
    String reason = "spam";
    return new DeleteChangeMessageInput(uuid, reason);
  }

  private static void assertMessage(String expected, String actual) {
    assertThat(actual).isEqualTo("Patch Set 1:\n\n" + expected);
  }

  private static void assertMessage(ChangeMessageInfo message, ChangeMessageInfo expectedMessage) {
    assertThat(message.id).isEqualTo(expectedMessage.id);
    assertThat(message.tag).isEqualTo(expectedMessage.tag);
    assertThat(message.author._accountId).isEqualTo(expectedMessage.author._accountId);
    assertThat(message.realAuthor).isEqualTo(expectedMessage.realAuthor);
    assertThat(message.date).isEqualTo(expectedMessage.date);
    assertThat(message.message).isEqualTo(expectedMessage.message);
    assertThat(message._revisionNumber).isEqualTo(expectedMessage._revisionNumber);
  }

  private static void assertMessagesAfterDeletion(
      List<ChangeMessageInfo> messagesBeforeDeletion,
      List<ChangeMessageInfo> messagesAfterDeletion,
      int deletedMessageIndex,
      TestAccount deletedBy,
      String deleteReason) {
    assertThat(messagesAfterDeletion).hasSize(messagesBeforeDeletion.size());
    for (int i = 0; i < messagesAfterDeletion.size(); ++i) {
      ChangeMessageInfo before = messagesBeforeDeletion.get(i);
      ChangeMessageInfo after = messagesAfterDeletion.get(i);
      if (i != deletedMessageIndex) {
        assertMessage(after, before);
        continue;
      }

      assertThat(after.id).isEqualTo(before.id);
      assertThat(after.tag).isEqualTo(before.tag);
      assertThat(after.author).isEqualTo(deletedBy.id);
      assertThat(after.realAuthor).isEqualTo(deletedBy.id);
      assertThat(after.message).isEqualTo(getNewChangeMessage(deletedBy.username, deleteReason));
      assertThat(after._revisionNumber).isEqualTo(before._revisionNumber);
    }
  }
}
