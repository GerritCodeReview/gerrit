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
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class DeleteZombieDraftIT extends AbstractDaemonTest {

  @Inject private DeleteZombieCommentsRefs.Factory deleteZombieDraftsFactory;

  @Test
  public void detectZombieDrafts() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();

    DraftInput comment = CommentsUtil.newDraft("f1.txt", Side.REVISION, /* line= */ 1, "comment 1");
    addDraft(changeId, revId, comment);
    Ref draftRef = getOnlyDraftRef();
    publishComments(r);
    Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
    List<CommentInfo> publishedComments = getPublishedCommentsAsList(changeId);
    assertThat(drafts).isEmpty();
    assertThat(publishedComments).hasSize(1);

    // Restore the draft ref, resulting in the comment existing twice as {draft, published}.
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      RefUpdate u = allUsersRepo.updateRef(draftRef.getName());
      u.setNewObjectId(draftRef.getObjectId());
      u.forceUpdate();
    }

    DeleteZombieCommentsRefs worker =
        deleteZombieDraftsFactory.create(/* cleanupPercentage= */ 100);
    assertThat(worker.getNumberOfDraftsThatAreAlsoPublished()).isEqualTo(1);
  }

  private Ref getOnlyDraftRef() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      return allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS).get(0);
    }
  }

  private void publishComments(PushOneCommit.Result r) throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "foo";
    revision(r).review(reviewInput);
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId, String revId)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).drafts();
  }

  private List<CommentInfo> getPublishedCommentsAsList(String changeId) throws Exception {
    return gApi.changes().id(changeId).commentsRequest().getAsList();
  }

  private CommentInfo addDraft(String changeId, String revId, DraftInput in) throws Exception {
    return gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }
}
