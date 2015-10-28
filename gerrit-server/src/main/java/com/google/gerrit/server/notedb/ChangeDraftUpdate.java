// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.CommentsInNotesUtil.addCommentToMap;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single delta to apply atomically to a change.
 * <p>
 * This delta contains only draft comments on a single patch set of a change by
 * a single author. This delta will become a single commit in the All-Users
 * repository.
 * <p>
 * This class is not thread safe.
 */
public class ChangeDraftUpdate extends AbstractChangeUpdate {
  public interface Factory {
    ChangeDraftUpdate create(ChangeControl ctl, Date when);
  }

  private final AllUsersName draftsProject;
  private final Account.Id accountId;
  private final CommentsInNotesUtil commentsUtil;
  private final ChangeNotes changeNotes;
  private final DraftCommentNotes draftNotes;

  private List<PatchLineComment> upsertComments;
  private List<PatchLineComment> deleteComments;

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      MetaDataUpdate.User updateFactory,
      DraftCommentNotes.Factory draftNotesFactory,
      AllUsersName allUsers,
      CommentsInNotesUtil commentsUtil,
      @Assisted ChangeControl ctl,
      @Assisted Date when) throws OrmException {
    super(migration, repoManager, updateFactory, ctl, serverIdent,
        anonymousCowardName, when);
    this.draftsProject = allUsers;
    this.commentsUtil = commentsUtil;
    checkState(ctl.getUser().isIdentifiedUser(),
        "Current user must be identified");
    IdentifiedUser user = ctl.getUser().asIdentifiedUser();
    this.accountId = user.getAccountId();
    this.changeNotes = getChangeNotes().load();
    this.draftNotes = draftNotesFactory.create(ctl.getId(),
        user.getAccountId());

    this.upsertComments = Lists.newArrayList();
    this.deleteComments = Lists.newArrayList();
  }

  public void insertComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    checkArgument(c.getStatus() == Status.DRAFT,
        "Cannot insert a published comment into a ChangeDraftUpdate");
    if (migration.readChanges()) {
      checkArgument(!changeNotes.containsComment(c),
          "A comment already exists with the same key,"
          + " so the following comment cannot be inserted: %s", c);
    }
    upsertComments.add(c);
  }

  public void upsertComment(PatchLineComment c) {
    verifyComment(c);
    checkArgument(c.getStatus() == Status.DRAFT,
        "Cannot upsert a published comment into a ChangeDraftUpdate");
    upsertComments.add(c);
  }

  public void updateComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    checkArgument(c.getStatus() == Status.DRAFT,
        "Cannot update a published comment into a ChangeDraftUpdate");
    // Here, we check to see if this comment existed previously as a draft.
    // However, this could cause a race condition if there is a delete and an
    // update operation happening concurrently (or two deletes) and they both
    // believe that the comment exists. If a delete happens first, then
    // the update will fail. However, this is an acceptable risk since the
    // caller wanted the comment deleted anyways, so the end result will be the
    // same either way.
    if (migration.readChanges()) {
      checkArgument(draftNotes.load().containsComment(c),
          "Cannot update this comment because it didn't exist previously");
    }
    upsertComments.add(c);
  }

  public void deleteComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    // See the comment above about potential race condition.
    if (migration.readChanges()) {
      checkArgument(draftNotes.load().containsComment(c),
          "Cannot delete this comment because it didn't previously exist as a"
          + " draft");
    }
    if (migration.writeChanges()) {
      if (draftNotes.load().containsComment(c)) {
        deleteComments.add(c);
      }
    }
  }

  /**
   * Deletes a PatchLineComment from the list of drafts only if it existed
   * previously as a draft. If it wasn't a draft previously, this is a no-op.
   */
  public void deleteCommentIfPresent(PatchLineComment c) throws OrmException {
    if (draftNotes.load().containsComment(c)) {
      verifyComment(c);
      deleteComments.add(c);
    }
  }

  private void verifyComment(PatchLineComment comment) {
    if (migration.writeChanges()) {
      checkArgument(comment.getRevId() != null);
    }
    checkArgument(comment.getAuthor().equals(accountId),
        "The author for the following comment does not match the author of"
        + " this ChangeDraftUpdate (%s): %s", accountId, comment);
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeCommentsInNotes(AtomicBoolean removedAllComments)
      throws OrmException, IOException {
    if (isEmpty()) {
      return null;
    }

    NoteMap noteMap = draftNotes.load().getNoteMap();
    if (noteMap == null) {
      noteMap = NoteMap.newEmptyMap();
    }

    Map<RevId, List<PatchLineComment>> allComments = new HashMap<>();

    boolean hasComments = false;
    int n = deleteComments.size() + upsertComments.size();
    Set<RevId> updatedRevs = Sets.newHashSetWithExpectedSize(n);
    Set<PatchLineComment.Key> updatedKeys = Sets.newHashSetWithExpectedSize(n);
    for (PatchLineComment c : deleteComments) {
      allComments.put(c.getRevId(), new ArrayList<PatchLineComment>());
      updatedRevs.add(c.getRevId());
      updatedKeys.add(c.getKey());
    }

    for (PatchLineComment c : upsertComments) {
      hasComments = true;
      addCommentToMap(allComments, c);
      updatedRevs.add(c.getRevId());
      updatedKeys.add(c.getKey());
    }

    // Re-add old comments for updated revisions so the new note contents
    // includes both old and new comments merged in the right order.
    //
    // writeCommentsToNoteMap doesn't touch notes for SHA-1s that are not
    // mentioned in the input map, so by omitting comments for those revisions,
    // we avoid the work of having to re-serialize identical comment data for
    // those revisions.
    ListMultimap<RevId, PatchLineComment> existing =
        draftNotes.getComments();
    for (Map.Entry<RevId, PatchLineComment> e : existing.entries()) {
      PatchLineComment c = e.getValue();
      if (updatedRevs.contains(c.getRevId())
          && !updatedKeys.contains(c.getKey())) {
        hasComments = true;
        addCommentToMap(allComments, e.getValue());
      }
    }

    // If we touched every revision and there are no comments left, set the flag
    // for the caller to delete the entire ref.
    boolean touchedAllRevs = updatedRevs.equals(existing.keySet());
    if (touchedAllRevs && !hasComments) {
      removedAllComments.set(touchedAllRevs && !hasComments);
      return null;
    }

    commentsUtil.writeCommentsToNoteMap(noteMap, allComments, inserter);
    return noteMap.writeTree(inserter);
  }

  public RevCommit commit() throws IOException {
    BatchMetaDataUpdate batch = openUpdate();
    try {
      writeCommit(batch);
      return batch.commit();
    } catch (OrmException e) {
      throw new IOException(e);
    } finally {
      batch.close();
    }
  }

  @Override
  public void writeCommit(BatchMetaDataUpdate batch)
      throws OrmException, IOException {
    CommitBuilder builder = new CommitBuilder();
    if (migration.writeChanges()) {
      AtomicBoolean removedAllComments = new AtomicBoolean();
      ObjectId treeId = storeCommentsInNotes(removedAllComments);
      if (removedAllComments.get()) {
        batch.removeRef(getRefName());
      } else if (treeId != null) {
        builder.setTreeId(treeId);
        batch.write(builder);
      }
    }
  }

  @Override
  protected Project.NameKey getProjectName() {
    return draftsProject;
  }

  @Override
  protected String getRefName() {
    return RefNames.refsDraftComments(accountId, ctl.getId());
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (isEmpty()) {
      return false;
    }
    commit.setAuthor(newIdent(getUser().getAccount(), when));
    commit.setCommitter(new PersonIdent(serverIdent, when));
    commit.setMessage("Update draft comments");
    return true;
  }

  private boolean isEmpty() {
    return deleteComments.isEmpty()
        && upsertComments.isEmpty();
  }
}
