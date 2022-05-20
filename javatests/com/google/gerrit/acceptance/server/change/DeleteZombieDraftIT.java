// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class DeleteZombieDraftIT extends AbstractDaemonTest {

  @Inject private DeleteZombieCommentsRefs.Factory deleteZombieDraftsFactory;

  @Test
  public void draftRefWithOneZombie() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();

    addDraft(changeId, revId, "comment 1");
    Ref draftRef = getOnlyDraftRef();
    publishAllDrafts(r);
    assertNumDrafts(changeId, 0);
    assertNumPublishedComments(changeId, 1);

    restoreRef(draftRef.getName(), draftRef.getObjectId());

    DeleteZombieCommentsRefs worker =
        deleteZombieDraftsFactory.create(/* cleanupPercentage= */ 100);
    assertThat(worker.deleteDraftCommentsThatAreAlsoPublished()).isEqualTo(1);
  }

  @Test
  public void draftRefWithOneDraftAndOneZombie() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    PushOneCommit.Result r2 = amendChange(changeId);

    addDraft(changeId, r1.getCommit().getName(), "comment 1");
    CommentInfo c2 = addDraft(changeId, r2.getCommit().getName(), "comment 2");
    Ref draftRef = getOnlyDraftRef();

    publishDraft(r2, c2.id);
    assertNumDrafts(changeId, 1);
    assertNumPublishedComments(changeId, 1);

    restoreRef(draftRef.getName(), draftRef.getObjectId());

    DeleteZombieCommentsRefs worker =
        deleteZombieDraftsFactory.create(/* cleanupPercentage= */ 100);
    assertThat(worker.deleteDraftCommentsThatAreAlsoPublished()).isEqualTo(1);

    // Re-run the worker: the zombie draft should've been fixed
    assertThat(worker.deleteDraftCommentsThatAreAlsoPublished()).isEqualTo(0);
    assertNumDrafts(changeId, 1);
    assertNumPublishedComments(changeId, 1);
  }

  private Ref getOnlyDraftRef() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      return allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS).get(0);
    }
  }

  private void publishAllDrafts(PushOneCommit.Result r) throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "foo";
    revision(r).review(reviewInput);
  }

  private void publishDraft(PushOneCommit.Result r, String draftId) throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "foo";
    reviewInput.draftIdsToPublish = ImmutableList.of(draftId);
    revision(r).review(reviewInput);
  }

  private List<CommentInfo> getDraftComments(String changeId) throws Exception {
    return gApi.changes().id(changeId).draftsRequest().getAsList();
  }

  private List<CommentInfo> getPublishedComments(String changeId) throws Exception {
    return gApi.changes().id(changeId).commentsRequest().getAsList();
  }

  private CommentInfo addDraft(String changeId, String revId, String commentText) throws Exception {
    DraftInput comment = CommentsUtil.newDraft("f1.txt", Side.REVISION, /* line= */ 1, commentText);
    return gApi.changes().id(changeId).revision(revId).createDraft(comment).get();
  }

  private void restoreRef(String refName, ObjectId id) throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      RefUpdate u = allUsersRepo.updateRef(refName);
      u.setNewObjectId(id);
      u.forceUpdate();
    }
  }

  private void assertNumDrafts(String changeId, int num) throws Exception {
    assertThat(getDraftComments(changeId)).hasSize(num);
  }

  private void assertNumPublishedComments(String changeId, int num) throws Exception {
    assertThat(getPublishedComments(changeId)).hasSize(num);
  }
}
