// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Tests for the default-deny {@code postReviewComment} change permission: it gates posting reviews,
 * creating and updating draft comments, and adding reviewers/CC, and is bridged by a temporary
 * experiment flag.
 *
 * <p>The permission is seeded for Registered Users on {@code refs/*} in All-Projects, so blocks are
 * placed on {@code refs/heads/*} (a different access section) to avoid the same-section exemption.
 */
public class PostReviewCommentPermissionIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void registeredUserCanPostReviewByDefault() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    int numMessages = gApi.changes().id(r.getChangeId()).get().messages.size();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.create().message("nit"));
    assertThat(gApi.changes().id(r.getChangeId()).get().messages).hasSize(numMessages + 1);
  }

  @Test
  public void blockedUserCannotPostReview() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(r.getChangeId())
                    .current()
                    .review(ReviewInput.create().message("nit")));
    assertThat(thrown).hasMessageThat().contains("post review comment");
  }

  @Test
  public void blockedUserCannotCreateDraft() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).current().createDraft(newDraft()));
    assertThat(thrown).hasMessageThat().contains("post review comment");
  }

  @Test
  public void blockedUserCannotUpdateDraftButCanDelete() throws Exception {
    PushOneCommit.Result r = createChange();
    // The user creates a draft while still permitted (seeded on refs/*).
    requestScopeOperations.setApiUser(user.id());
    CommentInfo draft = gApi.changes().id(r.getChangeId()).current().createDraft(newDraft()).get();

    blockPostReviewComment();

    // Updating the draft is denied ...
    DraftInput update = newDraft();
    update.message = "updated";
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).current().draft(draft.id).update(update));
    assertThat(thrown).hasMessageThat().contains("post review comment");

    // ... but deleting the unpublishable draft is still allowed.
    gApi.changes().id(r.getChangeId()).current().draft(draft.id).delete();
    assertThat(gApi.changes().id(r.getChangeId()).current().draftsAsList()).isEmpty();
  }

  @Test
  public void blockedUserCannotAddReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).addReviewer(admin.email()));
    assertThat(thrown).hasMessageThat().contains("post review comment");
  }

  @Test
  public void blockedUserCannotAddCc() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    ReviewerInput in = new ReviewerInput();
    in.reviewer = admin.email();
    in.state = ReviewerState.CC;
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(r.getChangeId()).addReviewer(in));
    assertThat(thrown).hasMessageThat().contains("post review comment");
  }

  @Test
  public void userBlockedFromPostReviewCommentCannotVote() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    // A label vote is published through PostReview, so the up-front
    // postReviewComment check gates it even when label voting is permitted.
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend()));
    assertThat(thrown).hasMessageThat().contains("post review comment");
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.ALLOW_POST_REVIEW_COMMENT_FOR_REGISTERED_USERS)
  public void experimentFlagAllowsReviewDespiteBlock() throws Exception {
    PushOneCommit.Result r = createChange();
    blockPostReviewComment();
    requestScopeOperations.setApiUser(user.id());
    int numMessages = gApi.changes().id(r.getChangeId()).get().messages.size();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.create().message("nit"));
    assertThat(gApi.changes().id(r.getChangeId()).get().messages).hasSize(numMessages + 1);
  }

  private void blockPostReviewComment() {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.POST_REVIEW_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
  }

  private static DraftInput newDraft() {
    DraftInput in = new DraftInput();
    in.path = FILE_NAME;
    in.line = 1;
    in.message = "nit";
    return in;
  }
}
