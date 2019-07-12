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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Sets;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A single delta to apply atomically to a change.
 *
 * <p>This delta contains only draft comments on a single patch set of a change by a single author.
 * This delta will become a single commit in the All-Users repository.
 *
 * <p>This class is not thread safe.
 */
public class ChangeDraftUpdate extends AbstractChangeUpdate {
  public interface Factory {
    ChangeDraftUpdate create(
        ChangeNotes notes,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Date when);

    ChangeDraftUpdate create(
        Change change,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Date when);
  }

  @AutoValue
  abstract static class Key {
    abstract ObjectId commitId();

    abstract Comment.Key key();
  }

  enum DeleteReason {
    DELETED,
    PUBLISHED,
    FIXED
  }

  private static Key key(Comment c) {
    return new AutoValue_ChangeDraftUpdate_Key(c.getCommitId(), c.key);
  }

  private final AllUsersName draftsProject;

  private List<Comment> put = new ArrayList<>();
  private Map<Key, DeleteReason> delete = new HashMap<>();

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted ChangeNotes notes,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(noteUtil, serverIdent, notes, null, accountId, realAccountId, authorIdent, when);
    this.draftsProject = allUsers;
  }

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(noteUtil, serverIdent, null, change, accountId, realAccountId, authorIdent, when);
    this.draftsProject = allUsers;
  }

  public void putComment(Comment c) {
    checkState(!put.contains(c), "comment already added");
    verifyComment(c);
    put.add(c);
  }

  /**
   * Marks a comment for deletion. Called when the comment is deleted because the user published it.
   */
  public void markCommentPublished(Comment c) {
    checkState(!delete.containsKey(key(c)), "comment already marked for deletion");
    verifyComment(c);
    delete.put(key(c), DeleteReason.PUBLISHED);
  }

  /**
   * Marks a comment for deletion. Called when the comment is deleted because the user removed it.
   */
  public void deleteComment(Comment c) {
    checkState(!delete.containsKey(key(c)), "comment already marked for deletion");
    verifyComment(c);
    delete.put(key(c), DeleteReason.DELETED);
  }

  /**
   * Marks a comment for deletion. Called when the comment should have been deleted previously, but
   * wasn't, so we're fixing it up.
   */
  public void deleteComment(ObjectId commitId, Comment.Key key) {
    Key commentKey = new AutoValue_ChangeDraftUpdate_Key(commitId, key);
    checkState(!delete.containsKey(commentKey), "comment already marked for deletion");
    delete.put(commentKey, DeleteReason.FIXED);
  }

  /**
   * Returns true if all we do in this operations is deletes caused by publishing or fixing up
   * comments.
   */
  public boolean canRunAsync() {
    return put.isEmpty()
        && delete
            .values()
            .stream()
            .allMatch(r -> r == DeleteReason.PUBLISHED || r == DeleteReason.FIXED);
  }

  /**
   * Returns a copy of the current {@link ChangeDraftUpdate} that contains references to all
   * deletions. Copying of {@link ChangeDraftUpdate} is only allowed if it contains no new comments.
   */
  ChangeDraftUpdate copy() {
    checkState(
        put.isEmpty(),
        "copying ChangeDraftUpdate is allowed only if it doesn't contain new comments");
    ChangeDraftUpdate clonedUpdate =
        new ChangeDraftUpdate(
            authorIdent,
            draftsProject,
            noteUtil,
            new Change(getChange()),
            accountId,
            realAccountId,
            authorIdent,
            when);
    clonedUpdate.delete.putAll(delete);
    return clonedUpdate;
  }

  private CommitBuilder storeCommentsInNotes(
      RevWalk rw, ObjectInserter ins, ObjectId curr, CommitBuilder cb)
      throws ConfigInvalidException, IOException {
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);
    Set<ObjectId> updatedCommits = Sets.newHashSetWithExpectedSize(rnm.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (Comment c : put) {
      if (!delete.keySet().contains(key(c))) {
        cache.get(c.getCommitId()).putComment(c);
      }
    }
    for (Key k : delete.keySet()) {
      cache.get(k.commitId()).deleteComment(k.key());
    }

    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    boolean touchedAnyRevs = false;
    boolean hasComments = false;
    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedCommits.add(e.getKey());
      ObjectId id = e.getKey();
      byte[] data = e.getValue().build(noteUtil.getChangeNoteJson());
      if (!Arrays.equals(data, e.getValue().baseRaw)) {
        touchedAnyRevs = true;
      }
      if (data.length == 0) {
        rnm.noteMap.remove(id);
      } else {
        hasComments = true;
        ObjectId dataBlob = ins.insert(OBJ_BLOB, data);
        rnm.noteMap.set(id, dataBlob);
      }
    }

    // If we didn't touch any notes, tell the caller this was a no-op update. We
    // couldn't have done this in isEmpty() below because we hadn't read the old
    // data yet.
    if (!touchedAnyRevs) {
      return NO_OP_UPDATE;
    }

    // If we touched every revision and there are no comments left, tell the
    // caller to delete the entire ref.
    boolean touchedAllRevs = updatedCommits.equals(rnm.revisionNotes.keySet());
    if (touchedAllRevs && !hasComments) {
      return null;
    }

    cb.setTreeId(rnm.noteMap.writeTree(ins));
    return cb;
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    // The old DraftCommentNotes already parsed the revision notes. We can reuse them as long as
    // the ref hasn't advanced.
    ChangeNotes changeNotes = getNotes();
    if (changeNotes != null) {
      DraftCommentNotes draftNotes = changeNotes.load().getDraftCommentNotes();
      if (draftNotes != null) {
        ObjectId idFromNotes = firstNonNull(draftNotes.getRevision(), ObjectId.zeroId());
        RevisionNoteMap<ChangeRevisionNote> rnm = draftNotes.getRevisionNoteMap();
        if (idFromNotes.equals(curr) && rnm != null) {
          return rnm;
        }
      }
    }
    NoteMap noteMap;
    if (!curr.equals(ObjectId.zeroId())) {
      noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    } else {
      noteMap = NoteMap.newEmptyMap();
    }
    // Even though reading from changes might not be enabled, we need to
    // parse any existing revision notes so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil.getChangeNoteJson(),
        noteUtil.getLegacyChangeNoteRead(),
        getId(),
        rw.getObjectReader(),
        noteMap,
        PatchLineComment.Status.DRAFT);
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setMessage("Update draft comments");
    try {
      return storeCommentsInNotes(rw, ins, curr, cb);
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
  }

  @Override
  protected Project.NameKey getProjectName() {
    return draftsProject;
  }

  @Override
  protected String getRefName() {
    return RefNames.refsDraftComments(getId(), accountId);
  }

  @Override
  public boolean isEmpty() {
    return delete.isEmpty() && put.isEmpty();
  }
}
