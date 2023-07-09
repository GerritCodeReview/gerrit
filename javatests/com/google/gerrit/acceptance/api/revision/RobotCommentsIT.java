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
import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.RobotCommentInfoSubject.assertThatList;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.testing.BinaryResultSubject;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class RobotCommentsIT extends AbstractDaemonTest {
  @Inject private TestCommentHelper testCommentHelper;
  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String PLAIN_TEXT_CONTENT_TYPE = "text/plain";
  private static final String GERRIT_COMMIT_MESSAGE_TYPE = "text/x-gerrit-commit-message";

  private static final String FILE_NAME = "file_to_fix.txt";
  private static final String FILE_NAME2 = "another_file_to_fix.txt";
  private static final String FILE_NAME3 = "file_without_newline_at_end.txt";
  private static final String FILE_CONTENT =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
          + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";
  private static final String FILE_CONTENT3 = "1st line\n2nd line";

  private String changeId;
  private String commitId;
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
    commitId = changeResult.getCommit().getName();

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
  public void fixSuggestionCannotPointToPatchsetLevel() throws Exception {
    RobotCommentInput input = TestCommentHelper.createRobotCommentInput(FILE_NAME);
    FixReplacementInfo brokenFixReplacement = createFixReplacementInfo();
    brokenFixReplacement.path = PATCHSET_LEVEL;
    input.fixSuggestions = ImmutableList.of(createFixSuggestionInfo(brokenFixReplacement));
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addRobotComment(changeId, input));
    assertThat(ex.getMessage()).contains("file path must not be " + PATCHSET_LEVEL);
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
  public void hugeRobotCommentIsRejected() {
    int defaultSizeLimit = 1 << 20;
    fixReplacementInfo.replacement = getStringFor(defaultSizeLimit + 1);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown).hasMessageThat().contains("limit");
  }

  @Test
  public void reasonablyLargeRobotCommentIsAccepted() throws Exception {
    int defaultSizeLimit = 1 << 20;
    // Allow for a few hundred bytes in other fields.
    fixReplacementInfo.replacement = getStringFor(defaultSizeLimit - 666);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThat(robotCommentInfos).hasSize(1);
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
  @GerritConfig(name = "change.robotCommentSizeLimit", value = "0")
  public void zeroForMaximumAllowedSizeOfRobotCommentRemovesRestriction() throws Exception {
    int defaultSizeLimit = 1 << 20;
    fixReplacementInfo.replacement = getStringFor(2 * defaultSizeLimit);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThat(robotCommentInfos).hasSize(1);
  }

  @Test
  @GerritConfig(name = "change.robotCommentSizeLimit", value = "-1")
  public void negativeValueForMaximumAllowedSizeOfRobotCommentRemovesRestriction()
      throws Exception {
    int defaultSizeLimit = 1 << 20;
    fixReplacementInfo.replacement = getStringFor(2 * defaultSizeLimit);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThat(robotCommentInfos).hasSize(1);
  }

  @Test
  public void addedFixSuggestionCanBeRetrieved() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos).onlyElement().onlyFixSuggestion().isNotNull();
  }

  @Test
  public void fixIdIsGeneratedForFixSuggestion() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos).onlyElement().onlyFixSuggestion().fixId().isNotEmpty();
    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .fixId()
        .isNotEqualTo(fixSuggestionInfo.fixId);
  }

  @Test
  public void descriptionOfFixSuggestionIsAcceptedAsIs() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .description()
        .isEqualTo(fixSuggestionInfo.description);
  }

  @Test
  public void descriptionOfFixSuggestionIsMandatory() {
    fixSuggestionInfo.description = null;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A description is required for the suggested fix of the robot comment on %s",
                withFixRobotCommentInput.path));
  }

  @Test
  public void addedFixReplacementCanBeRetrieved() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .isNotNull();
  }

  @Test
  public void fixReplacementsAreMandatory() {
    fixSuggestionInfo.replacements = Collections.emptyList();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "At least one replacement is required"
                    + " for the suggested fix of the robot comment on %s",
                withFixRobotCommentInput.path));
  }

  @Test
  public void pathOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .path()
        .isEqualTo(fixReplacementInfo.path);
  }

  @Test
  public void pathOfFixReplacementIsMandatory() {
    fixReplacementInfo.path = null;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A file path must be given for the replacement of the robot comment on %s",
                withFixRobotCommentInput.path));
  }

  @Test
  public void rangeOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .range()
        .isEqualTo(fixReplacementInfo.range);
  }

  @Test
  public void rangeOfFixReplacementIsMandatory() {
    fixReplacementInfo.range = null;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A range must be given for the replacement of the robot comment on %s",
                withFixRobotCommentInput.path));
  }

  @Test
  public void rangeOfFixReplacementNeedsToBeValid() {
    fixReplacementInfo.range = createRange(13, 9, 5, 10);
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown).hasMessageThat().contains("Range (13:9 - 5:10)");
  }

  @Test
  public void commentWithRangeAndLine_lineIsIgnored() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";

    withFixRobotCommentInput.line = 1;
    withFixRobotCommentInput.range = createRange(2, 0, 3, 1);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotComments = getRobotComments();
    assertThat(robotComments.get(0).line).isEqualTo(3);
  }

  @Test
  public void rangesOfFixReplacementsOfSameFixSuggestionForSameFileMayNotOverlap() {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Second modification\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown).hasMessageThat().contains("overlap");
  }

  @Test
  public void rangesOfFixReplacementsOfSameFixSuggestionForDifferentFileMayOverlap()
      throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Second modification\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThatList(robotCommentInfos).onlyElement().fixSuggestions().hasSize(1);
  }

  @Test
  public void rangesOfFixReplacementsOfDifferentFixSuggestionsForSameFileMayOverlap()
      throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Second modification\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    withFixRobotCommentInput.fixSuggestions =
        ImmutableList.of(fixSuggestionInfo1, fixSuggestionInfo2);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThatList(robotCommentInfos).onlyElement().fixSuggestions().hasSize(2);
  }

  @Test
  public void fixReplacementsDoNotNeedToBeOrderedAccordingToRange() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Second modification\n";

    FixReplacementInfo fixReplacementInfo3 = new FixReplacementInfo();
    fixReplacementInfo3.path = FILE_NAME;
    fixReplacementInfo3.range = createRange(4, 0, 5, 0);
    fixReplacementInfo3.replacement = "Third modification\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo2, fixReplacementInfo1, fixReplacementInfo3);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThatList(robotCommentInfos).onlyElement().onlyFixSuggestion().replacements().hasSize(3);
  }

  @Test
  public void replacementStringOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .replacement()
        .isEqualTo(fixReplacementInfo.replacement);
  }

  @Test
  public void replacementStringOfFixReplacementIsMandatory() {
    fixReplacementInfo.replacement = null;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A content for replacement must be "
                    + "indicated for the replacement of the robot comment on %s",
                withFixRobotCommentInput.path));
  }

  @Test
  public void storedFixWithinALineCanBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nTModified contentrd line\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void applyStoredFixAfterUpdatingPreferredEmail() throws Exception {
    String emailOne = "email1@example.com";
    Account.Id testUser = accountOperations.newAccount().preferredEmail(emailOne).create();

    // Create change
    Change.Id change =
        changeOperations
            .newChange()
            .project(project)
            .file(FILE_NAME)
            .content(FILE_CONTENT)
            .owner(testUser)
            .create();

    // Add Robot Comment to the change
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);
    testCommentHelper.addRobotComment(project + "~" + change.get(), withFixRobotCommentInput);

    // Change preferred email for the user
    String emailTwo = "email2@example.com";
    accountOperations.account(testUser).forUpdate().preferredEmail(emailTwo).update();
    requestScopeOperations.setApiUser(testUser);

    // Fetch Fix ID
    List<RobotCommentInfo> robotCommentInfoList =
        gApi.changes().id(change.get()).current().robotCommentsAsList();

    List<String> fixIds = getFixIds(robotCommentInfoList);
    String fixId = Iterables.getOnlyElement(fixIds);

    // Apply fix
    gApi.changes().id(change.get()).current().applyFix(fixId);

    EditInfo editInfo = gApi.changes().id(change.get()).edit().get().orElseThrow();
    assertThat(editInfo.commit.committer.email).isEqualTo(emailOne);
  }

  @Test
  public void storedFixSpanningMultipleLinesCanBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content\n5";
    fixReplacementInfo.range = createRange(3, 2, 5, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nThModified content\n5th line\nSixth line\nSeventh line\n"
                + "Eighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void storedFixWithTwoCloseReplacementsOnSameFileCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nSome other modified content\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void twoStoredFixesOnSameFileCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(8, 0, 9, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    RobotCommentInput robotCommentInput1 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addRobotComment(changeId, robotCommentInput1);
    testCommentHelper.addRobotComment(changeId, robotCommentInput2);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    gApi.changes().id(changeId).current().applyFix(fixIds.get(1));

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nSome other modified content\nNinth line\nTenth line\n");
  }

  @Test
  public void twoConflictingStoredFixesOnSameFileCannotBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    RobotCommentInput robotCommentInput1 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addRobotComment(changeId, robotCommentInput1);
    testCommentHelper.addRobotComment(changeId, robotCommentInput2);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(fixIds.get(1)));
    assertThat(thrown).hasMessageThat().contains("merge");
  }

  @Test
  public void twoStoredFixesOfSameRobotCommentCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(8, 0, 9, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    withFixRobotCommentInput.fixSuggestions =
        ImmutableList.of(fixSuggestionInfo1, fixSuggestionInfo2);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    gApi.changes().id(changeId).current().applyFix(fixIds.get(1));

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nSome other modified content\nNinth line\nTenth line\n");
  }

  @Test
  public void storedFixReferringToDifferentFileThanRobotCommentCanBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME2;
    fixReplacementInfo.range = createRange(2, 0, 3, 0);
    fixReplacementInfo.replacement = "Modified content\n";

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME2);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo("1st line\nModified content\n3rd line\n");
  }

  @Test
  public void storedFixInvolvingTwoFilesCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.range = createRange(1, 0, 2, 0);
    fixReplacementInfo2.replacement = "Different file modification\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nEighth line\nNinth line\nTenth line\n");
    Optional<BinaryResult> file2 = gApi.changes().id(changeId).edit().getFile(FILE_NAME2);
    BinaryResultSubject.assertThat(file2)
        .value()
        .asString()
        .isEqualTo("Different file modification\n2nd line\n3rd line\n");
  }

  @Test
  public void storedFixReferringToNonExistentFileCannotBeApplied() throws Exception {
    fixReplacementInfo.path = "a_non_existent_file.txt";
    fixReplacementInfo.range = createRange(1, 0, 2, 0);
    fixReplacementInfo.replacement = "Modified content\n";

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().applyFix(fixId));
  }

  @Test
  public void storedFixOnPreviousPatchSetWithoutChangeEditCannotBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).revision(previousRevision).applyFix(fixId));
    assertThat(thrown).hasMessageThat().contains("current");
  }

  @Test
  public void storedFixOnPreviousPatchSetWithExistingChangeEditCanBeApplied() throws Exception {
    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    EditInfo editInfo = gApi.changes().id(changeId).revision(previousRevision).applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nTModified contentrd line\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
    assertThat(editInfo).baseRevision().isEqualTo(previousRevision);
  }

  @Test
  public void storedFixOnCurrentPatchSetWithChangeEditOnPreviousPatchSetCannotBeApplied()
      throws Exception {
    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();

    // Add another patch set.
    amendChange(changeId);

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(fixId));
    assertThat(thrown).hasMessageThat().contains("based");
  }

  @Test
  public void storedFixDoesNotModifyCommitMessageOfChangeEdit() throws Exception {
    String changeEditCommitMessage =
        "This is the commit message of the change edit.\n\nChange-Id: " + changeId + "\n";
    gApi.changes().id(changeId).edit().modifyCommitMessage(changeEditCommitMessage);

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getRobotComments()));

    gApi.changes().id(changeId).current().applyFix(fixId);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo(changeEditCommitMessage);
  }

  @Test
  public void storedFixOnCommitMessageCanBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage = "Line 1 of commit message\nLine 2 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    withFixRobotCommentInput.path = Patch.COMMIT_MSG;
    fixReplacementInfo.path = Patch.COMMIT_MSG;
    fixReplacementInfo.replacement = "Modified line\n";
    fixReplacementInfo.range = createRange(7, 0, 8, 0);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getRobotComments()));

    gApi.changes().id(changeId).current().applyFix(fixId);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo("Modified line\nLine 2 of commit message\n" + footer);
  }

  @Test
  public void storedFixOnHeaderPartOfCommitMessageCannotBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "Change-Id: " + changeId;
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\n" + "\n" + footer + "\n";
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    withFixRobotCommentInput.path = Patch.COMMIT_MSG;
    fixReplacementInfo.path = Patch.COMMIT_MSG;
    fixReplacementInfo.replacement = "Modified line\n";
    fixReplacementInfo.range = createRange(1, 0, 2, 0);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getRobotComments()));

    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(fixId));
    assertThat(exception).hasMessageThat().contains("header");
  }

  @Test
  public void storedFixContainingSeveralModificationsOfCommitMessageCanBeApplied()
      throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\nLine 3 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = Patch.COMMIT_MSG;
    fixReplacementInfo2.range = createRange(9, 0, 10, 0);
    fixReplacementInfo2.replacement = "Modified line 3\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);
    withFixRobotCommentInput.path = Patch.COMMIT_MSG;

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getRobotComments()));

    gApi.changes().id(changeId).current().applyFix(fixId);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage)
        .isEqualTo("Modified line 1\nLine 2 of commit message\nModified line 3\n" + footer);
  }

  @Test
  public void storedFixModifyingTheCommitMessageAndAFileCanBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage = "Line 1 of commit message\nLine 2 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.range = createRange(1, 0, 2, 0);
    fixReplacementInfo2.replacement = "File modification\n";

    FixSuggestionInfo fixSuggestionInfo =
        createFixSuggestionInfo(fixReplacementInfo1, fixReplacementInfo2);
    withFixRobotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getRobotComments()));

    gApi.changes().id(changeId).current().applyFix(fixId);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo("Modified line 1\nLine 2 of commit message\n" + footer);
    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME2);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo("File modification\n2nd line\n3rd line\n");
  }

  @Test
  public void twoStoredFixesOnCommitMessageCanBeAppliedOneAfterTheOther() throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\nLine 3 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = Patch.COMMIT_MSG;
    fixReplacementInfo2.range = createRange(9, 0, 10, 0);
    fixReplacementInfo2.replacement = "Modified line 3\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    RobotCommentInput robotCommentInput1 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addRobotComment(changeId, robotCommentInput1);
    testCommentHelper.addRobotComment(changeId, robotCommentInput2);
    List<String> fixIds = getFixIds(getRobotComments());

    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    gApi.changes().id(changeId).current().applyFix(fixIds.get(1));

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage)
        .isEqualTo("Modified line 1\nLine 2 of commit message\nModified line 3\n" + footer);
  }

  @Test
  public void twoConflictingStoredFixesOnCommitMessageCanNotBeAppliedOneAfterTheOther()
      throws Exception {
    // Set a dedicated commit message.
    String footer = "Change-Id: " + changeId;
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\nLine 3 of commit message\n\n"
            + footer
            + "\n";
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";
    FixSuggestionInfo fixSuggestionInfo1 = createFixSuggestionInfo(fixReplacementInfo1);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = Patch.COMMIT_MSG;
    fixReplacementInfo2.range = createRange(7, 0, 10, 0);
    fixReplacementInfo2.replacement = "Differently modified line 1\n";
    FixSuggestionInfo fixSuggestionInfo2 = createFixSuggestionInfo(fixReplacementInfo2);

    RobotCommentInput robotCommentInput1 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addRobotComment(changeId, robotCommentInput1);
    testCommentHelper.addRobotComment(changeId, robotCommentInput2);
    List<String> fixIds = getFixIds(getRobotComments());

    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    assertThrows(
        ResourceConflictException.class,
        () -> gApi.changes().id(changeId).current().applyFix(fixIds.get(1)));
  }

  @Test
  public void applyingStoredFixTwiceIsIdempotent() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);
    String expectedEditCommit =
        gApi.changes().id(changeId).edit().get().map(edit -> edit.commit.commit).orElse("");

    // Apply the fix again.
    gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<EditInfo> editInfo = gApi.changes().id(changeId).edit().get();
    assertThat(editInfo).value().commit().commit().isEqualTo(expectedEditCommit);
  }

  @Test
  public void nonExistentStoredFixCannotBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);
    String nonExistentFixId = fixId + "_non-existent";

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().applyFix(nonExistentFixId));
  }

  @Test
  public void applyingStoredFixReturnsEditInfoForCreatedChangeEdit() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<EditInfo> expectedEditInfo = gApi.changes().id(changeId).edit().get();
    String expectedEditCommit = expectedEditInfo.map(edit -> edit.commit.commit).orElse("");
    assertThat(editInfo).commit().commit().isEqualTo(expectedEditCommit);
    String expectedBaseRevision = expectedEditInfo.map(edit -> edit.baseRevision).orElse("");
    assertThat(editInfo).baseRevision().isEqualTo(expectedBaseRevision);
  }

  @Test
  public void applyingStoredFixOnTopOfChangeEditReturnsEditInfoForUpdatedChangeEdit()
      throws Exception {
    gApi.changes().id(changeId).edit().create();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<EditInfo> expectedEditInfo = gApi.changes().id(changeId).edit().get();
    String expectedEditCommit = expectedEditInfo.map(edit -> edit.commit.commit).orElse("");
    assertThat(editInfo).commit().commit().isEqualTo(expectedEditCommit);
    String expectedBaseRevision = expectedEditInfo.map(edit -> edit.baseRevision).orElse("");
    assertThat(editInfo).baseRevision().isEqualTo(expectedBaseRevision);
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

  @Test
  public void previewStoredFixWithNonexistentFixId() throws Exception {
    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().getFixPreview("Non existing fixId"));
  }

  @Test
  public void previewStoredFixForCommitMsg() throws Exception {
    String footer = "Change-Id: " + changeId;
    updateCommitMessage(
        changeId,
        "Commit title\n\nCommit message line 1\nLine 2\nLine 3\nLast line\n\n" + footer + "\n");
    FixReplacementInfo commitMsgReplacement = new FixReplacementInfo();
    commitMsgReplacement.path = Patch.COMMIT_MSG;
    // The test assumes that the first 5 lines is a header.
    // Line 10 has content "Line 2"
    commitMsgReplacement.range = createRange(10, 0, 11, 0);
    commitMsgReplacement.replacement = "New content\n";

    FixSuggestionInfo commitMsgSuggestionInfo = createFixSuggestionInfo(commitMsgReplacement);
    RobotCommentInput commitMsgRobotCommentInput =
        TestCommentHelper.createRobotCommentInput(Patch.COMMIT_MSG, commitMsgSuggestionInfo);
    testCommentHelper.addRobotComment(changeId, commitMsgRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    Map<String, DiffInfo> fixPreview = gApi.changes().id(changeId).current().getFixPreview(fixId);
    assertThat(fixPreview).hasSize(1);
    assertThat(fixPreview).containsKey(Patch.COMMIT_MSG);

    DiffInfo diff = fixPreview.get(Patch.COMMIT_MSG);
    assertThat(diff).metaA().name().isEqualTo(Patch.COMMIT_MSG);
    assertThat(diff).metaA().contentType().isEqualTo(GERRIT_COMMIT_MESSAGE_TYPE);
    assertThat(diff).metaB().name().isEqualTo(Patch.COMMIT_MSG);
    assertThat(diff).metaB().contentType().isEqualTo(GERRIT_COMMIT_MESSAGE_TYPE);

    assertThat(diff).content().element(0).commonLines().hasSize(9);
    // Header has a dynamic content, do not check it
    assertThat(diff).content().element(0).commonLines().element(6).isEqualTo("Commit title");
    assertThat(diff).content().element(0).commonLines().element(7).isEqualTo("");
    assertThat(diff)
        .content()
        .element(0)
        .commonLines()
        .element(8)
        .isEqualTo("Commit message line 1");
    assertThat(diff).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diff).content().element(1).linesOfB().containsExactly("New content");
    assertThat(diff)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Line 3", "Last line", "", footer, "");
  }

  private void updateCommitMessage(String changeId, String newCommitMessage) throws Exception {
    gApi.changes().id(changeId).edit().create();
    gApi.changes().id(changeId).edit().modifyCommitMessage(newCommitMessage);
    PublishChangeEditInput publishInput = new PublishChangeEditInput();
    gApi.changes().id(changeId).edit().publish(publishInput);
  }

  @Test
  public void previewStoredFixForNonExistingFile() throws Exception {
    FixReplacementInfo replacement = new FixReplacementInfo();
    replacement.path = "a_non_existent_file.txt";
    replacement.range = createRange(1, 0, 2, 0);
    replacement.replacement = "Modified content\n";

    FixSuggestionInfo fixSuggestion = createFixSuggestionInfo(replacement);
    RobotCommentInput commentInput =
        TestCommentHelper.createRobotCommentInput(FILE_NAME2, fixSuggestion);
    testCommentHelper.addRobotComment(changeId, commentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().getFixPreview(fixId));
  }

  @Test
  public void previewStoredFix() throws Exception {
    FixReplacementInfo fixReplacementInfoFile1 = new FixReplacementInfo();
    fixReplacementInfoFile1.path = FILE_NAME;
    fixReplacementInfoFile1.replacement = "some replacement code";
    fixReplacementInfoFile1.range = createRange(3, 9, 8, 4);

    FixReplacementInfo fixReplacementInfoFile2 = new FixReplacementInfo();
    fixReplacementInfoFile2.path = FILE_NAME2;
    fixReplacementInfoFile2.replacement = "New line\n";
    fixReplacementInfoFile2.range = createRange(2, 0, 2, 0);

    fixSuggestionInfo = createFixSuggestionInfo(fixReplacementInfoFile1, fixReplacementInfoFile2);

    withFixRobotCommentInput =
        TestCommentHelper.createRobotCommentInput(FILE_NAME, fixSuggestionInfo);

    testCommentHelper.addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    Map<String, DiffInfo> fixPreview = gApi.changes().id(changeId).current().getFixPreview(fixId);
    assertThat(fixPreview).hasSize(2);
    assertThat(fixPreview).containsKey(FILE_NAME);
    assertThat(fixPreview).containsKey(FILE_NAME2);

    DiffInfo diff = fixPreview.get(FILE_NAME);
    assertThat(diff).intralineStatus().isEqualTo(IntraLineStatus.OK);
    assertThat(diff).webLinks().isNull();
    assertThat(diff).binary().isNull();
    assertThat(diff).diffHeader().isNull();
    assertThat(diff).changeType().isEqualTo(ChangeType.MODIFIED);
    assertThat(diff).metaA().totalLineCount().isEqualTo(11);
    assertThat(diff).metaA().name().isEqualTo(FILE_NAME);
    assertThat(diff).metaA().commitId().isEqualTo(commitId);
    assertThat(diff).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff).metaA().webLinks().isNull();
    assertThat(diff).metaB().totalLineCount().isEqualTo(6);
    assertThat(diff).metaB().name().isEqualTo(FILE_NAME);
    assertThat(diff).metaB().commitId().isNull();
    assertThat(diff).metaB().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff).metaB().webLinks().isNull();

    assertThat(diff).content().hasSize(3);
    assertThat(diff)
        .content()
        .element(0)
        .commonLines()
        .containsExactly("First line", "Second line");
    assertThat(diff).content().element(0).linesOfA().isNull();
    assertThat(diff).content().element(0).linesOfB().isNull();

    assertThat(diff).content().element(1).commonLines().isNull();
    assertThat(diff)
        .content()
        .element(1)
        .linesOfA()
        .containsExactly(
            "Third line", "Fourth line", "Fifth line", "Sixth line", "Seventh line", "Eighth line");
    assertThat(diff)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Third linsome replacement codeth line");

    assertThat(diff)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Ninth line", "Tenth line", "");
    assertThat(diff).content().element(2).linesOfA().isNull();
    assertThat(diff).content().element(2).linesOfB().isNull();

    DiffInfo diff2 = fixPreview.get(FILE_NAME2);
    assertThat(diff2).intralineStatus().isEqualTo(IntraLineStatus.OK);
    assertThat(diff2).webLinks().isNull();
    assertThat(diff2).binary().isNull();
    assertThat(diff2).diffHeader().isNull();
    assertThat(diff2).changeType().isEqualTo(ChangeType.MODIFIED);
    assertThat(diff2).metaA().totalLineCount().isEqualTo(4);
    assertThat(diff2).metaA().name().isEqualTo(FILE_NAME2);
    assertThat(diff2).metaA().commitId().isEqualTo(commitId);
    assertThat(diff2).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff2).metaA().webLinks().isNull();
    assertThat(diff2).metaB().totalLineCount().isEqualTo(5);
    assertThat(diff2).metaB().name().isEqualTo(FILE_NAME2);
    assertThat(diff2).metaB().commitId().isNull();
    assertThat(diff2).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff2).metaB().webLinks().isNull();

    assertThat(diff2).content().hasSize(3);
    assertThat(diff2).content().element(0).commonLines().containsExactly("1st line");
    assertThat(diff2).content().element(0).linesOfA().isNull();
    assertThat(diff2).content().element(0).linesOfB().isNull();

    assertThat(diff2).content().element(1).commonLines().isNull();
    assertThat(diff2).content().element(1).linesOfA().isNull();
    assertThat(diff2).content().element(1).linesOfB().containsExactly("New line");

    assertThat(diff2)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("2nd line", "3rd line", "");
    assertThat(diff2).content().element(2).linesOfA().isNull();
    assertThat(diff2).content().element(2).linesOfB().isNull();
  }

  @Test
  public void previewStoredFixAddNewLineAtEnd() throws Exception {
    FixReplacementInfo replacement = new FixReplacementInfo();
    replacement.path = FILE_NAME3;
    replacement.range = createRange(2, 8, 2, 8);
    replacement.replacement = "\n";

    FixSuggestionInfo fixSuggestion = createFixSuggestionInfo(replacement);
    RobotCommentInput commentInput =
        TestCommentHelper.createRobotCommentInput(FILE_NAME3, fixSuggestion);
    testCommentHelper.addRobotComment(changeId, commentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    Map<String, DiffInfo> fixPreview = gApi.changes().id(changeId).current().getFixPreview(fixId);

    assertThat(fixPreview).hasSize(1);
    assertThat(fixPreview).containsKey(FILE_NAME3);

    DiffInfo diff = fixPreview.get(FILE_NAME3);
    assertThat(diff).metaA().totalLineCount().isEqualTo(2);
    // Original file doesn't have EOL marker at the end of file.
    // Due to the additional EOL mark diff has one additional line
    // This behavior is in line with ordinary get diff API.
    assertThat(diff).metaB().totalLineCount().isEqualTo(3);

    assertThat(diff).content().hasSize(2);
    assertThat(diff).content().element(0).commonLines().containsExactly("1st line");
    assertThat(diff).content().element(1).linesOfA().containsExactly("2nd line");
    assertThat(diff).content().element(1).linesOfB().containsExactly("2nd line", "");
  }

  private static FixSuggestionInfo createFixSuggestionInfo(
      FixReplacementInfo... fixReplacementInfos) {
    FixSuggestionInfo newFixSuggestionInfo = new FixSuggestionInfo();
    newFixSuggestionInfo.fixId = "An ID which must be overwritten.";
    newFixSuggestionInfo.description = "A description for a suggested fix.";
    newFixSuggestionInfo.replacements = Arrays.asList(fixReplacementInfos);
    return newFixSuggestionInfo;
  }

  private static FixReplacementInfo createFixReplacementInfo() {
    FixReplacementInfo newFixReplacementInfo = new FixReplacementInfo();
    newFixReplacementInfo.path = FILE_NAME;
    newFixReplacementInfo.replacement = "some replacement code";
    newFixReplacementInfo.range = createRange(3, 9, 8, 4);
    return newFixReplacementInfo;
  }

  private static Comment.Range createRange(
      int startLine, int startCharacter, int endLine, int endCharacter) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.startCharacter = startCharacter;
    range.endLine = endLine;
    range.endCharacter = endCharacter;
    return range;
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
