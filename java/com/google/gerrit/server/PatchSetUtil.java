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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.RepoContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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

  @Inject
  PatchSetUtil(
      Provider<ApprovalsUtil> approvalsUtilProvider,
      ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    this.approvalsUtilProvider = approvalsUtilProvider;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
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
        .realUploader(update.getRealAccountId())
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

  /** Returns the commit for the given project at the given patchset revision */
  public RevCommit getRevCommit(Project.NameKey project, PatchSet patchSet) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit src = rw.parseCommit(patchSet.commitId());
      rw.parseBody(src);
      return src;
    }
  }

  /**
   * Gets the commit ID for the latest patch-set of a given change.
   *
   * <p>This also takes into account the patch sets that are added in the provided {@link
   * RepoContext}.
   *
   * @param ctx to look for pending updates in.
   * @param notesFactory to fetch existing patch sets with.
   * @param changeId to get the latest commit for.
   * @return the latest commit ID.
   * @throws IOException if no committed nor pending commits found for the change.
   */
  public static RevCommit getCurrentRevCommitIncludingPending(
      RepoContext ctx, ChangeNotes.Factory notesFactory, Change.Id changeId) throws IOException {
    Map<String, ObjectId> refUpdates = ctx.getRepoView().getRefs(changeId.toRefPrefix());
    refUpdates.remove("meta");
    if (!refUpdates.isEmpty()) {
      Optional<PatchSet.Id> latestPendingPatchSet =
          refUpdates.keySet().stream()
              .map(r -> PatchSet.Id.fromRef(changeId.toRefPrefix() + r))
              .max(PatchSet.Id::compareTo);
      if (latestPendingPatchSet.isPresent()) {
        return ctx.getRevWalk().parseCommit(refUpdates.get(latestPendingPatchSet.get().getId()));
      }
    }
    return getCurrentCommittedRevCommit(ctx.getProject(), ctx.getRevWalk(), notesFactory, changeId);
  }

  /**
   * Gets the commit ID for the latest committed patch-set of a given change.
   *
   * <p>This DOES NOT take into account the patch sets that are added in the provided {@link
   * RepoContext}.
   *
   * @param project name.
   * @param notesFactory to fetch existing patch sets with.
   * @param changeId to get the latest commit for.
   * @return the latest commit ID.
   * @throws IOException if no committed commits found for the change.
   */
  public static RevCommit getCurrentCommittedRevCommit(
      Project.NameKey project,
      RevWalk revWalk,
      ChangeNotes.Factory notesFactory,
      Change.Id changeId)
      throws IOException {
    ChangeNotes notes = notesFactory.createChecked(project, changeId);
    return revWalk.parseCommit(notes.getCurrentPatchSet().commitId());
  }
}
