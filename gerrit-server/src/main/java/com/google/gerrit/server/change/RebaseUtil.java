// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.auto.value.AutoValue;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility methods related to rebasing changes. */
public class RebaseUtil {
  private static final Logger log = LoggerFactory.getLogger(RebaseUtil.class);

  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final Provider<ReviewDb> dbProvider;
  private final PatchSetUtil psUtil;

  @Inject
  RebaseUtil(
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      Provider<ReviewDb> dbProvider,
      PatchSetUtil psUtil) {
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.dbProvider = dbProvider;
    this.psUtil = psUtil;
  }

  public boolean canRebase(PatchSet patchSet, Branch.NameKey dest, Repository git, RevWalk rw) {
    try {
      findBaseRevision(patchSet, dest, git, rw);
      return true;
    } catch (RestApiException e) {
      return false;
    } catch (OrmException | IOException e) {
      log.warn(
          String.format(
              "Error checking if patch set %s on %s can be rebased", patchSet.getId(), dest),
          e);
      return false;
    }
  }

  @AutoValue
  abstract static class Base {
    private static Base create(ChangeControl ctl, PatchSet ps) {
      if (ctl == null) {
        return null;
      }
      return new AutoValue_RebaseUtil_Base(ctl, ps);
    }

    abstract ChangeControl control();

    abstract PatchSet patchSet();
  }

  Base parseBase(RevisionResource rsrc, String base) throws OrmException, NoSuchChangeException {
    ReviewDb db = dbProvider.get();

    // Try parsing the base as a ref string.
    PatchSet.Id basePatchSetId = PatchSet.Id.fromRef(base);
    if (basePatchSetId != null) {
      Change.Id baseChangeId = basePatchSetId.getParentKey();
      ChangeControl baseCtl = controlFor(rsrc, baseChangeId);
      if (baseCtl != null) {
        return Base.create(
            controlFor(rsrc, basePatchSetId.getParentKey()),
            psUtil.get(db, baseCtl.getNotes(), basePatchSetId));
      }
    }

    // Try parsing base as a change number (assume current patch set).
    Integer baseChangeId = Ints.tryParse(base);
    if (baseChangeId != null) {
      ChangeControl baseCtl = controlFor(rsrc, new Change.Id(baseChangeId));
      if (baseCtl != null) {
        return Base.create(baseCtl, psUtil.current(db, baseCtl.getNotes()));
      }
    }

    // Try parsing as SHA-1.
    Base ret = null;
    for (ChangeData cd : queryProvider.get().byProjectCommit(rsrc.getProject(), base)) {
      for (PatchSet ps : cd.patchSets()) {
        if (!ps.getRevision().matches(base)) {
          continue;
        }
        if (ret == null || ret.patchSet().getId().get() < ps.getId().get()) {
          ret = Base.create(rsrc.getControl().getProjectControl().controlFor(cd.notes()), ps);
        }
      }
    }
    return ret;
  }

  private ChangeControl controlFor(RevisionResource rsrc, Change.Id id)
      throws OrmException, NoSuchChangeException {
    if (rsrc.getChange().getId().equals(id)) {
      return rsrc.getControl();
    }
    ChangeNotes notes = notesFactory.createChecked(dbProvider.get(), rsrc.getProject(), id);
    return rsrc.getControl().getProjectControl().controlFor(notes);
  }

  /**
   * Find the commit onto which a patch set should be rebased.
   *
   * <p>This is defined as the latest patch set of the change corresponding to this commit's parent,
   * or the destination branch tip in the case where the parent's change is merged.
   *
   * @param patchSet patch set for which the new base commit should be found.
   * @param destBranch the destination branch.
   * @param git the repository.
   * @param rw the RevWalk.
   * @return the commit onto which the patch set should be rebased.
   * @throws RestApiException if rebase is not possible.
   * @throws IOException if accessing the repository fails.
   * @throws OrmException if accessing the database fails.
   */
  ObjectId findBaseRevision(
      PatchSet patchSet, Branch.NameKey destBranch, Repository git, RevWalk rw)
      throws RestApiException, IOException, OrmException {
    String baseRev = null;
    RevCommit commit = rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));

    if (commit.getParentCount() > 1) {
      throw new UnprocessableEntityException("Cannot rebase a change with multiple parents.");
    } else if (commit.getParentCount() == 0) {
      throw new UnprocessableEntityException(
          "Cannot rebase a change without any parents" + " (is this the initial commit?).");
    }

    RevId parentRev = new RevId(commit.getParent(0).name());

    CHANGES:
    for (ChangeData cd : queryProvider.get().byBranchCommit(destBranch, parentRev.get())) {
      for (PatchSet depPatchSet : cd.patchSets()) {
        if (!depPatchSet.getRevision().equals(parentRev)) {
          continue;
        }
        Change depChange = cd.change();
        if (depChange.getStatus() == Status.ABANDONED) {
          throw new ResourceConflictException(
              "Cannot rebase a change with an abandoned parent: " + depChange.getKey());
        }

        if (depChange.getStatus().isOpen()) {
          if (depPatchSet.getId().equals(depChange.currentPatchSetId())) {
            throw new ResourceConflictException(
                "Change is already based on the latest patch set of the" + " dependent change.");
          }
          baseRev = cd.currentPatchSet().getRevision().get();
        }
        break CHANGES;
      }
    }

    if (baseRev == null) {
      // We are dependent on a merged PatchSet or have no PatchSet
      // dependencies at all.
      Ref destRef = git.getRefDatabase().exactRef(destBranch.get());
      if (destRef == null) {
        throw new UnprocessableEntityException(
            "The destination branch does not exist: " + destBranch.get());
      }
      baseRev = destRef.getObjectId().getName();
      if (baseRev.equals(parentRev.get())) {
        throw new ResourceConflictException("Change is already up to date.");
      }
    }
    return ObjectId.fromString(baseRev);
  }
}
