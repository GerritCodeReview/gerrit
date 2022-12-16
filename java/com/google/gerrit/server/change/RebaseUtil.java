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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
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
  private final RebaseChangeOp.Factory rebaseFactory;

  @Inject
  RebaseUtil(
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      RebaseChangeOp.Factory rebaseFactory) {
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.rebaseFactory = rebaseFactory;
  }

  public static void verifyRebasePreconditions(
      ProjectCache projectCache, PatchSetUtil patchSetUtil, RevWalk rw, RevisionResource rsrc)
      throws ResourceConflictException, IOException, AuthException, PermissionBackendException {
    // Not allowed to rebase if the current patch set is locked.
    patchSetUtil.checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.REBASE);
    projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .checkStatePermitsWrite();

    if (!rsrc.getChange().isNew()) {
      throw new ResourceConflictException(
          String.format(
              "Change %s is %s", rsrc.getChange().getId(), ChangeUtil.status(rsrc.getChange())));
    } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
      throw new ResourceConflictException(
          String.format(
              "Error rebasing %s. Cannot rebase %s",
              rsrc.getChange().getId(),
              countParents(rw, rsrc.getPatchSet()) > 1
                  ? "merge commits"
                  : "commit with no ancestor"));
    }
  }

  public static boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    return countParents(rw, ps) == 1;
  }

  private static int countParents(RevWalk rw, PatchSet ps) throws IOException {
    RevCommit c = rw.parseCommit(ps.commitId());
    return c.getParentCount();
  }

  private static boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip) throws IOException {
    ObjectId baseId = base.commitId();
    ObjectId tipId = tip.commitId();
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
  }

  public boolean canRebase(PatchSet patchSet, BranchNameKey dest, Repository git, RevWalk rw) {
    try {
      ObjectId unused = findBaseRevision(patchSet, dest, git, rw, true);
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
    @Nullable
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
   * Parse or find the commit onto which a patch set should be rebased.
   *
   * <p>If a {@code rebaseInput.base} is provided, parse it. Otherwise, finds the latest patch set
   * of the change corresponding to this commit's parent, or the destination branch tip in the case
   * where the parent's change is merged.
   *
   * @param git the repository.
   * @param rw the RevWalk.
   * @param permissionBackend to check base reading permissions with.
   * @param rsrc to find the base for
   * @param rebaseInput to optionally parse the base from.
   * @param verifyNeedsRebase whether to verify if the change base is not already up to date
   * @return the commit onto which the patch set should be rebased.
   * @throws RestApiException if rebase is not possible.
   * @throws IOException if accessing the repository fails.
   * @throws PermissionBackendException if the user don't have permissions to read the base change.
   */
  public ObjectId parseOrFindBaseRevision(
      Repository git,
      RevWalk rw,
      PermissionBackend permissionBackend,
      RevisionResource rsrc,
      RebaseInput rebaseInput,
      boolean verifyNeedsRebase)
      throws RestApiException, IOException, PermissionBackendException {
    Change change = rsrc.getChange();

    if (rebaseInput == null || rebaseInput.base == null) {
      return findBaseRevision(rsrc.getPatchSet(), change.getDest(), git, rw, verifyNeedsRebase);
    }

    String inputBase = rebaseInput.base.trim();

    if (inputBase.isEmpty()) {
      return getDestRefTip(git, change.getDest());
    }

    Base base;
    try {
      base = parseBase(rsrc, inputBase);
    } catch (NoSuchChangeException e) {
      throw new UnprocessableEntityException(
          String.format("Base change not found: %s", inputBase), e);
    }
    if (base == null) {
      throw new ResourceConflictException(
          "base revision is missing from the destination branch: " + inputBase);
    }
    return getLatestRevisionForBaseChange(rw, permissionBackend, rsrc, base);
  }

  private ObjectId getDestRefTip(Repository git, BranchNameKey destRefKey)
      throws ResourceConflictException, IOException {
    // Remove existing dependency to other patch set.
    Ref destRef = git.exactRef(destRefKey.branch());
    if (destRef == null) {
      throw new ResourceConflictException(
          "can't rebase onto tip of branch " + destRefKey.branch() + "; branch doesn't exist");
    }
    return destRef.getObjectId();
  }

  private ObjectId getLatestRevisionForBaseChange(
      RevWalk rw, PermissionBackend permissionBackend, RevisionResource childRsrc, Base base)
      throws ResourceConflictException, AuthException, PermissionBackendException, IOException {

    Change child = childRsrc.getChange();
    PatchSet.Id baseId = base.patchSet().id();
    if (child.getId().equals(baseId.changeId())) {
      throw new ResourceConflictException(
          String.format("cannot rebase change %s onto itself", childRsrc.getChange().getId()));
    }

    permissionBackend.user(childRsrc.getUser()).change(base.notes()).check(ChangePermission.READ);

    Change baseChange = base.notes().getChange();
    if (!baseChange.getProject().equals(child.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(child.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.isAbandoned()) {
      throw new ResourceConflictException("base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, childRsrc.getPatchSet(), base.patchSet())) {
      throw new ResourceConflictException(
          "base change "
              + baseChange.getKey()
              + " is a descendant of the current change - recursion not allowed");
    }
    return base.patchSet().commitId();
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
   * @param verifyNeedsRebase whether to verify if the change base is not already up to date
   * @return the commit onto which the patch set should be rebased.
   * @throws RestApiException if rebase is not possible.
   * @throws IOException if accessing the repository fails.
   */
  public ObjectId findBaseRevision(
      PatchSet patchSet,
      BranchNameKey destBranch,
      Repository git,
      RevWalk rw,
      boolean verifyNeedsRebase)
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
          if (verifyNeedsRebase && depPatchSet.id().equals(depChange.currentPatchSetId())) {
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
      if (verifyNeedsRebase && baseId.equals(parentId)) {
        throw new ResourceConflictException("Change is already up to date.");
      }
    }
    return baseId;
  }

  public RebaseChangeOp getRebaseOp(RevisionResource revRsrc, RebaseInput input, ObjectId baseRev) {
    return applyRebaseInputToOp(
        rebaseFactory.create(revRsrc.getNotes(), revRsrc.getPatchSet(), baseRev), input);
  }

  public RebaseChangeOp getRebaseOp(
      RevisionResource revRsrc, RebaseInput input, Change.Id baseChange) {
    return applyRebaseInputToOp(
        rebaseFactory.create(revRsrc.getNotes(), revRsrc.getPatchSet(), baseChange), input);
  }

  private RebaseChangeOp applyRebaseInputToOp(RebaseChangeOp op, RebaseInput input) {
    return op.setForceContentMerge(true)
        .setAllowConflicts(input.allowConflicts)
        .setValidationOptions(
            ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions))
        .setFireRevisionCreated(true);
  }
}
