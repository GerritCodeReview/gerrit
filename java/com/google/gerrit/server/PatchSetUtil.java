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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.gerrit.server.ChangeUtil.PS_ID_ORDER;
import static com.google.gerrit.server.notedb.PatchSetState.PUBLISHED;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
      return PS_ID_ORDER.immutableSortedCopy(db.patchSets().byChange(notes.getChangeId()));
    }
    return notes.load().getPatchSets().values();
  }

  public ImmutableMap<PatchSet.Id, PatchSet> byChangeAsMap(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    if (!migration.readChanges()) {
      ImmutableMap.Builder<PatchSet.Id, PatchSet> result = ImmutableMap.builder();
      for (PatchSet ps : PS_ID_ORDER.sortedCopy(db.patchSets().byChange(notes.getChangeId()))) {
        result.put(ps.getId(), ps);
      }
      return result.build();
    }
    return notes.load().getPatchSets();
  }

  public ImmutableMap<PatchSet.Id, PatchSet> getAsMap(
      ReviewDb db, ChangeNotes notes, Set<PatchSet.Id> patchSetIds) throws OrmException {
    if (!migration.readChanges()) {
      patchSetIds = Sets.filter(patchSetIds, p -> p.getParentKey().equals(notes.getChangeId()));
      return Streams.stream(db.patchSets().get(patchSetIds))
          .sorted(PS_ID_ORDER)
          .collect(toImmutableMap(PatchSet::getId, identity()));
    }
    return ImmutableMap.copyOf(Maps.filterKeys(notes.load().getPatchSets(), patchSetIds::contains));
  }

  public PatchSet insert(
      ReviewDb db,
      RevWalk rw,
      ChangeUpdate update,
      PatchSet.Id psId,
      ObjectId commit,
      List<String> groups,
      String pushCertificate,
      String description)
      throws OrmException, IOException {
    checkNotNull(groups, "groups may not be null");
    ensurePatchSetMatches(psId, update);

    PatchSet ps = new PatchSet(psId);
    ps.setRevision(new RevId(commit.name()));
    ps.setUploader(update.getAccountId());
    ps.setCreatedOn(new Timestamp(update.getWhen().getTime()));
    ps.setGroups(groups);
    ps.setPushCertificate(pushCertificate);
    ps.setDescription(description);
    db.patchSets().insert(Collections.singleton(ps));

    update.setCommit(rw, commit, pushCertificate);
    update.setPsDescription(description);
    update.setGroups(groups);

    return ps;
  }

  public void publish(ReviewDb db, ChangeUpdate update, PatchSet ps) throws OrmException {
    ensurePatchSetMatches(ps.getId(), update);
    update.setPatchSetState(PUBLISHED);
    db.patchSets().update(Collections.singleton(ps));
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

  /** Is the current patch set locked against state changes? */
  public static boolean isPatchSetLocked(
      ApprovalsUtil approvalsUtil,
      ProjectCache projectCache,
      ReviewDb db,
      ChangeNotes notes,
      CurrentUser user)
      throws OrmException, IOException {
    Change change = notes.getChange();
    if (change.getStatus() == Change.Status.MERGED) {
      return false;
    }

    ProjectState projectState = projectCache.checkedGet(notes.getProjectName());
    checkNotNull(projectState, "Failed to load project %s", notes.getProjectName());

    for (PatchSetApproval ap :
        approvalsUtil.byPatchSet(db, notes, user, change.currentPatchSetId(), null, null)) {
      LabelType type = projectState.getLabelTypes(notes, user).byLabel(ap.getLabel());
      if (type != null
          && ap.getValue() == 1
          && type.getFunction() == LabelFunction.PATCH_SET_LOCK) {
        return true;
      }
    }
    return false;
  }
}
