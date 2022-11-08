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

  @Inject
  RebaseUtil(
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil) {
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
  }

  public static ObjectId findBaseRevision(
      Repository repo,
      RevWalk rw,
      RebaseUtil rebaseUtil,
      PermissionBackend permissionBackend,
      RevisionResource rsrc,
      RebaseInput input)
      throws RestApiException, IOException, NoSuchChangeException, AuthException,
      PermissionBackendException {
    BranchNameKey destRefKey = rsrc.getChange().getDest();
    if (input == null || input.base == null) {
      return rebaseUtil.findBaseRevision(rsrc.getPatchSet(), destRefKey, repo, rw);
    }

    Change change = rsrc.getChange();
    String str = input.base.trim();
    if (str.equals("")) {
      // Remove existing dependency to other patch set.
      Ref destRef = repo.exactRef(destRefKey.branch());
      if (destRef == null) {
        throw new ResourceConflictException(
            "can't rebase onto tip of branch " + destRefKey.branch() + "; branch doesn't exist");
      }
      return destRef.getObjectId();
    }

    Base base;
    try {
      base = rebaseUtil.parseBase(rsrc, str);
      if (base == null) {
        throw new ResourceConflictException(
            "base revision is missing from the destination branch: " + str);
      }
    } catch (NoSuchChangeException e) {
      throw new UnprocessableEntityException(
          String.format("Base change not found: %s", input.base), e);
    }

    PatchSet.Id baseId = base.patchSet().id();
    if (change.getId().equals(baseId.changeId())) {
      throw new ResourceConflictException("cannot rebase change onto itself");
    }

    permissionBackend.user(rsrc.getUser()).change(base.notes()).check(ChangePermission.READ);

    Change baseChange = base.notes().getChange();
    if (!baseChange.getProject().equals(change.getProject())) {
      throw new ResourceConflictException(
          "base change is in wrong project: " + baseChange.getProject());
    } else if (!baseChange.getDest().equals(change.getDest())) {
      throw new ResourceConflictException(
          "base change is targeting wrong branch: " + baseChange.getDest());
    } else if (baseChange.isAbandoned()) {
      throw new ResourceConflictException("base change is abandoned: " + baseChange.getKey());
    } else if (isMergedInto(rw, rsrc.getPatchSet(), base.patchSet())) {
      throw new ResourceConflictException(
          "base change "
              + baseChange.getKey()
              + " is a descendant of the current change - recursion not allowed");
    }
    return base.patchSet().commitId();
  }

  private static boolean isMergedInto(RevWalk rw, PatchSet base, PatchSet tip) throws IOException {
    ObjectId baseId = base.commitId();
    ObjectId tipId = tip.commitId();
    return rw.isMergedInto(rw.parseCommit(baseId), rw.parseCommit(tipId));
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
      throw new ResourceConflictException("change is " + ChangeUtil.status(rsrc.getChange()));
    } else if (!hasOneParent(rw, rsrc.getPatchSet())) {
      throw new ResourceConflictException("cannot rebase merge commits or commit with no ancestor");
    }
  }

  public static boolean hasOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of exotic changes (merge commit, no ancestor).
    RevCommit c = rw.parseCommit(ps.commitId());
    return c.getParentCount() == 1;
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
