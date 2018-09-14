// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.acceptance.rest.util.RestCall.Method.GET;
import static com.google.gerrit.extensions.common.testing.RobotCommentInfoSubject.assertThatList;
import static java.util.stream.Collectors.toList;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.reviewdb.client.Patch;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Test;

/**
 * Tests for checking the bindings of the changes REST API.
 *
 * <p>These tests only verify that the change REST endpoints are correctly bound, they do no test
 * the functionality of the change REST endpoints.
 */
public class ChangesRestApiBindingsIT extends AbstractDaemonTest {
  /**
   * Change REST endpoints to be tested, each URL contains a placeholder for the change identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s"),
          RestCall.get("/changes/%s/detail"),
          RestCall.get("/changes/%s/topic"),
          RestCall.put("/changes/%s/topic"),
          RestCall.delete("/changes/%s/topic"),
          RestCall.get("/changes/%s/in"),
          RestCall.get("/changes/%s/hashtags"),
          RestCall.get("/changes/%s/comments"),
          RestCall.get("/changes/%s/robotcomments"),
          RestCall.get("/changes/%s/drafts"),
          RestCall.get("/changes/%s/assignee"),
          RestCall.get("/changes/%s/past_assignees"),
          RestCall.put("/changes/%s/assignee"),
          RestCall.delete("/changes/%s/assignee"),
          RestCall.post("/changes/%s/private"),
          RestCall.post("/changes/%s/private.delete"),
          RestCall.delete("/changes/%s/private"),
          RestCall.post("/changes/%s/wip"),
          RestCall.post("/changes/%s/ready"),
          RestCall.put("/changes/%s/ignore"),
          RestCall.put("/changes/%s/unignore"),
          RestCall.put("/changes/%s/reviewed"),
          RestCall.put("/changes/%s/unreviewed"),
          RestCall.get("/changes/%s/messages"),
          RestCall.put("/changes/%s/message"),
          RestCall.post("/changes/%s/merge"),
          RestCall.post("/changes/%s/abandon"),
          RestCall.post("/changes/%s/move"),
          RestCall.post("/changes/%s/rebase"),
          RestCall.post("/changes/%s/restore"),
          RestCall.post("/changes/%s/revert"),
          RestCall.get("/changes/%s/pure_revert"),
          RestCall.post("/changes/%s/submit"),
          RestCall.get("/changes/%s/submitted_together"),
          RestCall.post("/changes/%s/index"),
          RestCall.get("/changes/%s/check"),
          RestCall.post("/changes/%s/check"),
          RestCall.get("/changes/%s/reviewers"),
          RestCall.post("/changes/%s/reviewers"),
          RestCall.get("/changes/%s/suggest_reviewers"),
          RestCall.builder(GET, "/changes/%s/revisions")
              // GET /changes/<change-id>/revisions is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),
          RestCall.get("/changes/%s/edit"),
          RestCall.post("/changes/%s/edit"),
          RestCall.post("/changes/%s/edit:rebase"),
          RestCall.get("/changes/%s/edit:message"),
          RestCall.put("/changes/%s/edit:message"),

          // Publish edit and create a new edit
          RestCall.post("/changes/%s/edit:publish"),
          RestCall.put("/changes/%s/edit/a.txt"),

          // Deletion of change edit and change must be tested last
          RestCall.delete("/changes/%s/edit"),
          RestCall.delete("/changes/%s"));

  /**
   * Reviewer REST endpoints to be tested, each URL contains placeholders for the change identifier
   * and the reviewer identifier.
   */
  private static final ImmutableList<RestCall> REVIEWER_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/reviewers/%s"),
          RestCall.get("/changes/%s/reviewers/%s/votes"),
          RestCall.post("/changes/%s/reviewers/%s/delete"),
          RestCall.delete("/changes/%s/reviewers/%s"));

  /**
   * Vote REST endpoints to be tested, each URL contains placeholders for the change identifier, the
   * reviewer identifier and the label identifier.
   */
  private static final ImmutableList<RestCall> VOTE_ENDPOINTS =
      ImmutableList.of(
          RestCall.post("/changes/%s/reviewers/%s/votes/%s/delete"),
          RestCall.delete("/changes/%s/reviewers/%s/votes/%s"));

  /**
   * Revision REST endpoints to be tested, each URL contains placeholders for the change identifier
   * and the revision identifier.
   */
  private static final ImmutableList<RestCall> REVISION_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/revisions/%s/actions"),
          RestCall.post("/changes/%s/revisions/%s/cherrypick"),
          RestCall.get("/changes/%s/revisions/%s/commit"),
          RestCall.get("/changes/%s/revisions/%s/mergeable"),
          RestCall.get("/changes/%s/revisions/%s/related"),
          RestCall.get("/changes/%s/revisions/%s/review"),
          RestCall.post("/changes/%s/revisions/%s/review"),
          RestCall.get("/changes/%s/revisions/%s/preview_submit"),
          RestCall.post("/changes/%s/revisions/%s/submit"),
          RestCall.get("/changes/%s/revisions/%s/submit_type"),
          RestCall.post("/changes/%s/revisions/%s/test.submit_rule"),
          RestCall.post("/changes/%s/revisions/%s/test.submit_type"),
          RestCall.post("/changes/%s/revisions/%s/rebase"),
          RestCall.get("/changes/%s/revisions/%s/description"),
          RestCall.put("/changes/%s/revisions/%s/description"),
          RestCall.get("/changes/%s/revisions/%s/patch"),
          RestCall.get("/changes/%s/revisions/%s/archive"),
          RestCall.get("/changes/%s/revisions/%s/mergelist"),
          RestCall.get("/changes/%s/revisions/%s/reviewers"),
          RestCall.get("/changes/%s/revisions/%s/drafts"),
          RestCall.put("/changes/%s/revisions/%s/drafts"),
          RestCall.get("/changes/%s/revisions/%s/comments"),
          RestCall.get("/changes/%s/revisions/%s/robotcomments"),
          RestCall.builder(GET, "/changes/%s/revisions/%s/fixes")
              // GET /changes/<change>/revisions/<revision>/fixes is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),
          RestCall.get("/changes/%s/revisions/%s/files"));

  /**
   * Revision reviewer REST endpoints to be tested, each URL contains placeholders for the change
   * identifier, the revision identifier and the reviewer identifier.
   */
  private static final ImmutableList<RestCall> REVISION_REVIEWER_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/revisions/%s/reviewers/%s"),
          RestCall.get("/changes/%s/revisions/%s/reviewers/%s/votes"),
          RestCall.post("/changes/%s/revisions/%s/reviewers/%s/delete"),
          RestCall.delete("/changes/%s/revisions/%s/reviewers/%s"));

  /**
   * Revision vote REST endpoints to be tested, each URL contains placeholders for the change
   * identifier, the revision identifier, the reviewer identifier and the label identifier.
   */
  private static final ImmutableList<RestCall> REVISION_VOTE_ENDPOINTS =
      ImmutableList.of(
          RestCall.post("/changes/%s/revisions/%s/reviewers/%s/votes/%s/delete"),
          RestCall.delete("/changes/%s/revisions/%s/reviewers/%s/votes/%s"));

  /**
   * Draft comment REST endpoints to be tested, each URL contains placeholders for the change
   * identifier, the revision identifier and the draft comment identifier.
   */
  private static final ImmutableList<RestCall> DRAFT_COMMENT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/revisions/%s/drafts/%s"),
          RestCall.put("/changes/%s/revisions/%s/drafts/%s"),
          RestCall.delete("/changes/%s/revisions/%s/drafts/%s"));

  /**
   * Comment REST endpoints to be tested, each URL contains placeholders for the change identifier,
   * the revision identifier and the comment identifier.
   */
  private static final ImmutableList<RestCall> COMMENT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/revisions/%s/comments/%s"),
          RestCall.delete("/changes/%s/revisions/%s/comments/%s"),
          RestCall.post("/changes/%s/revisions/%s/comments/%s/delete"));

  /**
   * Robot comment REST endpoints to be tested, each URL contains placeholders for the change
   * identifier, the revision identifier and the robot comment identifier.
   */
  private static final ImmutableList<RestCall> ROBOT_COMMENT_ENDPOINTS =
      ImmutableList.of(RestCall.get("/changes/%s/revisions/%s/robotcomments/%s"));

  /**
   * Fix REST endpoints to be tested, each URL contains placeholders for the change identifier, the
   * revision identifier and the fix identifier.
   */
  private static final ImmutableList<RestCall> FIX_ENDPOINTS =
      ImmutableList.of(RestCall.post("/changes/%s/revisions/%s/fixes/%s/apply"));

  /**
   * Revision file REST endpoints to be tested, each URL contains placeholders for the change
   * identifier, the revision identifier and the file identifier.
   */
  private static final ImmutableList<RestCall> REVISION_FILE_ENDPOINTS =
      ImmutableList.of(
          RestCall.put("/changes/%s/revisions/%s/files/%s/reviewed"),
          RestCall.delete("/changes/%s/revisions/%s/files/%s/reviewed"),
          RestCall.get("/changes/%s/revisions/%s/files/%s/content"),
          RestCall.get("/changes/%s/revisions/%s/files/%s/download"),
          RestCall.get("/changes/%s/revisions/%s/files/%s/diff"),
          RestCall.get("/changes/%s/revisions/%s/files/%s/blame"));

  /**
   * Change message REST endpoints to be tested, each URL contains placeholders for the change
   * identifier and the change message identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_MESSAGE_ENDPOINTS =
      ImmutableList.of(RestCall.get("/changes/%s/messages/%s"));

  /**
   * Change edit REST endpoints that create an edit to be tested, each URL contains placeholders for
   * the change identifier and the change edit identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_EDIT_CREATE_ENDPOINTS =
      ImmutableList.of(
          // Create change edit by editing an existing file.
          RestCall.put("/changes/%s/edit/%s"),

          // Create change edit by deleting an existing file.
          RestCall.delete("/changes/%s/edit/%s"));

  /**
   * Change edit REST endpoints to be tested, each URL contains placeholders for the change
   * identifier and the change edit identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_EDIT_ENDPOINTS =
      ImmutableList.of(
          // Calls on existing change edit.
          RestCall.get("/changes/%s/edit/%s"),
          RestCall.put("/changes/%s/edit/%s"),
          RestCall.get("/changes/%s/edit/%s/meta"),

          // Delete content of a file in an existing change edit.
          RestCall.delete("/changes/%s/edit/%s"));

  private static final String FILENAME = "test.txt";

  @Test
  public void changeEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).edit().create();
    RestApiCallHelper.execute(adminRestSession, CHANGE_ENDPOINTS, changeId);
  }

  @Test
  public void reviewerEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.reviewer = user.email();

    RestApiCallHelper.execute(
        adminRestSession,
        REVIEWER_ENDPOINTS,
        () -> gApi.changes().id(changeId).addReviewer(addReviewerInput),
        changeId,
        addReviewerInput.reviewer);
  }

  @Test
  public void voteEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    RestApiCallHelper.execute(
        adminRestSession,
        VOTE_ENDPOINTS,
        () -> gApi.changes().id(changeId).current().review(ReviewInput.approve()),
        changeId,
        admin.email(),
        "Code-Review");
  }

  @Test
  public void revisionEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    RestApiCallHelper.execute(adminRestSession, REVISION_ENDPOINTS, changeId, "current");
  }

  @Test
  public void revisionReviewerEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.reviewer = user.email();

    RestApiCallHelper.execute(
        adminRestSession,
        REVISION_REVIEWER_ENDPOINTS,
        () -> gApi.changes().id(changeId).addReviewer(addReviewerInput),
        changeId,
        "current",
        addReviewerInput.reviewer);
  }

  @Test
  public void revisionVoteEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    RestApiCallHelper.execute(
        adminRestSession,
        REVISION_VOTE_ENDPOINTS,
        () -> gApi.changes().id(changeId).current().review(ReviewInput.approve()),
        changeId,
        "current",
        admin.email(),
        "Code-Review");
  }

  @Test
  public void draftCommentEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    for (RestCall restCall : DRAFT_COMMENT_ENDPOINTS) {
      DraftInput draftInput = new DraftInput();
      draftInput.path = Patch.COMMIT_MSG;
      draftInput.side = Side.REVISION;
      draftInput.line = 1;
      draftInput.message = "draft comment";
      CommentInfo draftInfo = gApi.changes().id(changeId).current().createDraft(draftInput).get();

      RestApiCallHelper.execute(adminRestSession, restCall, changeId, "current", draftInfo.id);
    }
  }

  @Test
  public void commentEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    for (RestCall restCall : COMMENT_ENDPOINTS) {
      DraftInput draftInput = new DraftInput();
      draftInput.path = Patch.COMMIT_MSG;
      draftInput.side = Side.REVISION;
      draftInput.line = 1;
      draftInput.message = "draft comment";
      CommentInfo commentInfo = gApi.changes().id(changeId).current().createDraft(draftInput).get();

      ReviewInput reviewInput = new ReviewInput();
      reviewInput.drafts = DraftHandling.PUBLISH;
      gApi.changes().id(changeId).current().review(reviewInput);

      RestApiCallHelper.execute(adminRestSession, restCall, changeId, "current", commentInfo.id);
    }
  }

  @Test
  public void robotCommentEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    RobotCommentInput robotCommentInput = new RobotCommentInput();
    robotCommentInput.robotId = "happyRobot";
    robotCommentInput.robotRunId = "1";
    robotCommentInput.line = 1;
    robotCommentInput.message = "nit: trailing whitespace";
    robotCommentInput.path = Patch.COMMIT_MSG;

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(robotCommentInput.path, ImmutableList.of(robotCommentInput));
    reviewInput.message = "robot comment test";
    gApi.changes().id(changeId).current().review(reviewInput);

    List<RobotCommentInfo> robotCommentInfos =
        gApi.changes().id(changeId).current().robotCommentsAsList();
    RobotCommentInfo robotCommentInfo = Iterables.getOnlyElement(robotCommentInfos);

    RestApiCallHelper.execute(
        adminRestSession, ROBOT_COMMENT_ENDPOINTS, changeId, "current", robotCommentInfo.id);
  }

  @Test
  public void fixEndpoints() throws Exception {
    String changeId = createChange("Subject", FILENAME, "content").getChangeId();

    RobotCommentInput robotCommentInput = new RobotCommentInput();
    robotCommentInput.robotId = "happyRobot";
    robotCommentInput.robotRunId = "1";
    robotCommentInput.line = 1;
    robotCommentInput.message = "nit: trailing whitespace";
    robotCommentInput.path = FILENAME;

    FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
    fixReplacementInfo.path = FILENAME;
    fixReplacementInfo.replacement = "some replacement code";
    fixReplacementInfo.range = createRange(1, 1, 1, 2);

    FixSuggestionInfo fixSuggestionInfo = new FixSuggestionInfo();
    fixSuggestionInfo.fixId = "An ID which must be overwritten.";
    fixSuggestionInfo.description = "A description for a suggested fix.";
    fixSuggestionInfo.replacements = ImmutableList.of(fixReplacementInfo);

    robotCommentInput.fixSuggestions = ImmutableList.of(fixSuggestionInfo);

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(robotCommentInput.path, ImmutableList.of(robotCommentInput));
    reviewInput.message = "robot comment test";
    gApi.changes().id(changeId).current().review(reviewInput);

    List<RobotCommentInfo> robotCommentInfos =
        gApi.changes().id(changeId).current().robotCommentsAsList();

    List<String> fixIds = getFixIds(robotCommentInfos);
    String fixId = Iterables.getOnlyElement(fixIds);

    RestApiCallHelper.execute(adminRestSession, FIX_ENDPOINTS, changeId, "current", fixId);
  }

  @Test
  public void revisionFileEndpoints() throws Exception {
    String changeId = createChange("Subject", FILENAME, "content").getChangeId();
    RestApiCallHelper.execute(
        adminRestSession, REVISION_FILE_ENDPOINTS, changeId, "current", FILENAME);
  }

  @Test
  public void changeMessageEndpoints() throws Exception {
    String changeId = createChange().getChangeId();

    // A change message is created on change creation.
    String changeMessageId = Iterables.getOnlyElement(gApi.changes().id(changeId).messages()).id;

    RestApiCallHelper.execute(
        adminRestSession, CHANGE_MESSAGE_ENDPOINTS, changeId, changeMessageId);
  }

  @Test
  public void changeEditCreateEndpoints() throws Exception {
    String changeId = createChange("Subject", FILENAME, "content").getChangeId();

    // Each of the REST calls creates the change edit newly.
    RestApiCallHelper.execute(
        adminRestSession,
        CHANGE_EDIT_CREATE_ENDPOINTS,
        () -> adminRestSession.delete("/changes/" + changeId + "/edit"),
        changeId,
        FILENAME);
  }

  @Test
  public void changeEditEndpoints() throws Exception {
    String changeId = createChange("Subject", FILENAME, "content").getChangeId();
    gApi.changes().id(changeId).edit().create();
    RestApiCallHelper.execute(adminRestSession, CHANGE_EDIT_ENDPOINTS, changeId, FILENAME);
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
