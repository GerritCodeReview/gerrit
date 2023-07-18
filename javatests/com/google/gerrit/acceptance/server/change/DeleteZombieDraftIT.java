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
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.notedb.ChangeNoteJson;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.lang3.reflect.TypeLiteral;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

/** Test for {@link com.google.gerrit.server.notedb.DeleteZombieCommentsRefs}. */
public class DeleteZombieDraftIT extends AbstractDaemonTest {
  private static final String TEST_PARAMETER_MARKER = "test_only_parameter";

  @Inject private DeleteZombieCommentsRefs.Factory deleteZombieDraftsFactory;
  @Inject private ChangeNoteJson changeNoteJson;
  private boolean dryRun;

  @ConfigSuite.Default
  public static Config dryRunMode() {
    Config config = new Config();
    config.setBoolean(TEST_PARAMETER_MARKER, null, "dryRun", true);
    return config;
  }

  @ConfigSuite.Config
  public static Config deleteMode() {
    Config config = new Config();
    config.setBoolean(TEST_PARAMETER_MARKER, null, "dryRun", false);
    return config;
  }

  @Before
  public void setUp() throws Exception {
    dryRun = baseConfig.getBoolean(TEST_PARAMETER_MARKER, "dryRun", true);
  }

  @Test
  public void draftRefWithOneZombie() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();

    // Create a draft. A draft ref is created for this draft comment.
    addDraft(changeId, revId, "comment 1");
    Ref draftRef = getOnlyDraftRef();
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), revId)).hasSize(1);
    // Publish the draft. The draft ref is deleted.
    publishAllDrafts(r);
    assertNumDrafts(changeId, 0);
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), revId)).isEmpty();
    assertNumPublishedComments(changeId, 1);

    // Restore the draft ref. Now the same comment exists as draft and published -> zombie.
    restoreRef(draftRef.getName(), draftRef.getObjectId());
    draftRef = getOnlyDraftRef();
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), revId)).hasSize(1);

    // Run the cleanup logic. The zombie draft is cleared. The published comment is untouched.
    DeleteZombieCommentsRefs worker =
        deleteZombieDraftsFactory.create(/* cleanupPercentage= */ 100, dryRun);
    worker.setup();
    assertThat(worker.listDraftCommentsThatAreAlsoPublished()).hasSize(1);
    worker.execute();
    if (dryRun) {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), revId)).hasSize(1);
    } else {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), revId)).isEmpty();
    }
    assertNumPublishedComments(changeId, 1);
  }

  @Test
  public void draftRefWithOneDraftAndOneZombie() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    PushOneCommit.Result r2 = amendChange(changeId);

    // Add two draft comments: one on PS1, the other on PS2
    addDraft(changeId, r1.getCommit().getName(), "comment 1");
    CommentInfo c2 = addDraft(changeId, r2.getCommit().getName(), "comment 2");
    Ref draftRef = getOnlyDraftRef();

    // Publish the draft on PS2. Now PS1 still has one draft, PS2 has no drafts
    publishDraft(r2, c2.id);
    assertNumDrafts(changeId, 1);
    assertNumPublishedComments(changeId, 1);
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r1.getCommit().name())).hasSize(1);
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).isEmpty();

    // Restore the draft ref for PS2 draft. Now draft on PS2 is zombie because it is also published.
    restoreRef(draftRef.getName(), draftRef.getObjectId());
    draftRef = getOnlyDraftRef();
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r1.getCommit().name())).hasSize(1);
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).hasSize(1);

    // Run the zombie cleanup logic. Zombie draft ref for PS2 will be removed.
    DeleteZombieCommentsRefs worker =
        deleteZombieDraftsFactory.create(/* cleanupPercentage= */ 100, dryRun);
    worker.setup();
    assertThat(worker.listDraftCommentsThatAreAlsoPublished()).hasSize(1);
    worker.execute();
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r1.getCommit().name())).hasSize(1);
    if (dryRun) {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).hasSize(1);
    } else {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).isEmpty();
    }
    assertNumPublishedComments(changeId, 1);

    // Re-run the worker: nothing happens.
    assertThat(worker.listDraftCommentsThatAreAlsoPublished()).hasSize(dryRun ? 1 : 0);
    worker.execute();
    assertNumDrafts(changeId, 1);
    assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r1.getCommit().name())).hasSize(1);
    if (dryRun) {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).hasSize(1);
    } else {
      assertThat(getDraftsByParsingDraftRef(draftRef.getName(), r2.getCommit().name())).isEmpty();
    }
    assertNumPublishedComments(changeId, 1);
  }

  private Ref getOnlyDraftRef() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      return Iterables.getOnlyElement(
          allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS));
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
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
        RefUpdate u = allUsersRepo.updateRef(refName);
        u.setNewObjectId(id);
        u.forceUpdate();
      }
    }
  }

  /**
   * Returns all draft comments that are stored in {@code draftRefStr} for a specific revision
   * (patchset) identified by its {@code blobFile} SHA-1.
   *
   * <p>Background: This ref points to a tree containing one or more blob files, each named after
   * the patchset revision SHA-1, that is drafts for each patchset are stored in a separate blob
   * file.
   */
  private List<HumanComment> getDraftsByParsingDraftRef(String draftRefStr, String blobFile)
      throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(allUsersRepo)) {
      Ref draftRef = allUsersRepo.exactRef(draftRefStr);
      if (draftRef == null) {
        // draft ref does not exist, i.e. no draft comments stored for this ref.
        return ImmutableList.of();
      }
      RevTree revTree = rw.parseTree(draftRef.getObjectId());
      TreeWalk tw = TreeWalk.forPath(allUsersRepo, blobFile, revTree);
      if (tw == null) {
        // blobFile does not exist, i.e. no draft comments for this revision.
        return ImmutableList.of();
      }
      ObjectLoader open = allUsersRepo.open(tw.getObjectId(0));
      String content = new String(open.getBytes(), UTF_8);
      List<HumanComment> drafts =
          changeNoteJson
              .getGson()
              .fromJson(
                  JsonParser.parseString(content)
                      .getAsJsonObject()
                      .getAsJsonArray("comments")
                      .toString(),
                  new TypeLiteral<ImmutableList<HumanComment>>() {}.getType());
      return drafts;
    }
  }

  private void assertNumDrafts(String changeId, int num) throws Exception {
    assertThat(getDraftComments(changeId)).hasSize(num);
  }

  private void assertNumPublishedComments(String changeId, int num) throws Exception {
    assertThat(getPublishedComments(changeId)).hasSize(num);
  }
}
