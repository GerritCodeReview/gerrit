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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.acceptance.GitUtil.initSsh;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class QueryIT extends AbstractDaemonTest {

  private static Gson gson = new Gson();

  @Test
  public void testBasicQueryJSON() throws Exception {
    String changeId1 = createChange().getChangeId();
    String changeId2 = createChange().getChangeId();

    List<ChangeAttribute> changes = executeSuccessfulQuery("1234");
    assertThat(changes.size()).isEqualTo(0);

    changes = executeSuccessfulQuery(changeId1);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId1);

    changes = executeSuccessfulQuery(changeId1 + " OR " + changeId2);
    assertThat(changes.size()).isEqualTo(2);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId2);
    assertThat(changes.get(1).project).isEqualTo(project.toString());
    assertThat(changes.get(1).id).isEqualTo(changeId1);

    changes = executeSuccessfulQuery("--start=1 " + changeId1 + " OR " + changeId2);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).project).isEqualTo(project.toString());
    assertThat(changes.get(0).id).isEqualTo(changeId1);
  }

  @Test
  public void testAllApprovalsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).isNull();

    changes = executeSuccessfulQuery("--all-approvals " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals.size()).isEqualTo(1);
  }

  @Test
  public void testAllReviewersOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(changeId).addReviewer(in);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).allReviewers).isNull();

    changes = executeSuccessfulQuery("--all-reviewers " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).allReviewers).isNotNull();
    assertThat(changes.get(0).allReviewers.size()).isEqualTo(1);
  }

  @Test
  public void testCommitMessageOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery("--commit-message " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).commitMessage).isNotNull();
    assertThat(changes.get(0).commitMessage).contains(PushOneCommit.SUBJECT);
  }

  @Test
  public void testCurrentPatchSetOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    amendChange(changeId);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet).isNull();

    changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet).isNotNull();
    assertThat(changes.get(0).currentPatchSet.number).isEqualTo("2");

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet).isNotNull();
    assertThat(changes.get(0).currentPatchSet.approvals).isNotNull();
    assertThat(changes.get(0).currentPatchSet.approvals.size()).isEqualTo(1);
  }

  @Test
  public void testPatchSetsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    amendChange(changeId);
    amendChange(changeId);

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).isNull();

    changes = executeSuccessfulQuery("--patch-sets " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).isNotNull();
    assertThat(changes.get(0).patchSets.size()).isEqualTo(3);
  }

  @Test
  public void shouldFailWithFilesWithoutPatchSetsOrCurrentPatchSetsOption() throws Exception {
    String changeId = createChange().getChangeId();
    adminSshSession.exec("gerrit query --files " + changeId);
    assertThat(adminSshSession.hasError()).isTrue();
    assertThat(adminSshSession.getError()).contains("needs --patch-sets or --current-patch-set");
  }

  @Test
  public void testFileOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();

    List<ChangeAttribute> changes =
        executeSuccessfulQuery("--current-patch-set --files " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet.files).isNotNull();
    assertThat(changes.get(0).currentPatchSet.files.size()).isEqualTo(2);

    changes = executeSuccessfulQuery("--patch-sets --files " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files.size()).isEqualTo(2);

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--patch-sets --files --all-approvals " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files.size()).isEqualTo(2);
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals.size()).isEqualTo(1);
  }

  @Test
  public void testCommentOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();

    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).comments).isNull();

    changes = executeSuccessfulQuery("--comments " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).comments).isNotNull();
    assertThat(changes.get(0).comments.size()).isEqualTo(1);
  }

  @Test
  public void testCommentOptionsInCurrentPatchSetJSON() throws Exception {
    String changeId = createChange().getChangeId();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = new HashMap<>();
    review.comments.put(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(review);

    List<ChangeAttribute> changes = executeSuccessfulQuery("--current-patch-set " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet.comments).isNull();

    changes = executeSuccessfulQuery("--current-patch-set --comments " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).currentPatchSet.comments).isNotNull();
    assertThat(changes.get(0).currentPatchSet.comments.size()).isEqualTo(1);
  }

  @Test
  public void testCommentOptionInPatchSetsJSON() throws Exception {
    String changeId = createChange().getChangeId();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = new HashMap<>();
    review.comments.put(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(review);

    List<ChangeAttribute> changes = executeSuccessfulQuery("--patch-sets " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNull();

    changes = executeSuccessfulQuery("--patch-sets --comments " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments.size()).isEqualTo(1);

    changes = executeSuccessfulQuery("--patch-sets --comments --files " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files.size()).isEqualTo(2);

    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    changes = executeSuccessfulQuery("--patch-sets --comments --files --all-approvals " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).comments).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).comments.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets.get(0).files).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).files.size()).isEqualTo(2);
    assertThat(changes.get(0).patchSets.get(0).approvals).isNotNull();
    assertThat(changes.get(0).patchSets.get(0).approvals.size()).isEqualTo(1);
  }

  @Test
  public void testDependenciesOptionJSON() throws Exception {
    String changeId1 = createChange().getChangeId();
    String changeId2 = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId1);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery("--dependencies " + changeId1);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery(changeId2);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).dependsOn).isNull();

    changes = executeSuccessfulQuery("--dependencies " + changeId2);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).dependsOn).isNotNull();
    assertThat(changes.get(0).dependsOn.size()).isEqualTo(1);
  }

  @Test
  public void testSubmitRecordsOptionJSON() throws Exception {
    String changeId = createChange().getChangeId();
    List<ChangeAttribute> changes = executeSuccessfulQuery(changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).submitRecords).isNull();

    changes = executeSuccessfulQuery("--submit-records " + changeId);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).submitRecords).isNotNull();
    assertThat(changes.get(0).submitRecords.size()).isEqualTo(1);
  }

  @Test
  public void testQueryWithNonVisibleCurrentPatchSet() throws Exception {
    String changeId = createChange().getChangeId();
    amendChangeAsDraft(changeId);
    String query = "--current-patch-set --patch-sets " + changeId;
    List<ChangeAttribute> changes = executeSuccessfulQuery(query);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).isNotNull();
    assertThat(changes.get(0).patchSets).hasSize(2);
    assertThat(changes.get(0).currentPatchSet).isNotNull();

    SshSession userSession = new SshSession(server, user);
    initSsh(user);
    userSession.open();
    changes = executeSuccessfulQuery(query, userSession);
    assertThat(changes.size()).isEqualTo(1);
    assertThat(changes.get(0).patchSets).hasSize(1);
    assertThat(changes.get(0).currentPatchSet).isNull();
    userSession.close();
  }

  private List<ChangeAttribute> executeSuccessfulQuery(String params, SshSession session)
      throws Exception {
    String rawResponse = session.exec("gerrit query --format=JSON " + params);
    assert_().withFailureMessage(session.getError()).that(session.hasError()).isFalse();
    return getChanges(rawResponse);
  }

  private List<ChangeAttribute> executeSuccessfulQuery(String params) throws Exception {
    return executeSuccessfulQuery(params, adminSshSession);
  }

  private static List<ChangeAttribute> getChanges(String rawResponse) {
    String[] lines = rawResponse.split("\\n");
    List<ChangeAttribute> changes = new ArrayList<>(lines.length - 1);
    for (int i = 0; i < lines.length - 1; i++) {
      changes.add(gson.fromJson(lines[i], ChangeAttribute.class));
    }
    return changes;
  }
}
