// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.git.receive;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * Tests for comment validation when publishing drafts via the {@code --publish-comments} option.
 */
public class ReceiveCommitsCommentValidationIT extends AbstractDaemonTest {
  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentHelper testCommentHelper;

  private static final String COMMENT_TEXT = "The comment text";

  @Captor private ArgumentCaptor<ImmutableList<CommentForValidation>> capture;

  @ConfigSuite.Default
  public static Config maxCommentsConfig() {
    Config cfg = new Config();
    cfg.setInt("change", null, "maxComments", 2);  //ö Is this OK for the other tests?
    return cfg;
  }

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        CommentValidator mockCommentValidator = mock(CommentValidator.class);
        bind(CommentValidator.class)
            .annotatedWith(Exports.named(mockCommentValidator.getClass()))
            .toInstance(mockCommentValidator);
        bind(CommentValidator.class).toInstance(mockCommentValidator);
      }
    };
  }

  @Before
  public void resetMock() {
    initMocks(this);
    clearInvocations(mockCommentValidator);
  }

  @Test
  public void validateComments_commentOK() throws Exception {
    when(mockCommentValidator.validateComments(
            ImmutableList.of(
                CommentForValidation.create(
                    CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    amendResult.assertOkStatus();
    amendResult.assertNotMessage("Comment validation failure:");
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);
  }

  @Test
  public void validateComments_commentRejected() throws Exception {
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.FILE_COMMENT, COMMENT_TEXT);
    when(mockCommentValidator.validateComments(
            ImmutableList.of(
                CommentForValidation.create(
                    CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .thenReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    amendResult.assertOkStatus();
    amendResult.assertMessage("Comment validation failure:");
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
  }

  @Test
  public void validateComments_inlineVsFileComments_allOK() throws Exception {
    when(mockCommentValidator.validateComments(capture.capture())).thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    DraftInput draftFile = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftFile);
    DraftInput draftInline =
        testCommentHelper.newDraft(
            result.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(2);
    assertThat(capture.getAllValues()).hasSize(1);
    assertThat(capture.getValue())
        .containsExactly(
            CommentForValidation.create(
                CommentForValidation.CommentType.INLINE_COMMENT, draftInline.message),
            CommentForValidation.create(
                CommentForValidation.CommentType.FILE_COMMENT, draftFile.message));
  }

  @Test
  public void countComments_limitNumberOfComments() throws Exception {
    when(mockCommentValidator.validateComments(any())).thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    DraftInput draftInline =
        testCommentHelper.newDraft(
            result.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);

    addRobotComment(
        changeId, createRobotCommentInput(result.getChange().currentFilePaths().get(0)));

    draftInline =
        testCommentHelper.newDraft(
            result.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);
    //ö Verify the error message.
  }

  // ö Duplicated code; inline & simplify? Extract & share?
  private static RobotCommentInput createRobotCommentInputWithMandatoryFields(String path) {
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = path;
    return in;
  }

  private void addRobotComment(String targetChangeId, RobotCommentInput robotCommentInput)
      throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(robotCommentInput.path, ImmutableList.of(robotCommentInput));
    reviewInput.message = "robot comment test";
    gApi.changes().id(targetChangeId).current().review(reviewInput);
  }

  private static RobotCommentInput createRobotCommentInput(
      String path, FixSuggestionInfo... fixSuggestionInfos) {
    RobotCommentInput in = createRobotCommentInputWithMandatoryFields(path);
    in.url = "http://www.happy-robot.com";
    in.properties = new HashMap<>();
    in.properties.put("key1", "value1");
    in.properties.put("key2", "value2");
    in.fixSuggestions = Arrays.asList(fixSuggestionInfos);
    return in;
  }
}
