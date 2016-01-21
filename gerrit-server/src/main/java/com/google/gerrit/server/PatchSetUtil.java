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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;

import java.sql.Timestamp;
import java.util.Collections;

/** Utilities for manipulating patch sets. */
@Singleton
public class PatchSetUtil {
  private final NotesMigration migration;

  @Inject
  PatchSetUtil(NotesMigration migration) {
    this.migration = migration;
  }

  public PatchSet current(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    return get(db, notes, notes.getChange().currentPatchSetId());
  }

  @SuppressWarnings("unused") // TODO(dborowitz): Read from notedb.
  public PatchSet get(ReviewDb db, ChangeNotes notes, PatchSet.Id psId)
      throws OrmException {
    return db.patchSets().get(psId);
  }

  public ImmutableList<PatchSet> byChange(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    return ChangeUtil.PS_ID_ORDER.immutableSortedCopy(
        db.patchSets().byChange(notes.getChangeId()));
  }

  public PatchSet insert(ReviewDb db, ChangeUpdate update, PatchSet.Id psId,
      RevCommit commit, boolean draft, Iterable<String> groups,
      String pushCertificate) throws OrmException {
    Change.Id changeId = update.getChange().getId();
    checkArgument(psId.getParentKey().equals(changeId),
        "cannot insert patch set %s on change %s", psId, changeId);
    if (update.getPatchSetId() != null) {
      checkArgument(update.getPatchSetId().equals(psId),
          "cannot insert patch set %s on update for %s",
          psId, update.getPatchSetId());
    } else {
      update.setPatchSetId(psId);
    }

    PatchSet ps = new PatchSet(psId);
    ps.setRevision(new RevId(commit.name()));
    ps.setUploader(update.getUser().getAccountId());
    ps.setCreatedOn(new Timestamp(update.getWhen().getTime()));
    ps.setDraft(draft);
    ps.setGroups(groups);
    ps.setPushCertificate(pushCertificate);
    db.patchSets().insert(Collections.singleton(ps));

    if (!update.getChange().getSubject().equals(commit.getShortMessage())) {
      update.setSubject(commit.getShortMessage());
    }

    if (migration.writeChanges()) {
      // TODO(dborowitz): Write to notedb.
    }
    return ps;
  }
}
