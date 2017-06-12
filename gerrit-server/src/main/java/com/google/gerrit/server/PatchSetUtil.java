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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static com.google.gerrit.server.notedb.PatchSetState.DRAFT;
import static com.google.gerrit.server.notedb.PatchSetState.PUBLISHED;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.PatchSetState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utilities for manipulating patch sets. */
@Singleton
public class PatchSetUtil {
  private final NotesMigration migration;

  @Inject
  PatchSetUtil(NotesMigration migration) {
    this.migration = migration;
  }

  public PatchSet current(ReviewDb db, ChangeNotes notes) throws OrmException {
    return get(db, notes, notes.getChange().currentPatchSetId());
  }

  public PatchSet get(ReviewDb db, ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return db.patchSets().get(psId);
    }
    return notes.load().getPatchSets().get(psId);
  }

  public ImmutableCollection<PatchSet> byChange(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    if (!migration.readChanges()) {
      return ChangeUtil.PS_ID_ORDER.immutableSortedCopy(
          db.patchSets().byChange(notes.getChangeId()));
    }
    return notes.load().getPatchSets().values();
  }

  public ImmutableMap<PatchSet.Id, PatchSet> byChangeAsMap(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    if (!migration.readChanges()) {
      ImmutableMap.Builder<PatchSet.Id, PatchSet> result = ImmutableMap.builder();
      for (PatchSet ps :
          ChangeUtil.PS_ID_ORDER.sortedCopy(db.patchSets().byChange(notes.getChangeId()))) {
        result.put(ps.getId(), ps);
      }
      return result.build();
    }
    return notes.load().getPatchSets();
  }

  public PatchSet insert(
      ReviewDb db,
      RevWalk rw,
      ChangeUpdate update,
      PatchSet.Id psId,
      ObjectId commit,
      boolean draft,
      List<String> groups,
      String pushCertificate)
      throws OrmException, IOException {
    checkNotNull(groups, "groups may not be null");
    ensurePatchSetMatches(psId, update);

    PatchSet ps = new PatchSet(psId);
    ps.setRevision(new RevId(commit.name()));
    ps.setUploader(update.getAccountId());
    ps.setCreatedOn(new Timestamp(update.getWhen().getTime()));
    ps.setDraft(draft);
    ps.setGroups(groups);
    ps.setPushCertificate(pushCertificate);
    db.patchSets().insert(Collections.singleton(ps));

    update.setCommit(rw, commit, pushCertificate);
    update.setGroups(groups);
    if (draft) {
      update.setPatchSetState(DRAFT);
    }

    return ps;
  }

  public void publish(ReviewDb db, ChangeUpdate update, PatchSet ps) throws OrmException {
    ensurePatchSetMatches(ps.getId(), update);
    ps.setDraft(false);
    update.setPatchSetState(PUBLISHED);
    db.patchSets().update(Collections.singleton(ps));
  }

  public void delete(ReviewDb db, ChangeUpdate update, PatchSet ps) throws OrmException {
    ensurePatchSetMatches(ps.getId(), update);
    checkArgument(ps.isDraft(), "cannot delete non-draft patch set %s", ps.getId());
    update.setPatchSetState(PatchSetState.DELETED);
    if (PrimaryStorage.of(update.getChange()) == REVIEW_DB) {
      // Avoid OrmConcurrencyException trying to delete non-existent entities.
      db.patchSets().delete(Collections.singleton(ps));
    }
  }

  private void ensurePatchSetMatches(PatchSet.Id psId, ChangeUpdate update) {
    Change.Id changeId = update.getChange().getId();
    checkArgument(
        psId.getParentKey().equals(changeId),
        "cannot modify patch set %s on update for change %s",
        psId,
        changeId);
    if (update.getPatchSetId() != null) {
      checkArgument(
          update.getPatchSetId().equals(psId),
          "cannot modify patch set %s on update for %s",
          psId,
          update.getPatchSetId());
    } else {
      update.setPatchSetId(psId);
    }
  }

  public void setGroups(ReviewDb db, ChangeUpdate update, PatchSet ps, List<String> groups)
      throws OrmException {
    ps.setGroups(groups);
    update.setGroups(groups);
    db.patchSets().update(Collections.singleton(ps));
  }
}
