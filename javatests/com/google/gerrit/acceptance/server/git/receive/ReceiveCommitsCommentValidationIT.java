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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for comment validation when publishing drafts via the {@code --publish-comments} option.
 */
public class ReceiveCommitsCommentValidationIT extends AbstractDaemonTest {
  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentHelper testCommentHelper;

  private static final String COMMENT_TEXT = "The comment text";

  private Capture<ImmutableList<CommentForValidation>> capture = new Capture<>();

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        CommentValidator mockCommentValidator = EasyMock.createMock(CommentValidator.class);
        bind(CommentValidator.class)
            .annotatedWith(Exports.named(mockCommentValidator.getClass()))
            .toInstance(mockCommentValidator);
        bind(CommentValidator.class).toInstance(mockCommentValidator);
      }
    };
  }

  @Before
  public void resetMock() {
    EasyMock.reset(mockCommentValidator);
  }

  @After
  public void verifyMock() {
    EasyMock.verify(mockCommentValidator);
  }

  @Test
  public void validateComments_commentOK() throws Exception {
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(
                        CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);
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
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(
                        CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    EasyMock.replay(mockCommentValidator);
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
    EasyMock.expect(mockCommentValidator.validateComments(EasyMock.capture(capture)))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);
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
    assertThat(capture.getValues()).hasSize(1);
    assertThat(capture.getValue())
        .containsExactly(
            CommentForValidation.create(
                CommentForValidation.CommentType.INLINE_COMMENT, draftInline.message),
            CommentForValidation.create(
                CommentForValidation.CommentType.FILE_COMMENT, draftFile.message));
  }
}
