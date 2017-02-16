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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.extensions.common.RobotCommentInfoSubject.assertThatList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class RobotCommentsIT extends AbstractDaemonTest {
  private String changeId;
  private FixReplacementInfo fixReplacementInfo;
  private FixSuggestionInfo fixSuggestionInfo;
  private RobotCommentInput withFixRobotCommentInput;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    changeId = changeResult.getChangeId();

    fixReplacementInfo = createFixReplacementInfo();
    fixSuggestionInfo = createFixSuggestionInfo(fixReplacementInfo);
    withFixRobotCommentInput = createRobotCommentInput(fixSuggestionInfo);
  }

  @Test
  public void retrievingRobotCommentsBeforeAddingAnyDoesNotRaiseAnException() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    Map<String, List<RobotCommentInfo>> robotComments =
        gApi.changes().id(changeId).current().robotComments();

    assertThat(robotComments).isNotNull();
    assertThat(robotComments).isEmpty();
  }

  @Test
  public void addedRobotCommentsCanBeRetrieved() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput in = createRobotCommentInput();
    addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).current().robotComments();

    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void addedRobotCommentsCanBeRetrievedByChange() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput in = createRobotCommentInput();
    addRobotComment(changeId, in);

    pushFactory.create(db, admin.getIdent(), testRepo, changeId).to("refs/for/master");

    RobotCommentInput in2 = createRobotCommentInput();
    addRobotComment(changeId, in2);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).robotComments();

    assertThat(out).hasSize(1);
    assertThat(out.get(in.path)).hasSize(2);

    RobotCommentInfo comment1 = out.get(in.path).get(0);
    assertRobotComment(comment1, in, false);
    RobotCommentInfo comment2 = out.get(in.path).get(1);
    assertRobotComment(comment2, in2, false);
  }

  @Test
  public void robotCommentsCanBeRetrievedAsList() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput robotCommentInput = createRobotCommentInput();
    addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos =
        gApi.changes().id(changeId).current().robotCommentsAsList();

    assertThat(robotCommentInfos).hasSize(1);
    RobotCommentInfo robotCommentInfo = Iterables.getOnlyElement(robotCommentInfos);
    assertRobotComment(robotCommentInfo, robotCommentInput);
  }

  @Test
  public void specificRobotCommentCanBeRetrieved() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput robotCommentInput = createRobotCommentInput();
    addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    RobotCommentInfo robotCommentInfo = Iterables.getOnlyElement(robotCommentInfos);

    RobotCommentInfo specificRobotCommentInfo =
        gApi.changes().id(changeId).current().robotComment(robotCommentInfo.id).get();
    assertRobotComment(specificRobotCommentInfo, robotCommentInput);
  }

  @Test
  public void robotCommentWithoutOptionalFieldsCanBeAdded() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput in = createRobotCommentInputWithMandatoryFields();
    addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes().id(changeId).current().robotComments();
    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void addedFixSuggestionCanBeRetrieved() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos).onlyElement().onlyFixSuggestion().isNotNull();
  }

  @Test
  public void fixIdIsGeneratedForFixSuggestion() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);
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
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .description()
        .isEqualTo(fixSuggestionInfo.description);
  }

  @Test
  public void descriptionOfFixSuggestionIsMandatory() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixSuggestionInfo.description = null;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "A description is required for the suggested fix of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void addedFixReplacementCanBeRetrieved() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .isNotNull();
  }

  @Test
  public void fixReplacementsAreMandatory() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixSuggestionInfo.replacements = Collections.emptyList();

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "At least one replacement is required"
                + " for the suggested fix of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void pathOfFixReplacementIsAcceptedAsIs() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .path()
        .isEqualTo(fixReplacementInfo.path);
  }

  @Test
  public void pathOfFixReplacementIsMandatory() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.path = null;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "A file path must be given for the replacement of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void pathOfFixReplacementMustReferToFileOfComment() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.path = "anotherFile.txt";

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "Replacements may only be specified "
                + "for the file %s on which the robot comment was added",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void rangeOfFixReplacementIsAcceptedAsIs() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .range()
        .isEqualTo(fixReplacementInfo.range);
  }

  @Test
  public void rangeOfFixReplacementIsMandatory() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.range = null;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "A range must be given for the replacement of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void rangeOfFixReplacementNeedsToBeValid() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.range = createRange(13, 9, 5, 10);

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "Range (13:9 - 5:10) is not valid for the replacement of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void replacementStringOfFixReplacementIsAcceptedAsIs() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    assertThatList(robotCommentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .onlyReplacement()
        .replacement()
        .isEqualTo(fixReplacementInfo.replacement);
  }

  @Test
  public void replacementStringOfFixReplacementIsMandatory() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.replacement = null;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        String.format(
            "A content for replacement must be "
                + "indicated for the replacement of the robot comment on %s",
            withFixRobotCommentInput.path));
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void robotCommentsNotSupportedWithoutNoteDb() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();

    RobotCommentInput in = createRobotCommentInput();
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<RobotCommentInput>> robotComments = new HashMap<>();
    robotComments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.robotComments = robotComments;
    reviewInput.message = "comment test";

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("robot comments not supported");
    gApi.changes().id(changeId).current().review(reviewInput);
  }

  @Test
  public void queryChangesWithUnresolvedCommentCount() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 =
        pushFactory
            .create(
                db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new content", r1.getChangeId())
            .to("refs/for/master");

    addRobotComment(r2.getChangeId(), createRobotCommentInputWithMandatoryFields());

    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      ChangeInfo result = Iterables.getOnlyElement(query(r2.getChangeId()));
      // currently, we create all robot comments as 'resolved' by default.
      // if we allow users to resolve a robot comment, then this test should
      // be modified.
      assertThat(result.unresolvedCommentCount).isEqualTo(0);
    } finally {
      enableDb(ctx);
    }
  }

  private RobotCommentInput createRobotCommentInputWithMandatoryFields() {
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    return in;
  }

  private RobotCommentInput createRobotCommentInput(FixSuggestionInfo... fixSuggestionInfos) {
    RobotCommentInput in = createRobotCommentInputWithMandatoryFields();
    in.url = "http://www.happy-robot.com";
    in.properties = new HashMap<>();
    in.properties.put("key1", "value1");
    in.properties.put("key2", "value2");
    in.fixSuggestions = Arrays.asList(fixSuggestionInfos);
    return in;
  }

  private FixSuggestionInfo createFixSuggestionInfo(FixReplacementInfo... fixReplacementInfos) {
    FixSuggestionInfo newFixSuggestionInfo = new FixSuggestionInfo();
    newFixSuggestionInfo.fixId = "An ID which must be overwritten.";
    newFixSuggestionInfo.description = "A description for a suggested fix.";
    newFixSuggestionInfo.replacements = Arrays.asList(fixReplacementInfos);
    return newFixSuggestionInfo;
  }

  private FixReplacementInfo createFixReplacementInfo() {
    FixReplacementInfo newFixReplacementInfo = new FixReplacementInfo();
    newFixReplacementInfo.path = FILE_NAME;
    newFixReplacementInfo.replacement = "some replacement code";
    newFixReplacementInfo.range = createRange(3, 12, 15, 4);
    return newFixReplacementInfo;
  }

  private Comment.Range createRange(
      int startLine, int startCharacter, int endLine, int endCharacter) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.startCharacter = startCharacter;
    range.endLine = endLine;
    range.endCharacter = endCharacter;
    return range;
  }

  private void addRobotComment(String targetChangeId, RobotCommentInput robotCommentInput)
      throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(
            robotCommentInput.path, Collections.singletonList(robotCommentInput));
    reviewInput.message = "robot comment test";
    gApi.changes().id(targetChangeId).current().review(reviewInput);
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

    assertThat(c.author.email).isEqualTo(admin.email);

    if (expectPath) {
      assertThat(c.path).isEqualTo(expected.path);
    } else {
      assertThat(c.path).isNull();
    }
  }
}
