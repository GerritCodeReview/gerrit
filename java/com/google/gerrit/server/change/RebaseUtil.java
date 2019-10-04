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
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utility methods related to rebasing changes. */
public class RebaseUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;

  @Inject
  RebaseUtil(
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil) {
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
  }

  public boolean canRebase(PatchSet patchSet, BranchNameKey dest, Repository git, RevWalk rw) {
    try {
      findBaseRevision(patchSet, dest, git, rw);
      return true;
    } catch (RestApiException e) {
      return false;
    } catch (StorageException | IOException e) {
      logger.atWarning().withCause(e).log(
          "Error checking if patch set %s on %s can be rebased", patchSet.id(), dest);
      return false;
    }
  }

  @AutoValue
  public abstract static class Base {
    private static Base create(ChangeNotes notes, PatchSet ps) {
      if (notes == null) {
        return null;
      }
      return new AutoValue_RebaseUtil_Base(notes, ps);
    }

    public abstract ChangeNotes notes();

    public abstract PatchSet patchSet();
  }

  public Base parseBase(RevisionResource rsrc, String base) {
    // Try parsing the base as a ref string.
    PatchSet.Id basePatchSetId = PatchSet.Id.fromRef(base);
    if (basePatchSetId != null) {
      Change.Id baseChangeId = basePatchSetId.changeId();
      ChangeNotes baseNotes = notesFor(rsrc, baseChangeId);
      if (baseNotes != null) {
        return Base.create(
            notesFor(rsrc, basePatchSetId.changeId()), psUtil.get(baseNotes, basePatchSetId));
      }
    }

    // Try parsing base as a change number (assume current patch set).
    Integer baseChangeId = Ints.tryParse(base);
    if (baseChangeId != null) {
      ChangeNotes baseNotes = notesFor(rsrc, Change.id(baseChangeId));
      if (baseNotes != null) {
        return Base.create(baseNotes, psUtil.current(baseNotes));
      }
    }

    // Try parsing as SHA-1.
    Base ret = null;
    for (ChangeData cd : queryProvider.get().byProjectCommit(rsrc.getProject(), base)) {
      for (PatchSet ps : cd.patchSets()) {
        if (!ObjectIds.matchesAbbreviation(ps.commitId(), base)) {
          continue;
        }
        if (ret == null || ret.patchSet().id().get() < ps.id().get()) {
          ret = Base.create(cd.notes(), ps);
        }
      }
    }
    return ret;
  }

  private ChangeNotes notesFor(RevisionResource rsrc, Change.Id id) {
    if (rsrc.getChange().getId().equals(id)) {
      return rsrc.getNotes();
    }
    return notesFactory.createChecked(rsrc.getProject(), id);
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
   */
  public ObjectId findBaseRevision(
      PatchSet patchSet, BranchNameKey destBranch, Repository git, RevWalk rw)
      throws RestApiException, IOException {
    ObjectId baseId = null;
    RevCommit commit = rw.parseCommit(patchSet.commitId());

    if (commit.getParentCount() > 1) {
      throw new UnprocessableEntityException("Cannot rebase a change with multiple parents.");
    } else if (commit.getParentCount() == 0) {
      throw new UnprocessableEntityException(
          "Cannot rebase a change without any parents (is this the initial commit?).");
    }

    ObjectId parentId = commit.getParent(0);

    CHANGES:
    for (ChangeData cd : queryProvider.get().byBranchCommit(destBranch, parentId.name())) {
      for (PatchSet depPatchSet : cd.patchSets()) {
        if (!depPatchSet.commitId().equals(parentId)) {
          continue;
        }
        Change depChange = cd.change();
        if (depChange.isAbandoned()) {
          throw new ResourceConflictException(
              "Cannot rebase a change with an abandoned parent: " + depChange.getKey());
        }

        if (depChange.isNew()) {
          if (depPatchSet.id().equals(depChange.currentPatchSetId())) {
            throw new ResourceConflictException(
                "Change is already based on the latest patch set of the dependent change.");
          }
          baseId = cd.currentPatchSet().commitId();
        }
        break CHANGES;
      }
    }

    if (baseId == null) {
      // We are dependent on a merged PatchSet or have no PatchSet
      // dependencies at all.
      Ref destRef = git.getRefDatabase().exactRef(destBranch.branch());
      if (destRef == null) {
        throw new UnprocessableEntityException(
            "The destination branch does not exist: " + destBranch.branch());
      }
      baseId = destRef.getObjectId();
      if (baseId.equals(parentId)) {
        throw new ResourceConflictException("Change is already up to date.");
      }
    }
    return baseId;
  }
}
