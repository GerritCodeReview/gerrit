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
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utility methods related to rebasing changes. */
public class RebaseUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<PersonIdent> serverIdent;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final Provider<CurrentUser> self;

  @Inject
  RebaseUtil(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      IdentifiedUser.GenericFactory userFactory,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      RebaseChangeOp.Factory rebaseFactory,
      Provider<CurrentUser> self) {
    this.serverIdent = serverIdent;
    this.userFactory = userFactory;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.rebaseFactory = rebaseFactory;
    this.self = self;
  }

  /**
   * Checks that the uploader has permissions to create a new patch set as the current user which
   * can be used for {@link BatchUpdate} to do the rebase on behalf of the uploader.
   *
   * <p>The following permissions are required for the uploader:
   *
   * <ul>
   *   <li>The {@code Read} permission that allows to see the change.
   *   <li>The {@code Push} permission that allows upload.
   *   <li>The {@code Add Patch Set} permission, required if the change is owned by another user
   *       (change owners implicitly have this permission).
   *   <li>The {@code Forge Author} permission if the patch set that is rebased has a forged author
   *       (author != uploader).
   *   <li>The {@code Forge Server} permission if the patch set that is rebased has the server
   *       identity as the author.
   * </ul>
   *
   * <p>Usually the uploader should have all these permission since they were already required for
   * the original upload, but there is the edge case that the uploader had the permission when doing
   * the original upload and then the permission was revoked.
   *
   * <p>Note that patch sets with a forged committer (committer != uploader) can be rebased on
   * behalf of the uploader, even if the uploader doesn't have the {@code Forge Committer}
   * permission. This is because on rebase on behalf of the uploader the uploader will become the
   * committer of the new rebased patch set, hence for the rebased patch set the committer is no
   * longer forged (committer == uploader) and hence the {@code Forge Committer} permission is not
   * required.
   *
   * <p>Note that the {@code Rebase} permission is not required for the uploader since the {@code
   * Rebase} permission is specifically about allowing a user to do a rebase via the web UI by
   * clicking on the {@code REBASE} button and the uploader is not clicking on this button.
   *
   * <p>The permissions of the uploader are checked explicitly here so that we can return a {@code
   * 409 Conflict} response with a proper error message if they are missing (the error message says
   * that the permission is missing for the uploader). The normal code path also checks these
   * permission but the exception thrown there would result in a {@code 403 Forbidden} response and
   * the error message would wrongly look like the caller (i.e. the rebaser) is missing the
   * permission.
   *
   * <p>Note that this method doesn't check permissions for the rebaser (aka the impersonating user
   * aka the calling user). Callers should check the permissions for the rebaser before calling this
   * method.
   *
   * @param rsrc the revision resource that should be rebased
   * @param rebaseInput the request input containing options for the rebase
   */
  public void checkCanRebaseOnBehalfOf(RevisionResource rsrc, RebaseInput rebaseInput)
      throws IOException, PermissionBackendException, BadRequestException,
          ResourceConflictException {
    if (rebaseInput.allowConflicts) {
      throw new BadRequestException(
          "allow_conflicts and on_behalf_of_uploader are mutually exclusive");
    }

    if (rsrc.getPatchSet().id().get() != rsrc.getChange().currentPatchSetId().get()) {
      throw new BadRequestException(
          String.format(
              "change %s: non-current patch set cannot be rebased on behalf of the uploader",
              rsrc.getChange().getId()));
    }

    CurrentUser caller = rsrc.getUser();
    Account.Id uploaderId = rsrc.getPatchSet().uploader();
    IdentifiedUser uploader = userFactory.runAs(/*remotePeer= */ null, uploaderId, caller);
    logger.atFine().log(
        "%s is rebasing patch set %s of project %s on behalf of uploader %s",
        caller.getLoggableName(),
        rsrc.getPatchSet().id(),
        rsrc.getProject(),
        uploader.getLoggableName());

    checkPermissionForUploader(
        uploader,
        rsrc.getNotes(),
        ChangePermission.READ,
        String.format(
            "change %s: uploader %s cannot read change",
            rsrc.getChange().getId(), uploader.getLoggableName()));
    checkPermissionForUploader(
        uploader,
        rsrc.getNotes(),
        ChangePermission.ADD_PATCH_SET,
        String.format(
            "change %s: uploader %s cannot add patch set",
            rsrc.getChange().getId(), uploader.getLoggableName()));

    try (Repository repo = repoManager.openRepository(rsrc.getProject())) {
      RevCommit commit = repo.parseCommit(rsrc.getPatchSet().commitId());

      if (!uploader.hasEmailAddress(commit.getAuthorIdent().getEmailAddress())) {
        checkPermissionForUploader(
            uploader,
            rsrc.getNotes(),
            RefPermission.FORGE_AUTHOR,
            String.format(
                "change %s: author of patch set %d is forged and the uploader %s cannot forge author",
                rsrc.getChange().getId(),
                rsrc.getPatchSet().id().get(),
                uploader.getLoggableName()));

        if (serverIdent.get().getEmailAddress().equals(commit.getAuthorIdent().getEmailAddress())) {
          checkPermissionForUploader(
              uploader,
              rsrc.getNotes(),
              RefPermission.FORGE_SERVER,
              String.format(
                  "change %s: author of patch set %d is the server identity and the uploader %s cannot forge"
                      + " the server identity",
                  rsrc.getChange().getId(),
                  rsrc.getPatchSet().id().get(),
                  uploader.getLoggableName()));
        }
      }
    }
  }

  private void checkPermissionForUploader(
      IdentifiedUser uploader,
      ChangeNotes changeNotes,
      ChangePermission changePermission,
      String errorMessage)
      throws PermissionBackendException, ResourceConflictException {
    try {
      permissionBackend.user(uploader).change(changeNotes).check(changePermission);
    } catch (AuthException e) {
      throw new ResourceConflictException(errorMessage, e);
    }
  }

  private void checkPermissionForUploader(
      IdentifiedUser uploader,
      ChangeNotes changeNotes,
      RefPermission refPermission,
      String errorMessage)
      throws PermissionBackendException, ResourceConflictException {
    try {
      permissionBackend.user(uploader).ref(changeNotes.getChange().getDest()).check(refPermission);
    } catch (AuthException e) {
      throw new ResourceConflictException(errorMessage, e);
    }
  }

  /**
   * Checks whether the given change fulfills all preconditions to be rebased.
   *
   * <p>This method does not check whether the calling user is allowed to rebase the change.
   */
  public void verifyRebasePreconditions(RevWalk rw, ChangeNotes changeNotes, PatchSet patchSet)
      throws ResourceConflictException, IOException {
    // Not allowed to rebase if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(changeNotes);

    Change change = changeNotes.getChange();
    if (!change.isNew()) {
      throw new ResourceConflictException(
          String.format("Change %s is %s", change.getId(), ChangeUtil.status(change)));
    }

    if (!hasAtLeastOneParent(rw, patchSet)) {
      throw new ResourceConflictException(
          String.format(
              "Error rebasing %s. Cannot rebase commit with no ancestor", change.getId()));
    }
  }

  public static boolean hasAtLeastOneParent(RevWalk rw, PatchSet ps) throws IOException {
    // Prevent rebase of changes with no ancestor.
    return countParents(rw, ps) >= 1;
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
      RevCommit commit = rw.parseCommit(patchSet.commitId());

      if (commit.getParentCount() > 1) {
        throw new UnprocessableEntityException("Cannot rebase a change with multiple parents.");
      } else if (commit.getParentCount() == 0) {
        throw new UnprocessableEntityException(
            "Cannot rebase a change without any parents (is this the initial commit?).");
      }

      Ref destRef = git.getRefDatabase().exactRef(dest.branch());
      if (destRef == null) {
        throw new UnprocessableEntityException(
            "The destination branch does not exist: " + dest.branch());
      }

      // Change can be rebased if its parent commit differs from the HEAD commit of the destination
      // branch.
      // It's possible that the change is part of a chain that is based on the HEAD commit of the
      // destination branch and the chain cannot be rebased, but then the change can still be
      // rebased onto the destination branch to break the relation to its parent change.
      ObjectId parentId = commit.getParent(0);
      return !destRef.getObjectId().equals(parentId);
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

    // Try parsing as SHA-1 based on the change-index.
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
   * <p>If a {@code rebaseInput.base} is provided, parse it. Otherwise, find the latest patch set of
   * the change corresponding to this commit's parent, or the destination branch tip in the case
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
    if (base != null) {
      return getLatestRevisionForBaseChange(rw, permissionBackend, rsrc, base);
    }
    if (isBaseRevisionInDestBranch(rw, inputBase, git, change.getDest())) {
      // The requested base is a valid commit in the dest branch, which is not associated with any
      // Gerrit change.
      return ObjectId.fromString(inputBase);
    }

    // Support "refs/heads/..."
    Ref ref = git.getRefDatabase().exactRef(inputBase);
    if (ref != null
        && isBaseRevisionInDestBranch(
            rw, ObjectId.toString(ref.getObjectId()), git, change.getDest())) {
      return ref.getObjectId();
    }

    throw new ResourceConflictException(
        "base revision is missing from the destination branch: " + inputBase);
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

    if (commit.getParentCount() == 0) {
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

  private boolean isBaseRevisionInDestBranch(
      RevWalk rw, String expectedBaseSha1, Repository git, BranchNameKey destRefKey)
      throws IOException, ResourceConflictException {
    RevCommit potentialBaseCommit;
    try {
      potentialBaseCommit = rw.parseCommit(ObjectId.fromString(expectedBaseSha1));
    } catch (InvalidObjectIdException | IOException e) {
      return false;
    }
    return rw.isMergedInto(potentialBaseCommit, rw.parseCommit(getDestRefTip(git, destRefKey)));
  }

  public RebaseChangeOp getRebaseOp(
      RevWalk rw,
      RevisionResource revRsrc,
      RebaseInput input,
      ObjectId baseRev,
      IdentifiedUser rebaseAsUser)
      throws ResourceConflictException, PermissionBackendException, IOException {
    return applyRebaseInputToOp(
        rw,
        rebaseFactory.create(revRsrc.getNotes(), revRsrc.getPatchSet(), baseRev),
        input,
        rebaseAsUser);
  }

  public RebaseChangeOp getRebaseOp(
      RevWalk rw,
      RevisionResource revRsrc,
      RebaseInput input,
      Change.Id baseChange,
      IdentifiedUser rebaseAsUser)
      throws ResourceConflictException, PermissionBackendException, IOException {
    return applyRebaseInputToOp(
        rw,
        rebaseFactory.create(revRsrc.getNotes(), revRsrc.getPatchSet(), baseChange),
        input,
        rebaseAsUser);
  }

  private RebaseChangeOp applyRebaseInputToOp(
      RevWalk rw, RebaseChangeOp op, RebaseInput input, IdentifiedUser rebaseAsUser)
      throws ResourceConflictException, PermissionBackendException, IOException {
    RebaseChangeOp rebaseChangeOp =
        op.setForceContentMerge(true)
            .setAllowConflicts(input.allowConflicts)
            .setMergeStrategy(input.strategy)
            .setValidationOptions(
                ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions))
            .setFireRevisionCreated(true);

    String originalPatchSetCommitterEmail =
        rw.parseCommit(rebaseChangeOp.getOriginalPatchSet().commitId())
            .getCommitterIdent()
            .getEmailAddress();

    if (input.committerEmail != null) {
      if (!self.get().hasSameAccountId(rebaseAsUser)
          && !input.committerEmail.equals(rebaseAsUser.getAccount().preferredEmail())
          && !input.committerEmail.equals(originalPatchSetCommitterEmail)
          && !permissionBackend.currentUser().test(GlobalPermission.VIEW_SECONDARY_EMAILS)) {
        throw new ResourceConflictException(
            String.format(
                "Cannot rebase using committer email '%s'. It can only be done using the "
                    + "preferred email or the committer email of the uploader",
                input.committerEmail));
      }

      ImmutableSet<String> emails = rebaseAsUser.getEmailAddresses();
      if (!emails.contains(input.committerEmail)) {
        throw new ResourceConflictException(
            String.format(
                "Cannot rebase using committer email '%s' as it is not a registered "
                    + "email of the user on whose behalf the rebase operation is performed",
                input.committerEmail));
      }
      rebaseChangeOp.setCommitterIdent(
          new PersonIdent(
              rebaseAsUser.getName(),
              input.committerEmail,
              TimeUtil.now(),
              serverIdent.get().getZoneId()));
    }
    return rebaseChangeOp;
  }
}
