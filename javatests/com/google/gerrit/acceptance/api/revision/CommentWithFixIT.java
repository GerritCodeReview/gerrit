// Copyright (C) 2024 The Android Open Source Project
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
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createFixReplacementInfo;
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createFixSuggestionInfo;
import static com.google.gerrit.acceptance.server.change.CommentsUtil.createRange;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThatList;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.testing.BinaryResultSubject;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class CommentWithFixIT extends AbstractDaemonTest {
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
  private CommentInput withFixCommentInput;

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
    withFixCommentInput = TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo);
  }

  @ConfigSuite.Default
  public static Config setExperimentFlag() {
    Config cfg = new Config();
    cfg.setString(
        "experiments",
        null,
        "enabled",
        ExperimentFeaturesConstants.ALLOW_FIX_SUGGESTIONS_IN_COMMENTS);
    return cfg;
  }

  @Test
  public void fixSuggestionCannotPointToPatchsetLevel() throws Exception {
    CommentInput input = TestCommentHelper.createCommentInput(FILE_NAME);
    FixReplacementInfo brokenFixReplacement = createFixReplacementInfo();
    brokenFixReplacement.path = PATCHSET_LEVEL;
    input.fixSuggestions = ImmutableList.of(createFixSuggestionInfo(brokenFixReplacement));
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> testCommentHelper.addComment(changeId, input));
    assertThat(ex.getMessage()).contains("file path must not be " + PATCHSET_LEVEL);
  }

  @Test
  public void hugeCommentIsRejected() {
    int defaultSizeLimit = 1 << 20;
    fixReplacementInfo.replacement = getStringFor(defaultSizeLimit + 1);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown).hasMessageThat().contains("limit");
  }

  @Test
  public void reasonablyLargeCommentIsAccepted() throws Exception {
    int defaultSizeLimit = 1 << 10;
    // Allow for a few hundred bytes in other fields.
    fixReplacementInfo.replacement = getStringFor(defaultSizeLimit - 666);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThat(commentInfos).hasSize(1);
  }

  @Test
  @GerritConfig(name = "change.commentSizeLimit", value = "0")
  public void zeroForMaximumAllowedSizeOfCommentRemovesRestriction() throws Exception {
    int defaultSizeLimit = 1 << 10;
    fixReplacementInfo.replacement = getStringFor(2 * defaultSizeLimit);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThat(commentInfos).hasSize(1);
  }

  @Test
  @GerritConfig(name = "change.commentSizeLimit", value = "-1")
  public void negativeValueForMaximumAllowedSizeOfCommentRemovesRestriction() throws Exception {
    int defaultSizeLimit = 1 << 20;
    fixReplacementInfo.replacement = getStringFor(2 * defaultSizeLimit);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThat(commentInfos).hasSize(1);
  }

  @Test
  public void addedFixSuggestionCanBeRetrieved() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos).onlyElement().onlyFixSuggestion().isNotNull();
  }

  @Test
  public void fixIdIsGeneratedForFixSuggestion() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos).onlyElement().onlyFixSuggestion().fixId().isNotEmpty();
    assertThatList(commentInfos)
        .onlyElement()
        .onlyFixSuggestion()
        .fixId()
        .isNotEqualTo(fixSuggestionInfo.fixId);
  }

  @Test
  public void descriptionOfFixSuggestionIsAcceptedAsIs() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos)
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
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A description is required for the suggested fix of the comment on %s",
                withFixCommentInput.path));
  }

  @Test
  public void addedFixReplacementCanBeRetrieved() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos).onlyElement().onlyFixSuggestion().onlyReplacement().isNotNull();
  }

  @Test
  public void fixReplacementsAreMandatory() {
    fixSuggestionInfo.replacements = Collections.emptyList();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "At least one replacement is required"
                    + " for the suggested fix of the comment on %s",
                withFixCommentInput.path));
  }

  @Test
  public void pathOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos)
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
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A file path must be given for the replacement of the comment on %s",
                withFixCommentInput.path));
  }

  @Test
  public void rangeOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos)
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
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A range must be given for the replacement of the comment on %s",
                withFixCommentInput.path));
  }

  @Test
  public void rangeOfFixReplacementNeedsToBeValid() {
    fixReplacementInfo.range = createRange(13, 9, 5, 10);
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown).hasMessageThat().contains("Range (13:9 - 5:10)");
  }

  @Test
  public void commentWithRangeAndLine_lineIsIgnored() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";

    withFixCommentInput.line = 1;
    withFixCommentInput.range = createRange(2, 0, 3, 1);
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> comments = getComments();
    assertThat(comments.get(0).line).isEqualTo(3);
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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThatList(commentInfos).onlyElement().fixSuggestions().hasSize(1);
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

    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo1, fixSuggestionInfo2);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThatList(commentInfos).onlyElement().fixSuggestions().hasSize(2);
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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();
    assertThatList(commentInfos).onlyElement().onlyFixSuggestion().replacements().hasSize(3);
  }

  @Test
  public void replacementStringOfFixReplacementIsAcceptedAsIs() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);

    List<CommentInfo> commentInfos = getComments();

    assertThatList(commentInfos)
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
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format(
                "A content for replacement must be "
                    + "indicated for the replacement of the comment on %s",
                withFixCommentInput.path));
  }

  @Test
  public void storedFixWithinALineCanBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME;
    fixReplacementInfo.replacement = "Modified content";
    fixReplacementInfo.range = createRange(3, 1, 3, 3);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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
    testCommentHelper.addComment(project + "~" + change.get(), withFixCommentInput);

    // Change preferred email for the user
    String emailTwo = "email2@example.com";
    accountOperations.account(testUser).forUpdate().preferredEmail(emailTwo).update();
    requestScopeOperations.setApiUser(testUser);

    // Fetch Fix ID
    List<CommentInfo> commentInfoList = gApi.changes().id(change.get()).current().commentsAsList();

    List<String> fixIds = getFixIds(commentInfoList);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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

    CommentInput commentInput1 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo1);
    CommentInput commentInput2 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addComment(changeId, commentInput1);
    testCommentHelper.addComment(changeId, commentInput2);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

    CommentInput commentInput1 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo1);
    CommentInput commentInput2 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addComment(changeId, commentInput1);
    testCommentHelper.addComment(changeId, commentInput2);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
    gApi.changes().id(changeId).current().applyFix(fixIds.get(0));
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(fixIds.get(1)));
    assertThat(thrown).hasMessageThat().contains("merge");
  }

  @Test
  public void twoStoredFixesOfSameCommentCanBeApplied() throws Exception {
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

    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo1, fixSuggestionInfo2);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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
  public void storedFixReferringToDifferentFileThanCommentCanBeApplied() throws Exception {
    fixReplacementInfo.path = FILE_NAME2;
    fixReplacementInfo.range = createRange(2, 0, 3, 0);
    fixReplacementInfo.replacement = "Modified content\n";

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getComments()));

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

    withFixCommentInput.path = Patch.COMMIT_MSG;
    fixReplacementInfo.path = Patch.COMMIT_MSG;
    fixReplacementInfo.replacement = "Modified line\n";
    fixReplacementInfo.range = createRange(7, 0, 8, 0);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getComments()));

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

    withFixCommentInput.path = Patch.COMMIT_MSG;
    fixReplacementInfo.path = Patch.COMMIT_MSG;
    fixReplacementInfo.replacement = "Modified line\n";
    fixReplacementInfo.range = createRange(1, 0, 2, 0);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getComments()));

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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);
    withFixCommentInput.path = Patch.COMMIT_MSG;

    testCommentHelper.addComment(changeId, withFixCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getComments()));

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
    withFixCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    String fixId = Iterables.getOnlyElement(getFixIds(getComments()));

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

    CommentInput commentInput1 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo1);
    CommentInput commentInput2 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addComment(changeId, commentInput1);
    testCommentHelper.addComment(changeId, commentInput2);
    List<String> fixIds = getFixIds(getComments());

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

    CommentInput commentInput1 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo1);
    CommentInput commentInput2 =
        TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo2);
    testCommentHelper.addComment(changeId, commentInput1);
    testCommentHelper.addComment(changeId, commentInput2);
    List<String> fixIds = getFixIds(getComments());

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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(fixId);

    Optional<EditInfo> expectedEditInfo = gApi.changes().id(changeId).edit().get();
    String expectedEditCommit = expectedEditInfo.map(edit -> edit.commit.commit).orElse("");
    assertThat(editInfo).commit().commit().isEqualTo(expectedEditCommit);
    String expectedBaseRevision = expectedEditInfo.map(edit -> edit.baseRevision).orElse("");
    assertThat(editInfo).baseRevision().isEqualTo(expectedBaseRevision);
  }

  @Test
  public void previewStoredFixWithNonexistentFixId() throws Exception {
    testCommentHelper.addComment(changeId, withFixCommentInput);

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
    CommentInput commitMsgCommentInput =
        TestCommentHelper.createCommentInput(Patch.COMMIT_MSG, commitMsgSuggestionInfo);
    testCommentHelper.addComment(changeId, commitMsgCommentInput);

    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

  @Test
  @GerritConfig(
      name = "experiments.disabled",
      values = {ExperimentFeaturesConstants.ALLOW_FIX_SUGGESTIONS_IN_COMMENTS})
  public void commentWithFixFailsToPersistWithoutFeatureFlag() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> testCommentHelper.addComment(changeId, withFixCommentInput));
    assertThat(thrown).hasMessageThat().contains("feature flag prohibits setting fixSuggestions");
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
    CommentInput commentInput = TestCommentHelper.createCommentInput(FILE_NAME2, fixSuggestion);
    testCommentHelper.addComment(changeId, commentInput);

    List<CommentInfo> commentInfos = getComments();
    List<String> fixIds = getFixIds(commentInfos);
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

    withFixCommentInput = TestCommentHelper.createCommentInput(FILE_NAME, fixSuggestionInfo);

    testCommentHelper.addComment(changeId, withFixCommentInput);
    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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
    CommentInput commentInput = TestCommentHelper.createCommentInput(FILE_NAME3, fixSuggestion);
    testCommentHelper.addComment(changeId, commentInput);

    List<CommentInfo> commentInfos = getComments();

    List<String> fixIds = getFixIds(commentInfos);
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

  private List<CommentInfo> getComments() throws RestApiException {
    return gApi.changes().id(changeId).current().commentsAsList();
  }

  private static String getStringFor(int numberOfBytes) {
    char[] chars = new char[numberOfBytes];
    // 'a' will require one byte even when mapped to a JSON string
    Arrays.fill(chars, 'a');
    return new String(chars);
  }

  private static List<String> getFixIds(List<CommentInfo> comments) {
    assertThatList(comments).isNotNull();
    return comments.stream()
        .map(commentInfo -> commentInfo.fixSuggestions)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(fixSuggestionInfo -> fixSuggestionInfo.fixId)
        .collect(toList());
  }
}
