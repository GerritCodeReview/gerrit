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
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
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
    abstract String revId();

    abstract Comment.Key key();
  }

  private static Key key(Comment c) {
    return new AutoValue_ChangeDraftUpdate_Key(c.revId, c.key);
  }

  private final AllUsersName draftsProject;

  private List<Comment> put = new ArrayList<>();
  private Set<Key> delete = new HashSet<>();

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted ChangeNotes notes,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(
        migration,
        noteUtil,
        serverIdent,
        anonymousCowardName,
        notes,
        null,
        accountId,
        realAccountId,
        authorIdent,
        when);
    this.draftsProject = allUsers;
  }

  @AssistedInject
  private ChangeDraftUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(
        migration,
        noteUtil,
        serverIdent,
        anonymousCowardName,
        null,
        change,
        accountId,
        realAccountId,
        authorIdent,
        when);
    this.draftsProject = allUsers;
  }

  public void putComment(Comment c) {
    verifyComment(c);
    put.add(c);
  }

  public void deleteComment(Comment c) {
    verifyComment(c);
    delete.add(key(c));
  }

  public void deleteComment(String revId, Comment.Key key) {
    delete.add(new AutoValue_ChangeDraftUpdate_Key(revId, key));
  }

  private CommitBuilder storeCommentsInNotes(
      RevWalk rw, ObjectInserter ins, ObjectId curr, CommitBuilder cb)
      throws ConfigInvalidException, OrmException, IOException {
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);
    Set<RevId> updatedRevs = Sets.newHashSetWithExpectedSize(rnm.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (Comment c : put) {
      if (!delete.contains(key(c))) {
        cache.get(new RevId(c.revId)).putComment(c);
      }
    }
    for (Key k : delete) {
      cache.get(new RevId(k.revId())).deleteComment(k.key());
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    boolean touchedAnyRevs = false;
    boolean hasComments = false;
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedRevs.add(e.getKey());
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, noteUtil.getWriteJson());
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
    boolean touchedAllRevs = updatedRevs.equals(rnm.revisionNotes.keySet());
    if (touchedAllRevs && !hasComments) {
      return null;
    }

    cb.setTreeId(rnm.noteMap.writeTree(ins));
    return cb;
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, OrmException, IOException {
    if (migration.readChanges()) {
      // If reading from changes is enabled, then the old DraftCommentNotes
      // already parsed the revision notes. We can reuse them as long as the ref
      // hasn't advanced.
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
        noteUtil, getId(), rw.getObjectReader(), noteMap, PatchLineComment.Status.DRAFT);
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setMessage("Update draft comments");
    try {
      return storeCommentsInNotes(rw, ins, curr, cb);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
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
