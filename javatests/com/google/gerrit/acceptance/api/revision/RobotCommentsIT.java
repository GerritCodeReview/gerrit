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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createFixReplacementInfo;
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createFixSuggestionInfo;
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createRange;
import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.RobotCommentInfoSubject.assertThatList;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class RobotCommentsIT extends AbstractDaemonTest {
  @Inject private TestCommentHelper testCommentHelper;
  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String FILE_NAME = "file_to_fix.txt";
  private static final String FILE_NAME2 = "another_file_to_fix.txt";
  private static final String FILE_NAME3 = "file_without_newline_at_end.txt";
  private static final String FILE_CONTENT =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
          + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";
  private static final String FILE_CONTENT3 = "1st line\n2nd line";

  private String changeId;

  private FixReplacementInfo fixReplacementInfo;
  private FixSuggestionInfo fixSuggestionInfo;
  private RobotCommentInput withFixRobotCommentInput;

  @Before
  public void setUp() throws Exception {
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Provide files which can be used for fixes",
            ImmutableMap.of(
                FILE_NAME, FILE_CONTENT, FILE_NAME2, FILE_CONTENT2, FILE_NAME3, FILE_CONTENT3));
    PushOneCommit.Result changeResult = push.to("refs/for/master");
    changeId = changeResult.getChangeId();

    fixReplacementInfo = createFixReplacementInfo();
    fixSuggestionInfo = createFixSuggestionInfo(fixReplacementInfo);
    withFixRobotCommentInput =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo);
  }

  @Test
  public void retrievingRobotCommentsBeforeAddingAnyDoesNotRaiseAnException() throws Exception {
    Map<String, List<RobotCommentInfo>> robotComments =
        gApi.changes().id(changeId).current().robotComments();

    assertThat(robotComments).isNotNull();
    assertThat(robotComments).isEmpty();
  }

  @Test
  public void addedRobotCommentsCanBeRetrieved() throws Exception {
    RobotCommentInput in = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).current().robotComments();

    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void addedRobotCommentsCanBeRetrievedByChange() throws Exception {
    RobotCommentInput in = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, in);

    pushFactory.create(admin.newIdent(), testRepo, changeId).to("refs/for/master");

    RobotCommentInput in2 = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, in2);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).robotComments();

    assertThat(out).hasSize(1);
    assertThat(out.get(in.path)).hasSize(2);

    RobotCommentInfo comment1 = out.get(in.path).get(0);
    assertRobotComment(comment1, in, false);
    RobotCommentInfo comment2 = out.get(in.path).get(1);
    assertRobotComment(comment2, in2, false);
  }

  @UseClockStep
  @Test
  public void addedRobotCommentsAreLinkedToChangeMessages() throws Exception {
    // Advancing the time after creating the change so that the first robot comment is not in the
    // same timestamp as with the change creation.
    TestTimeUtil.incrementClock(10, TimeUnit.SECONDS);

    RobotCommentInput c1 = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    RobotCommentInput c2 = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    RobotCommentInput c3 = TestCommentHelper.createRobotCommentInput(FILE_NAME);

    // Give the robot comments identifiable names for testing
    c1.message = "robot comment 1";
    c2.message = "robot comment 2";
    c3.message = "robot comment 3";

    testCommentHelper.addRobotComment(changeId, c1, "robot message 1");
    TestTimeUtil.incrementClock(5, TimeUnit.SECONDS);

    testCommentHelper.addRobotComment(changeId, c2, "robot message 2");
    TestTimeUtil.incrementClock(5, TimeUnit.SECONDS);

    testCommentHelper.addRobotComment(changeId, c3, "robot message 3");
    TestTimeUtil.incrementClock(5, TimeUnit.SECONDS);

    Map<String, List<RobotCommentInfo>> robotComments = gApi.changes().id(changeId).robotComments();
    List<RobotCommentInfo> robotCommentsList =
        robotComments.values().stream().flatMap(List::stream).collect(toList());

    List<ChangeMessageInfo> allMessages =
        gApi.changes().id(changeId).get(MESSAGES).messages.stream().collect(toList());

    assertThat(allMessages.stream().map(cm -> cm.message).collect(toList()))
        .containsExactly(
            "Uploaded patch set 1.",
            "Patch Set 1:\n\n(1 comment)\n\nrobot message 1",
            "Patch Set 1:\n\n(1 comment)\n\nrobot message 2",
            "Patch Set 1:\n\n(1 comment)\n\nrobot message 3");

    assertThat(robotCommentsList.stream().map(c -> c.message).collect(toList()))
        .containsExactly("robot comment 1", "robot comment 2", "robot comment 3");

    String message1ChangeId =
        allMessages.stream()
            .filter(c -> c.message.contains("robot message 1"))
            .collect(onlyElement())
            .id;
    String message2ChangeId =
        allMessages.stream()
            .filter(c -> c.message.contains("robot message 2"))
            .collect(onlyElement())
            .id;
    String message3ChangeId =
        allMessages.stream()
            .filter(c -> c.message.contains("robot message 3"))
            .collect(onlyElement())
            .id;

    String comment1MessageId =
        robotCommentsList.stream()
            .filter(c -> c.message.equals("robot comment 1"))
            .collect(onlyElement())
            .changeMessageId;
    String comment2MessageId =
        robotCommentsList.stream()
            .filter(c -> c.message.equals("robot comment 2"))
            .collect(onlyElement())
            .changeMessageId;
    String comment3MessageId =
        robotCommentsList.stream()
            .filter(c -> c.message.equals("robot comment 3"))
            .collect(onlyElement())
            .changeMessageId;

    /**
     * All change messages have the auto-generated tag. Robot comments can be linked to
     * auto-generated messages where each comment is linked to the next nearest change message in
     * timestamp
     */
    assertThat(message1ChangeId).isEqualTo(comment1MessageId);
    assertThat(message2ChangeId).isEqualTo(comment2MessageId);
    assertThat(message3ChangeId).isEqualTo(comment3MessageId);
  }

  @Test
  public void robotCommentsCanBeRetrievedAsList() throws Exception {
    RobotCommentInput robotCommentInput = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos =
        gApi.changes().id(changeId).current().robotCommentsAsList();

    assertThat(robotCommentInfos).hasSize(1);
    RobotCommentInfo robotCommentInfo = Iterables.getOnlyElement(robotCommentInfos);
    assertRobotComment(robotCommentInfo, robotCommentInput);
  }

  @Test
  public void specificRobotCommentCanBeRetrieved() throws Exception {
    RobotCommentInput robotCommentInput = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    RobotCommentInfo robotCommentInfo = Iterables.getOnlyElement(robotCommentInfos);

    RobotCommentInfo specificRobotCommentInfo =
        gApi.changes().id(changeId).current().robotComment(robotCommentInfo.id).get();
    assertRobotComment(specificRobotCommentInfo, robotCommentInput);
  }

  @Test
  public void robotCommentWithoutOptionalFieldsCanBeAdded() throws Exception {
    RobotCommentInput in = TestCommentHelper.createRobotCommentInputWithMandatoryFields(FILE_NAME);
    testCommentHelper.addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).current().robotComments();
    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void patchsetLevelRobotCommentCanBeAddedAndRetrieved() throws Exception {
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(PATCHSET_LEVEL);
    testCommentHelper.addRobotComment(changeId, input);

    List<RobotCommentInfo> results = getRobotComments();
    assertThatList(results).onlyElement().path().isEqualTo(PATCHSET_LEVEL);
  }

  @Test
  public void patchsetLevelRobotCommentCantHaveLine() throws Exception {
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(PATCHSET_LEVEL);
    input.line = 1;
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addRobotComment(changeId, input));
    assertThat(ex.getMessage()).contains("line");
  }

  @Test
  public void patchsetLevelRobotCommentCantHaveRange() throws Exception {
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(PATCHSET_LEVEL);
    input.range = createRange(2, 9, 5, 10);
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addRobotComment(changeId, input));
    assertThat(ex.getMessage()).contains("range");
  }

  @Test
  public void patchsetLevelRobotCommentCantHaveSide() throws Exception {
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(PATCHSET_LEVEL);
    input.side = Side.REVISION;
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addRobotComment(changeId, input));
    assertThat(ex.getMessage()).contains("side");
  }

  @Test
  public void robotCommentInvalidInReplyTo() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(PATCHSET_LEVEL);
    input.inReplyTo = "invalid";
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addRobotComment(changeId, input));
    assertThat(ex.getMessage()).contains("inReplyTo");
  }

  @Test
  public void canCreateRobotCommentWithRobotCommentAsParent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    String parentRobotCommentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().create();

    ReviewInput.RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(COMMIT_MSG);
    robotCommentInput.message = "comment reply";
    robotCommentInput.inReplyTo = parentRobotCommentUuid;
    testCommentHelper.addRobotComment(changeId, robotCommentInput);

    RobotCommentInfo resultComment =
        Iterables.getOnlyElement(
            gApi.changes().id(changeId.get()).current().robotCommentsAsList().stream()
                .filter(c -> c.message.equals("comment reply"))
                .collect(toImmutableSet()));
    assertThat(resultComment.inReplyTo).isEqualTo(parentRobotCommentUuid);
  }

  @Test
  public void canCreateRobotCommentWithHumanCommentAsParent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    String changeIdString = changeOperations.change(changeId).get().changeId();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().create();

    ReviewInput.RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(COMMIT_MSG);
    robotCommentInput.message = "comment reply";
    robotCommentInput.inReplyTo = parentCommentUuid;
    testCommentHelper.addRobotComment(changeIdString, robotCommentInput);

    RobotCommentInfo resultComment =
        Iterables.getOnlyElement(
            gApi.changes().id(changeIdString).current().robotCommentsAsList().stream()
                .filter(c -> c.message.equals("comment reply"))
                .collect(toImmutableSet()));
    assertThat(resultComment.inReplyTo).isEqualTo(parentCommentUuid);
  }

  @Test
  @GerritConfig(name = "change.robotCommentSizeLimit", value = "10k")
  public void maximumAllowedSizeOfRobotCommentCanBeAdjusted() {
    int sizeLimit = 10 << 20;
    fixReplacementInfo.replacement = getStringFor(sizeLimit);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown).hasMessageThat().contains("limit");
  }

  @Test
  public void createdChangeEditIsBasedOnCurrentPatchSet() throws Exception {
    String currentRevision = gApi.changes().id(changeId).get().currentRevision;

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(fixId);

    assertThat(editInfo).baseRevision().isEqualTo(currentRevision);
  }

  @Test
  public void queryChangesWithCommentCounts() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "new content", r1.getChangeId())
            .to("refs/for/master");

    testCommentHelper.addRobotComment(
        r2.getChangeId(), TestCommentHelper.createRobotCommentInputWithMandatoryFields(FILE_NAME));

    try (AutoCloseable ignored = disableNoteDb()) {
      ChangeInfo result = Iterables.getOnlyElement(query(r2.getChangeId()));
      // currently, we create all robot comments as 'resolved' by default.
      // if we allow users to resolve a robot comment, then this test should
      // be modified.
      assertThat(result.unresolvedCommentCount).isEqualTo(0);
      assertThat(result.totalCommentCount).isEqualTo(1);
    }
  }

  private List<RobotCommentInfo> getRobotComments() throws RestApiException {
    return gApi.changes().id(changeId).current().robotCommentsAsList();
  }

  private void assertRobotComment(RobotCommentInfo c, RobotCommentInput expected) {
    assertRobotComment(c, expected, true);
  }

  private void assertRobotComment(
      RobotCommentInfo c, RobotCommentInput expected, boolean expectPath) {
    assertThat(c.robotId).isEqualTo(expected.robotId);
    assertThat(c.robotRunId).isEqualTo(expected.robotRunId);
    assertThat(c.url).isEqualTo(expected.url);
    assertThat(c.properties).isEqualTo(expected.properties);
    assertThat(c.line).isEqualTo(expected.line);
    assertThat(c.message).isEqualTo(expected.message);

    assertThat(c.author.email).isEqualTo(admin.email());

    if (expectPath) {
      assertThat(c.path).isEqualTo(expected.path);
    } else {
      assertThat(c.path).isNull();
    }
  }

  private static String getStringFor(int numberOfBytes) {
    char[] chars = new char[numberOfBytes];
    // 'a' will require one byte even when mapped to a JSON string
    Arrays.fill(chars, 'a');
    return new String(chars);
  }

  private static List<String> getFixIds(List<RobotCommentInfo> robotComments) {
    assertThatList(robotComments).isNotNull();
    return robotComments.stream()
        .map(robotCommentInfo -> robotCommentInfo.fixSuggestions)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(fixSuggestionInfo -> fixSuggestionInfo.fixId)
        .collect(toList());
  }
}
