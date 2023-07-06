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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ChangeDraftUpdate;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class ChangeDraftNotesUpdate extends AbstractChangeUpdate implements ChangeDraftUpdate {
  public interface Factory extends ChangeDraftUpdateFactory {
    @Override
    ChangeDraftNotesUpdate create(
        ChangeNotes notes,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Instant when);

    @Override
    ChangeDraftNotesUpdate create(
        Change change,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Instant when);
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
    return new AutoValue_ChangeDraftNotesUpdate_Key(c.getCommitId(), c.key);
  }

  private final AllUsersName draftsProject;

  private List<HumanComment> put = new ArrayList<>();
  private Map<Key, DeleteReason> delete = new HashMap<>();

  @SuppressWarnings("UnusedMethod")
  @AssistedInject
  private ChangeDraftNotesUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted ChangeNotes notes,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Instant when) {
    super(noteUtil, serverIdent, notes, null, accountId, realAccountId, authorIdent, when);
    this.draftsProject = allUsers;
  }

  @AssistedInject
  private ChangeDraftNotesUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Instant when) {
    super(noteUtil, serverIdent, null, change, accountId, realAccountId, authorIdent, when);
    this.draftsProject = allUsers;
  }

  @Override
  public void putDraftComment(HumanComment c) {
    checkState(!put.contains(c), "comment already added");
    verifyComment(c);
    put.add(c);
  }

  @Override
  public void markDraftCommentAsPublished(HumanComment c) {
    checkState(!delete.containsKey(key(c)), "comment already marked for deletion");
    verifyComment(c);
    delete.put(key(c), DeleteReason.PUBLISHED);
  }

  @Override
  public void deleteComment(HumanComment c) {
    checkState(!delete.containsKey(key(c)), "comment already marked for deletion");
    verifyComment(c);
    delete.put(key(c), DeleteReason.DELETED);
  }

  @Override
  public void deleteAllComments(List<Comment> comments) {
    comments.forEach(
        comment -> {
          Key commentKey = key(comment);
          checkState(!delete.containsKey(commentKey), "comment already marked for deletion");
          delete.put(commentKey, DeleteReason.FIXED);
        });
  }

  public boolean canRunAsync() {
    return put.isEmpty()
        && delete.values().stream()
            .allMatch(r -> r == DeleteReason.PUBLISHED || r == DeleteReason.FIXED);
  }

  /**
   * Returns a copy of the current {@link ChangeDraftNotesUpdate} that contains references to all
   * deletions. Copying of {@link ChangeDraftNotesUpdate} is only allowed if it contains no new
   * comments.
   */
  ChangeDraftNotesUpdate copy() {
    checkState(
        put.isEmpty(),
        "copying ChangeDraftNotesUpdate is allowed only if it doesn't contain new comments");
    ChangeDraftNotesUpdate clonedUpdate =
        new ChangeDraftNotesUpdate(
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

  @Nullable
  private CommitBuilder storeCommentsInNotes(
      RevWalk rw, ObjectInserter ins, ObjectId curr, CommitBuilder cb)
      throws ConfigInvalidException, IOException {
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (HumanComment c : put) {
      if (!delete.keySet().contains(key(c))) {
        cache.get(c.getCommitId()).putComment(c);
      }
    }
    for (Key k : delete.keySet()) {
      cache.get(k.commitId()).deleteComment(k.key());
    }

    // keyed by commit ID.
    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    boolean touchedAnyRevs = false;
    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId id = e.getKey();
      byte[] data = e.getValue().build(noteUtil.getChangeNoteJson());
      if (!Arrays.equals(data, e.getValue().baseRaw)) {
        touchedAnyRevs = true;
      }
      if (data.length == 0) {
        rnm.noteMap.remove(id);
      } else {
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

    // If there are no comments left, tell the
    // caller to delete the entire ref.
    if (!rnm.noteMap.iterator().hasNext()) {
      return null;
    }

    ObjectId treeId = rnm.noteMap.writeTree(ins);
    cb.setTreeId(treeId);
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
    // parse any existing revision notes, so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap, HumanComment.Status.DRAFT);
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
  protected void setParentCommit(CommitBuilder cb, ObjectId parentCommitId) {
    cb.setParentIds(); // Draft updates should not keep history of parent commits
  }

  @Override
  public boolean isEmpty() {
    return delete.isEmpty() && put.isEmpty();
  }

  public static Optional<ChangeDraftNotesUpdate> asChangeDraftNotesUpdate(
      @Nullable ChangeDraftUpdate obj) {
    if (obj == null) {
      return Optional.empty();
    }
    if (obj instanceof ChangeDraftNotesUpdate) {
      return Optional.of((ChangeDraftNotesUpdate) obj);
    }
    return Optional.empty();
  }
}
