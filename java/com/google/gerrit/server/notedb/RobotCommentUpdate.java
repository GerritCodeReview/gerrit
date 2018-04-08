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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.reviewdb.client.RefNames.robotCommentsRef;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.Sets;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A single delta to apply atomically to a change.
 *
 * <p>This delta contains only robot comments on a single patch set of a change by a single author.
 * This delta will become a single commit in the repository.
 *
 * <p>This class is not thread safe.
 */
public class RobotCommentUpdate extends AbstractChangeUpdate {
  public interface Factory {
    RobotCommentUpdate create(
        ChangeNotes notes,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Date when);

    RobotCommentUpdate create(
        Change change,
        @Assisted("effective") Account.Id accountId,
        @Assisted("real") Account.Id realAccountId,
        PersonIdent authorIdent,
        Date when);
  }

  private List<RobotComment> put = new ArrayList<>();

  @AssistedInject
  private RobotCommentUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      ChangeNoteUtil noteUtil,
      @Assisted ChangeNotes notes,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(
        cfg,
        migration,
        noteUtil,
        serverIdent,
        notes,
        null,
        accountId,
        realAccountId,
        authorIdent,
        when);
  }

  @AssistedInject
  private RobotCommentUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted("effective") Account.Id accountId,
      @Assisted("real") Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when) {
    super(
        cfg,
        migration,
        noteUtil,
        serverIdent,
        null,
        change,
        accountId,
        realAccountId,
        authorIdent,
        when);
  }

  public void putComment(RobotComment c) {
    verifyComment(c);
    put.add(c);
  }

  private CommitBuilder storeCommentsInNotes(
      RevWalk rw, ObjectInserter ins, ObjectId curr, CommitBuilder cb)
      throws ConfigInvalidException, OrmException, IOException {
    RevisionNoteMap<RobotCommentsRevisionNote> rnm = getRevisionNoteMap(rw, curr);
    Set<RevId> updatedRevs = Sets.newHashSetWithExpectedSize(rnm.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (RobotComment c : put) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    boolean touchedAnyRevs = false;
    boolean hasComments = false;
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedRevs.add(e.getKey());
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, true);
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

  private RevisionNoteMap<RobotCommentsRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, OrmException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return RevisionNoteMap.emptyMap();
    }
    if (migration.readChanges()) {
      // If reading from changes is enabled, then the old RobotCommentNotes
      // already parsed the revision notes. We can reuse them as long as the ref
      // hasn't advanced.
      ChangeNotes changeNotes = getNotes();
      if (changeNotes != null) {
        RobotCommentNotes robotCommentNotes = changeNotes.load().getRobotCommentNotes();
        if (robotCommentNotes != null) {
          ObjectId idFromNotes = firstNonNull(robotCommentNotes.getRevision(), ObjectId.zeroId());
          RevisionNoteMap<RobotCommentsRevisionNote> rnm = robotCommentNotes.getRevisionNoteMap();
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
    return RevisionNoteMap.parseRobotComments(noteUtil, rw.getObjectReader(), noteMap);
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setMessage("Update robot comments");
    try {
      return storeCommentsInNotes(rw, ins, curr, cb);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    }
  }

  @Override
  protected Project.NameKey getProjectName() {
    return getNotes().getProjectName();
  }

  @Override
  protected String getRefName() {
    return robotCommentsRef(getId());
  }

  @Override
  public boolean isEmpty() {
    return put.isEmpty();
  }
}
