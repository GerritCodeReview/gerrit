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
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
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
      String pushCertificate,
      String description)
      throws IOException {
    requireNonNull(groups, "groups may not be null");
    ensurePatchSetMatches(psId, update);

    update.setCommit(rw, commit, pushCertificate);
    update.setPsDescription(description);
    update.setGroups(groups);

    PatchSet ps = new PatchSet(psId);
    ps.setRevision(new RevId(commit.name()));
    ps.setUploader(update.getAccountId());
    ps.setCreatedOn(new Timestamp(update.getWhen().getTime()));
    ps.setGroups(groups);
    ps.setPushCertificate(pushCertificate);
    ps.setDescription(description);
    return ps;
  }

  private static void ensurePatchSetMatches(PatchSet.Id psId, ChangeUpdate update) {
    Change.Id changeId = update.getChange().getId();
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

  public void setGroups(ChangeUpdate update, PatchSet ps, List<String> groups) {
    ps.setGroups(groups);
    update.setGroups(groups);
  }

  /** Check if the current patch set of the change is locked. */
  public void checkPatchSetNotLocked(ChangeNotes notes)
      throws IOException, ResourceConflictException {
    if (isPatchSetLocked(notes)) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", notes.getChangeId()));
    }
  }

  /** Is the current patch set locked against state changes? */
  public boolean isPatchSetLocked(ChangeNotes notes) throws IOException {
    Change change = notes.getChange();
    if (change.isMerged()) {
      return false;
    }

    ProjectState projectState = projectCache.checkedGet(notes.getProjectName());
    requireNonNull(
        projectState, () -> String.format("Failed to load project %s", notes.getProjectName()));

    ApprovalsUtil approvalsUtil = approvalsUtilProvider.get();
    for (PatchSetApproval ap :
        approvalsUtil.byPatchSet(notes, change.currentPatchSetId(), null, null)) {
      LabelType type = projectState.getLabelTypes(notes).byLabel(ap.getLabel());
      if (type != null
          && ap.getValue() == 1
          && type.getFunction() == LabelFunction.PATCH_SET_LOCK) {
        return true;
      }
    }
    return false;
  }

  /** Returns the commit for the given project at the given patchset revision */
  public RevCommit getRevCommit(Project.NameKey project, PatchSet patchSet) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit src = rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
      rw.parseBody(src);
      return src;
    }
  }
}
