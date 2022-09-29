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
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utilities for manipulating patch sets. */
@Singleton
public class PatchSetUtil {
  private final Provider<ApprovalsUtil> approvalsUtilProvider;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> currentUser;

  @Inject
  PatchSetUtil(
      Provider<ApprovalsUtil> approvalsUtilProvider,
      ProjectCache projectCache,
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> currentUser) {
    this.approvalsUtilProvider = approvalsUtilProvider;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.currentUser = currentUser;
  }

  public PatchSet current(ChangeNotes notes) {
    return get(notes, notes.getChange().currentPatchSetId());
  }

  public PatchSet get(ChangeNotes notes, PatchSet.Id psId) {
    return notes.load().getPatchSets().get(psId);
  }

  public ImmutableCollection<PatchSet> byChange(ChangeNotes notes) {
    return notes.load().getPatchSets().values();
  }

  public ImmutableMap<PatchSet.Id, PatchSet> byChangeAsMap(ChangeNotes notes) {
    return notes.load().getPatchSets();
  }

  public ImmutableMap<PatchSet.Id, PatchSet> getAsMap(
      ChangeNotes notes, Set<PatchSet.Id> patchSetIds) {
    return ImmutableMap.copyOf(Maps.filterKeys(notes.load().getPatchSets(), patchSetIds::contains));
  }

  public PatchSet insert(
      RevWalk rw,
      ChangeUpdate update,
      PatchSet.Id psId,
      ObjectId commit,
      List<String> groups,
      @Nullable String pushCertificate,
      @Nullable String description)
      throws IOException {
    requireNonNull(groups, "groups may not be null");
    ensurePatchSetMatches(psId, update);

    update.setCommit(rw, commit, pushCertificate);
    update.setPsDescription(description);
    update.setGroups(groups);

    return PatchSet.builder()
        .id(psId)
        .commitId(commit)
        .uploader(update.getAccountId())
        .createdOn(update.getWhen())
        .groups(groups)
        .pushCertificate(Optional.ofNullable(pushCertificate))
        .description(Optional.ofNullable(description))
        .build();
  }

  private static void ensurePatchSetMatches(PatchSet.Id psId, ChangeUpdate update) {
    Change.Id changeId = update.getId();
    checkArgument(
        psId.changeId().equals(changeId),
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

  /** Check if the current patch set of the change is locked. */
  public void checkPatchSetNotLocked(ChangeNotes notes) throws ResourceConflictException {
    if (isPatchSetLocked(notes)) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", notes.getChangeId()));
    }
  }

  /** Is the current patch set locked against state changes? */
  public boolean isPatchSetLocked(ChangeNotes notes) {
    Change change = notes.getChange();
    if (change.isMerged()) {
      return false;
    }

    ProjectState projectState =
        projectCache.get(notes.getProjectName()).orElseThrow(illegalState(notes.getProjectName()));

    ApprovalsUtil approvalsUtil = approvalsUtilProvider.get();
    for (PatchSetApproval ap : approvalsUtil.byPatchSet(notes, change.currentPatchSetId())) {
      Optional<LabelType> type = projectState.getLabelTypes(notes).byLabel(ap.label());
      if (type.isPresent()
          && ap.value() == 1
          && type.get().getFunction() == LabelFunction.PATCH_SET_LOCK) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether the current user has permissions for adding a new patch set in the given change.
   *
   * @param projectState of the new patch set
   * @param rsrc of the destination change.
   * @param forgedAuthor The author that is requested to be listed.
   * @throws ResourceConflictException if the branch doesn't allow writing.
   * @throws AuthException if there are authentication errors.
   * @throws PermissionBackendException if there are permission issues.
   */
  public void checkPermissionsForAddingPatchSet(
      ProjectState projectState, ChangeResource rsrc, @Nullable AccountInput forgedAuthor)
      throws ResourceConflictException, AuthException, PermissionBackendException {
    // Not allowed to create a new patch set if the current patch set is locked.
    checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.ADD_PATCH_SET);
    if (forgedAuthor != null
        && !currentUser.get().getEmailAddresses().contains(forgedAuthor.email)) {
      permissionBackend
          .currentUser()
          .project(rsrc.getProject())
          .ref(rsrc.getChange().getDest().branch())
          .check(RefPermission.FORGE_AUTHOR);
    }
    projectState.checkStatePermitsWrite();
  }

  /**
   * Tests whether the current user has permissions for adding a new patch set in the given change.
   *
   * @param projectState of the new patch set
   * @param rsrc of the destination change.
   * @param forgedAuthor The author that is requested to be listed.
   */
  public boolean testPermissionsForAddingPatchSet(
      ProjectState projectState, ChangeResource rsrc, @Nullable AccountInput forgedAuthor) {
    try {
      checkPermissionsForAddingPatchSet(projectState, rsrc, forgedAuthor);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Returns the commit for the given project at the given patchset revision */
  public RevCommit getRevCommit(Project.NameKey project, PatchSet patchSet) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit src = rw.parseCommit(patchSet.commitId());
      rw.parseBody(src);
      return src;
    }
  }
}
