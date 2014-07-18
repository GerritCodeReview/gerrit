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
import static com.google.gerrit.server.notedb.CommentsInNotesUtil.getCommentPsId;

import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
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
import java.util.Date;
import java.util.List;
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
    ChangeDraftUpdate create(ChangeControl ctl);
    ChangeDraftUpdate create(ChangeControl ctl, Date when);
  }

  private final AllUsersName draftsProject;
  private final Account.Id accountId;
  private final CommentsInNotesUtil commentsUtil;
  private final DraftCommentNotes draftNotes;

  private List<PatchLineComment> upsertComments;
  private List<PatchLineComment> deleteComments;

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      MetaDataUpdate.User updateFactory,
      IdentifiedUser user,
      @Assisted ChangeControl ctl,
      DraftCommentNotes.Factory draftNotesFactory,
      AllUsersNameProvider allUsers,
      CommentsInNotesUtil commentsUtil) throws OrmException {
    this(serverIdent, repoManager, migration, updateFactory, user, ctl,
        serverIdent.getWhen(), draftNotesFactory, allUsers, commentsUtil);
  }

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      MetaDataUpdate.User updateFactory,
      IdentifiedUser user,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      DraftCommentNotes.Factory draftNotesFactory,
      AllUsersNameProvider allUsers,
      CommentsInNotesUtil commentsUtil) throws OrmException {
    super(migration, repoManager, updateFactory, ctl, serverIdent, when);
    this.draftsProject = allUsers.get();
    this.commentsUtil = commentsUtil;
    this.accountId = user.getAccountId();
    this.draftNotes = draftNotesFactory.create(ctl.getChange().getId(),
        user.getAccountId()).load();

    this.upsertComments = Lists.newArrayList();
    this.deleteComments = Lists.newArrayList();
  }

  public void upsertComment(PatchLineComment c) {
    verifyComment(c);
    checkArgument(c.getStatus() == Status.DRAFT,
        "Cannot upsert a published comment into a ChangeDraftUpdate");
    upsertComments.add(c);
  }

  public void deleteComment(PatchLineComment c) {
    verifyComment(c);
    checkArgument(draftNotes.containsComment(c), "Cannot delete this comment "
        + "because it didn't previously exist as a draft");
    deleteComments.add(c);
  }

  /**
   * Deletes a PatchLineComment from the list of drafts only if it existed
   * previously as a draft. If it wasn't a draft previously, this is a no-op.
   */
  public void deleteCommentIfPresent(PatchLineComment c) {
    if (draftNotes.containsComment(c)) {
      verifyComment(c);
      deleteComments.add(c);
    }
  }

  private void verifyComment(PatchLineComment comment) {
    checkArgument(psId != null,
        "setPatchSetId must be called before putComment");
    checkArgument(getCommentPsId(comment).equals(psId),
        "Comment on %s doesn't match previous patch set %s",
        getCommentPsId(comment), psId);
    checkArgument(comment.getRevId() != null);
    checkArgument(comment.getAuthor().equals(accountId),
        "The author for the following comment does not match the author of "
        + "this ChangeDraftUpdate (%s): %s", accountId, comment);
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeCommentsInNotes(AtomicBoolean removedAllComments)
      throws OrmException, IOException {
    NoteMap noteMap = draftNotes.getNoteMap();
    if (noteMap == null) {
      noteMap = NoteMap.newEmptyMap();
    }
    if (isEmpty()) {
      return null;
    }

    Table<PatchSet.Id, String, PatchLineComment> draftBaseCommentTable =
        draftNotes.getDraftBaseComments();
    Table<PatchSet.Id, String, PatchLineComment> draftPsCommentTable =
        draftNotes.getDraftPsComments();

    // There is no need to rewrite the note for one of the sides of the patch
    // set if all of the modifications were made to the comments of one side,
    // so we set these flags to potentially save that extra work.
    boolean baseSideChanged = false;
    boolean revisionSideChanged = false;

    for (PatchLineComment c : upsertComments) {
      if (c.getSide() == (short) 0) {
        baseSideChanged = true;
        draftBaseCommentTable.put(psId, c.getKey().get(), c);
      } else {
        revisionSideChanged = true;
        draftPsCommentTable.put(psId, c.getKey().get(), c);
      }
    }

    // We must define these RevIds so that if this update deletes all
    // remaining comments on a given side, then we can remove that note.
    // However, if this update doesn't delete any comments, it is okay for these
    // to be null because they won't be used.
    RevId baseRevId = null;
    RevId psRevId = null;

    for (PatchLineComment c : deleteComments) {
      if (c.getSide() == (short) 0) {
        baseSideChanged = true;
        baseRevId = c.getRevId();
        deleteCommentFromTable(draftBaseCommentTable, c);
      } else {
        revisionSideChanged = true;
        psRevId = c.getRevId();
        deleteCommentFromTable(draftPsCommentTable, c);
      }
    }

    List<PatchLineComment> updatedDraftBaseComments =
        Lists.newArrayList(draftBaseCommentTable.row(psId).values());
    List<PatchLineComment> updatedDraftPsComments =
        Lists.newArrayList(draftPsCommentTable.row(psId).values());

    updateNoteMap(baseSideChanged, noteMap, updatedDraftBaseComments,
        baseRevId);
    updateNoteMap(revisionSideChanged, noteMap, updatedDraftPsComments,
        psRevId);

    removedAllComments.set(updatedDraftBaseComments.isEmpty() &&
        updatedDraftPsComments.isEmpty());

    return noteMap.writeTree(inserter);
  }

  private void deleteCommentFromTable(
      Table<PatchSet.Id, String, PatchLineComment> t, PatchLineComment c) {
    PatchLineComment old = t.remove(psId, c.getKey().get());
    checkArgument(old != null, "Cannot delete following draft because one "
        + "did not previously exist with the same UUID: %s", c);
  }

  private void updateNoteMap(boolean changed, NoteMap noteMap,
      List<PatchLineComment> comments, RevId commitId)
      throws IOException, OrmException {
    if (changed) {
      if (comments.isEmpty()) {
        commentsUtil.removeNote(noteMap, commitId);
      } else {
        commentsUtil.writeCommentsToNoteMap(noteMap, comments, inserter);
      }
    }
  }

  public RevCommit commit() throws IOException {
    BatchMetaDataUpdate batch = openUpdate();
    try {
      CommitBuilder builder = new CommitBuilder();
      if (migration.write()) {
        AtomicBoolean removedAllComments = new AtomicBoolean();
        ObjectId treeId = storeCommentsInNotes(removedAllComments);
        if (treeId != null) {
          if (removedAllComments.get()) {
            batch.removeRef(getRefName());
          } else {
            builder.setTreeId(treeId);
            batch.write(builder);
          }
        }
      }
      RevCommit c = batch.commit();
      return c;
    } catch (OrmException e) {
      throw new IOException(e);
    } finally {
      batch.close();
    }
  }

  @Override
  protected Project.NameKey getProjectName() {
    return draftsProject;
  }

  @Override
  protected String getRefName() {
    return RefNames.refsDraftComments(accountId, getChange().getId());
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (isEmpty()) {
      return false;
    }
    commit.setAuthor(newIdent(getUser().getAccount(), when));
    commit.setCommitter(new PersonIdent(serverIdent, when));

    StringBuilder msg = new StringBuilder();
    msg.append("Comment on patch set ").append(psId.get());
    commit.setMessage(msg.toString());
    return true;
  }

  private boolean isEmpty() {
    return deleteComments.isEmpty()
        && upsertComments.isEmpty();
  }
}
