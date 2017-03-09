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
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.extensions.common.RobotCommentInfoSubject.assertThatList;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
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
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.BinaryResultSubject;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RobotCommentsIT extends AbstractDaemonTest {
  private static final String FILE_NAME = "file_to_fix.txt";
  private static final String FILE_CONTENT =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
          + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private String changeId;
  private FixReplacementInfo fixReplacementInfo;
  private FixSuggestionInfo fixSuggestionInfo;
  private RobotCommentInput withFixRobotCommentInput;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result changeResult =
        createChange("Provide a file which can be used for fixes", FILE_NAME, FILE_CONTENT);
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
    exception.expectMessage("Range (13:9 - 5:10)");
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void rangesOfFixReplacementsOfSameFixSuggestionMayNotOverlap() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    exception.expect(BadRequestException.class);
    exception.expectMessage("overlap");
    addRobotComment(changeId, withFixRobotCommentInput);
  }

  @Test
  public void rangesOfFixReplacementsOfDifferentFixSuggestionsMayOverlap() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThatList(robotCommentInfos).onlyElement().fixSuggestions().hasSize(2);
  }

  @Test
  public void fixReplacementsDoNotNeedToBeOrderedAccordingToRange() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    addRobotComment(changeId, withFixRobotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = getRobotComments();
    assertThatList(robotCommentInfos).onlyElement().onlyFixSuggestion().replacements().hasSize(3);
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
  public void fixWithinALineCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    addRobotComment(changeId, withFixRobotCommentInput);
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
  public void fixSpanningMultipleLinesCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content\n5";
    fixReplacementInfo.range = createRange(3, 2, 5, 3);

    addRobotComment(changeId, withFixRobotCommentInput);
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
  public void fixWithTwoCloseReplacementsOnSameFileCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    addRobotComment(changeId, withFixRobotCommentInput);
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
  public void twoFixesOnSameFileCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    RobotCommentInput robotCommentInput1 = createRobotCommentInput(fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 = createRobotCommentInput(fixSuggestionInfo2);
    addRobotComment(changeId, robotCommentInput1);
    addRobotComment(changeId, robotCommentInput2);
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
  public void twoConflictingFixesOnSameFileCannotBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    RobotCommentInput robotCommentInput1 = createRobotCommentInput(fixSuggestionInfo1);
    RobotCommentInput robotCommentInput2 = createRobotCommentInput(fixSuggestionInfo2);
    addRobotComment(changeId, robotCommentInput1);
    addRobotComment(changeId, robotCommentInput2);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    expectedException.expect(ResourceConflictException.class);
    expectedException.expectMessage("merge");
    gApi.changes().id(changeId).current().applyFix(fixIds.get(1));
  }

  @Test
  public void twoFixesOfSameRobotCommentCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

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

    addRobotComment(changeId, withFixRobotCommentInput);
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
  public void fixOnPreviousPatchSetWithoutChangeEditCannotBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    expectedException.expect(ResourceConflictException.class);
    expectedException.expectMessage("current");
    gApi.changes().id(changeId).revision(previousRevision).applyFix(fixId);
  }

  @Test
  public void fixOnPreviousPatchSetWithExistingChangeEditCanBeApplied() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).revision(previousRevision).applyFix(fixId);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nTModified contentrd line\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void fixOnCurrentPatchSetWithChangeEditOnPreviousPatchSetCannotBeApplied()
      throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    // Add another patch set.
    amendChange(changeId);

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    expectedException.expect(ResourceConflictException.class);
    expectedException.expectMessage("based");
    gApi.changes().id(changeId).current().applyFix(fixId);
  }

  @Test
  public void fixDoesNotModifyCommitMessageOfChangeEdit() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    String changeEditCommitMessage = "This is the commit message of the change edit.\n";
    gApi.changes().id(changeId).edit().modifyCommitMessage(changeEditCommitMessage);

    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    addRobotComment(changeId, withFixRobotCommentInput);
    List<RobotCommentInfo> robotCommentInfos = getRobotComments();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    gApi.changes().id(changeId).current().applyFix(fixId);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo(changeEditCommitMessage);
  }

  @Test
  public void robotCommentsNotSupportedWithoutNoteDb() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();

    RobotCommentInput in = createRobotCommentInput();
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<RobotCommentInput>> robotComments = new HashMap<>();
    robotComments.put(in.path, ImmutableList.of(in));
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

  private static RobotCommentInput createRobotCommentInputWithMandatoryFields() {
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    return in;
  }

  private static RobotCommentInput createRobotCommentInput(
      FixSuggestionInfo... fixSuggestionInfos) {
    RobotCommentInput in = createRobotCommentInputWithMandatoryFields();
    in.url = "http://www.happy-robot.com";
    in.properties = new HashMap<>();
    in.properties.put("key1", "value1");
    in.properties.put("key2", "value2");
    in.fixSuggestions = Arrays.asList(fixSuggestionInfos);
    return in;
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

  private void addRobotComment(String targetChangeId, RobotCommentInput robotCommentInput)
      throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(robotCommentInput.path, ImmutableList.of(robotCommentInput));
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

  private static List<String> getFixIds(List<RobotCommentInfo> robotComments) {
    assertThatList(robotComments).isNotNull();
    return robotComments
        .stream()
        .map(robotCommentInfo -> robotCommentInfo.fixSuggestions)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(fixSuggestionInfo -> fixSuggestionInfo.fixId)
        .collect(toList());
  }
}
