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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.parseCommitMessageRange;
import static com.google.gerrit.server.restapi.change.DeleteChangeMessage.createNewChangeMessage;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class ChangeMessagesIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

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
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    List<ChangeMessageInfo> messages1 = gApi.changes().id(changeNum).messages();
    List<ChangeMessageInfo> messages2 =
        new ArrayList<>(gApi.changes().id(changeNum).get().messages);
    assertThat(messages1).containsExactlyElementsIn(messages2).inOrder();
  }

  @Test
  public void listChangeMessagesSkippedEmpty() throws Exception {
    // Change message 1: create a change.
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    // Will be a new commit with empty change message on the meta branch.
    addOneReviewWithEmptyChangeMessage(changeId);
    // Change Message 2: post a review with message "message 1".
    addOneReview(changeId, "message");

    List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
    assertThat(messages).hasSize(2);
  }

  @Test
  public void getOneChangeMessage() throws Exception {
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    List<ChangeMessageInfo> messages = new ArrayList<>(gApi.changes().id(changeNum).get().messages);
    for (ChangeMessageInfo messageInfo : messages) {
      String id = messageInfo.id;
      assertThat(gApi.changes().id(changeNum).message(id).get()).isEqualTo(messageInfo);
    }
  }

  @Test
  public void deleteCannotBeAppliedWithoutAdministrateServerCapability() throws Exception {
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    requestScopeOperations.setApiUser(user.id());

    try {
      deleteOneChangeMessage(changeNum, 0, user, "spam");
      fail("expected AuthException");
    } catch (AuthException e) {
      assertThat(e.getMessage()).isEqualTo("administrate server not permitted");
    }
  }

  @Test
  public void deleteCanBeAppliedWithAdministrateServerCapability() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ADMINISTRATE_SERVER);
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    requestScopeOperations.setApiUser(user.id());
    deleteOneChangeMessage(changeNum, 0, user, "spam");
  }

  @Test
  public void deleteCannotBeAppliedWithEmptyChangeMessageUuid() throws Exception {
    String changeId = createChange().getChangeId();

    try {
      gApi.changes().id(changeId).message("").delete(new DeleteChangeMessageInput("spam"));
      fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e.getMessage()).isEqualTo("change message  not found");
    }
  }

  @Test
  public void deleteCannotBeAppliedWithNonExistingChangeMessageUuid() throws Exception {
    String changeId = createChange().getChangeId();
    DeleteChangeMessageInput input = new DeleteChangeMessageInput();
    String id = "8473b95934b5732ac55d26311a706c9c2bde9941";
    input.reason = "spam";

    try {
      gApi.changes().id(changeId).message(id).delete(input);
      fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e.getMessage()).isEqualTo(String.format("change message %s not found", id));
    }
  }

  @Test
  public void deleteCanBeAppliedWithoutProvidingReason() throws Exception {
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    deleteOneChangeMessage(changeNum, 2, admin, "");
  }

  @Test
  public void deleteOneChangeMessageTwice() throws Exception {
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    // Deletes the second change message twice.
    deleteOneChangeMessage(changeNum, 1, admin, "reason 1");
    deleteOneChangeMessage(changeNum, 1, admin, "reason 2");
  }

  @Test
  public void deleteMultipleChangeMessages() throws Exception {
    int changeNum = createOneChangeWithMultipleChangeMessagesInHistory();
    for (int i = 0; i < 7; ++i) {
      deleteOneChangeMessage(changeNum, i, admin, "reason " + i);
    }
  }

  private int createOneChangeWithMultipleChangeMessagesInHistory() throws Exception {
    // Creates the following commit history on the meta branch of the test change.

    requestScopeOperations.setApiUser(user.id());
    // Commit 1: create a change.
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    // Commit 2: post an empty change message.
    requestScopeOperations.setApiUser(admin.id());
    addOneReviewWithEmptyChangeMessage(changeId);
    // Commit 3: post a review with message "message 1".
    addOneReview(changeId, "message 1");
    // Commit 4: amend a new patch set.
    requestScopeOperations.setApiUser(user.id());
    amendChange(changeId);
    // Commit 5: post a review with message "message 2".
    addOneReview(changeId, "message 2");
    // Commit 6: amend a new patch set.
    amendChange(changeId);
    // Commit 7: approve the change.
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    // commit 8: submit the change.
    gApi.changes().id(changeId).current().submit();

    // Verifies there is only 7 change messages although there are 8 commits.
    List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
    assertThat(messages).hasSize(7);

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

  private void addOneReviewWithEmptyChangeMessage(String changeId) throws Exception {
    gApi.changes().id(changeId).current().review(new ReviewInput());
  }

  private void deleteOneChangeMessage(
      int changeNum, int deletedMessageIndex, TestAccount deletedBy, String reason)
      throws Exception {
    List<ChangeMessageInfo> messagesBeforeDeletion = gApi.changes().id(changeNum).messages();

    List<CommentInfo> commentsBefore = getChangeSortedComments(changeNum);
    List<RevCommit> commitsBefore = getChangeMetaCommitsInReverseOrder(new Change.Id(changeNum));

    String id = messagesBeforeDeletion.get(deletedMessageIndex).id;
    DeleteChangeMessageInput input = new DeleteChangeMessageInput(reason);
    ChangeMessageInfo info = gApi.changes().id(changeNum).message(id).delete(input);

    // Verify the return change message info is as expect.
    assertThat(info.message).isEqualTo(createNewChangeMessage(deletedBy.fullName(), reason));
    List<ChangeMessageInfo> messagesAfterDeletion = gApi.changes().id(changeNum).messages();
    assertMessagesAfterDeletion(
        messagesBeforeDeletion, messagesAfterDeletion, deletedMessageIndex, deletedBy, reason);
    assertCommentsAfterDeletion(changeNum, commentsBefore);

    // Verify change index is updated after deletion.
    List<ChangeInfo> changes = gApi.changes().query("message removed").get();
    assertThat(changes.stream().map(c -> c._number).collect(toSet())).contains(changeNum);

    // Verifies states of commits.
    assertMetaCommitsAfterDeletion(commitsBefore, changeNum, id, deletedBy, reason);
  }

  private void assertMessagesAfterDeletion(
      List<ChangeMessageInfo> messagesBeforeDeletion,
      List<ChangeMessageInfo> messagesAfterDeletion,
      int deletedMessageIndex,
      TestAccount deletedBy,
      String deleteReason) {
    assertThat(messagesAfterDeletion)
        .named("after: %s; before: %s", messagesAfterDeletion, messagesBeforeDeletion)
        .hasSize(messagesBeforeDeletion.size());

    for (int i = 0; i < messagesAfterDeletion.size(); ++i) {
      ChangeMessageInfo before = messagesBeforeDeletion.get(i);
      ChangeMessageInfo after = messagesAfterDeletion.get(i);

      if (i < deletedMessageIndex) {
        // The uuid of a commit message will be updated after rewriting.
        assertThat(after.id).isEqualTo(before.id);
      }

      assertThat(after.tag).isEqualTo(before.tag);
      assertThat(after.author).isEqualTo(before.author);
      assertThat(after.realAuthor).isEqualTo(before.realAuthor);
      assertThat(after._revisionNumber).isEqualTo(before._revisionNumber);

      if (i == deletedMessageIndex) {
        assertThat(after.message)
            .isEqualTo(createNewChangeMessage(deletedBy.fullName(), deleteReason));
      } else {
        assertThat(after.message).isEqualTo(before.message);
      }
    }
  }

  private void assertMetaCommitsAfterDeletion(
      List<RevCommit> commitsBeforeDeletion,
      int changeNum,
      String deletedMessageId,
      TestAccount deletedBy,
      String deleteReason)
      throws Exception {
    List<RevCommit> commitsAfterDeletion =
        getChangeMetaCommitsInReverseOrder(new Change.Id(changeNum));
    assertThat(commitsAfterDeletion).hasSize(commitsBeforeDeletion.size());

    for (int i = 0; i < commitsBeforeDeletion.size(); i++) {
      RevCommit commitBefore = commitsBeforeDeletion.get(i);
      RevCommit commitAfter = commitsAfterDeletion.get(i);
      if (commitBefore.getId().getName().equals(deletedMessageId)) {
        byte[] rawBefore = commitBefore.getRawBuffer();
        byte[] rawAfter = commitAfter.getRawBuffer();
        Charset encodingBefore = RawParseUtils.parseEncoding(rawBefore);
        Charset encodingAfter = RawParseUtils.parseEncoding(rawAfter);
        Optional<ChangeNoteUtil.CommitMessageRange> rangeBefore =
            parseCommitMessageRange(commitBefore);
        Optional<ChangeNoteUtil.CommitMessageRange> rangeAfter =
            parseCommitMessageRange(commitAfter);
        assertThat(rangeBefore.isPresent()).isTrue();
        assertThat(rangeAfter.isPresent()).isTrue();

        String subjectBefore =
            decode(
                encodingBefore,
                rawBefore,
                rangeBefore.get().subjectStart(),
                rangeBefore.get().subjectEnd());
        String subjectAfter =
            decode(
                encodingAfter,
                rawAfter,
                rangeAfter.get().subjectStart(),
                rangeAfter.get().subjectEnd());
        assertThat(subjectBefore).isEqualTo(subjectAfter);

        String footersBefore =
            decode(
                encodingBefore,
                rawBefore,
                rangeBefore.get().changeMessageEnd() + 1,
                rawBefore.length);
        String footersAfter =
            decode(
                encodingAfter, rawAfter, rangeAfter.get().changeMessageEnd() + 1, rawAfter.length);
        assertThat(footersBefore).isEqualTo(footersAfter);

        String message =
            decode(
                encodingAfter,
                rawAfter,
                rangeAfter.get().changeMessageStart(),
                rangeAfter.get().changeMessageEnd() + 1);
        assertThat(message).isEqualTo(createNewChangeMessage(deletedBy.fullName(), deleteReason));
      } else {
        assertThat(commitAfter.getFullMessage()).isEqualTo(commitBefore.getFullMessage());
      }

      assertThat(commitAfter.getCommitterIdent().getName())
          .isEqualTo(commitBefore.getCommitterIdent().getName());
      assertThat(commitAfter.getAuthorIdent().getName())
          .isEqualTo(commitBefore.getAuthorIdent().getName());
      assertThat(commitAfter.getEncoding()).isEqualTo(commitBefore.getEncoding());
      assertThat(commitAfter.getEncodingName()).isEqualTo(commitBefore.getEncodingName());
    }
  }

  /** Verifies comments are not changed after deleting change message(s). */
  private void assertCommentsAfterDeletion(int changeNum, List<CommentInfo> commentsBeforeDeletion)
      throws Exception {
    List<CommentInfo> commentsAfterDeletion = getChangeSortedComments(changeNum);
    assertThat(commentsAfterDeletion).containsExactlyElementsIn(commentsBeforeDeletion).inOrder();
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
