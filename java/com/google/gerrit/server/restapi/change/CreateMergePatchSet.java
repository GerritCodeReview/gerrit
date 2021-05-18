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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.InvalidMergeStrategyException;
import com.google.gerrit.exceptions.MergeWithConflictsNotSupportedException;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.CommitsCollection;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CreateMergePatchSet implements RestModifyView<ChangeResource, MergePatchSetInput> {
  private final BatchUpdate.Factory updateFactory;
  private final GitRepositoryManager gitManager;
  private final CommitsCollection commits;
  private final TimeZone serverTimeZone;
  private final Provider<CurrentUser> user;
  private final ChangeJson.Factory jsonFactory;
  private final PatchSetUtil psUtil;
  private final MergeUtil.Factory mergeUtilFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ProjectCache projectCache;
  private final ChangeFinder changeFinder;
  private final PermissionBackend permissionBackend;

  @Inject
  CreateMergePatchSet(
      BatchUpdate.Factory updateFactory,
      GitRepositoryManager gitManager,
      CommitsCollection commits,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> user,
      ChangeJson.Factory json,
      PatchSetUtil psUtil,
      MergeUtil.Factory mergeUtilFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      ProjectCache projectCache,
      ChangeFinder changeFinder,
      PermissionBackend permissionBackend) {
    this.updateFactory = updateFactory;
    this.gitManager = gitManager;
    this.commits = commits;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.jsonFactory = json;
    this.psUtil = psUtil;
    this.mergeUtilFactory = mergeUtilFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.projectCache = projectCache;
    this.changeFinder = changeFinder;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, MergePatchSetInput in)
      throws IOException, RestApiException, UpdateException, PermissionBackendException {
    // Not allowed to create a new patch set if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.ADD_PATCH_SET);
    if (in.author != null) {
      permissionBackend
          .currentUser()
          .project(rsrc.getProject())
          .ref(rsrc.getChange().getDest().branch())
          .check(RefPermission.FORGE_AUTHOR);
    }

    ProjectState projectState =
        projectCache.get(rsrc.getProject()).orElseThrow(illegalState(rsrc.getProject()));
    projectState.checkStatePermitsWrite();

    MergeInput merge = in.merge;
    if (merge == null || Strings.isNullOrEmpty(merge.source)) {
      throw new BadRequestException("merge.source must be non-empty");
    }
    if (in.author != null
        && (Strings.isNullOrEmpty(in.author.email) || Strings.isNullOrEmpty(in.author.name))) {
      throw new BadRequestException("Author must specify name and email");
    }
    in.baseChange = Strings.nullToEmpty(in.baseChange).trim();

    PatchSet ps = psUtil.current(rsrc.getNotes());
    Change change = rsrc.getChange();
    Project.NameKey project = change.getProject();
    BranchNameKey dest = change.getDest();
    try (Repository git = gitManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(reader)) {

      RevCommit sourceCommit = MergeUtil.resolveCommit(git, rw, merge.source);
      if (!commits.canRead(projectState, git, sourceCommit)) {
        throw new ResourceNotFoundException(
            "cannot find source commit: " + merge.source + " to merge.");
      }

      RevCommit currentPsCommit;
      List<String> groups = null;
      if (!in.inheritParent && !in.baseChange.isEmpty()) {
        PatchSet basePS = findBasePatchSet(in.baseChange);
        currentPsCommit = rw.parseCommit(basePS.commitId());
        groups = basePS.groups();
      } else {
        currentPsCommit = rw.parseCommit(ps.commitId());
      }

      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser me = user.get().asIdentifiedUser();
      PersonIdent author =
          in.author == null
              ? me.newCommitterIdent(now, serverTimeZone)
              : new PersonIdent(in.author.name, in.author.email, now, serverTimeZone);
      CodeReviewCommit newCommit =
          createMergeCommit(
              in,
              projectState,
              dest,
              git,
              oi,
              rw,
              currentPsCommit,
              sourceCommit,
              author,
              ObjectId.fromString(change.getKey().get().substring(1)));
      oi.flush();

      PatchSet.Id nextPsId = ChangeUtil.nextPatchSetId(ps.id());
      PatchSetInserter psInserter =
          patchSetInserterFactory.create(rsrc.getNotes(), nextPsId, newCommit);
      try (BatchUpdate bu = updateFactory.create(project, me, now)) {
        bu.setRepository(git, rw, oi);
        bu.setNotify(NotifyResolver.Result.none());
        psInserter
            .setMessage(messageForChange(nextPsId, newCommit))
            .setWorkInProgress(!newCommit.getFilesWithGitConflicts().isEmpty())
            .setCheckAddPatchSetPermission(false);
        if (groups != null) {
          psInserter.setGroups(groups);
        }
        bu.addOp(rsrc.getId(), psInserter);
        bu.execute();
      }

      ChangeJson json = jsonFactory.create(ListChangesOption.CURRENT_REVISION);
      ChangeInfo changeInfo = json.format(psInserter.getChange());
      changeInfo.containsGitConflicts =
          !newCommit.getFilesWithGitConflicts().isEmpty() ? true : null;
      return Response.ok(changeInfo);
    } catch (InvalidMergeStrategyException | MergeWithConflictsNotSupportedException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private PatchSet findBasePatchSet(String baseChange)
      throws PermissionBackendException, UnprocessableEntityException {
    List<ChangeNotes> notes = changeFinder.find(baseChange);
    if (notes.size() != 1) {
      throw new UnprocessableEntityException("Base change not found: " + baseChange);
    }
    ChangeNotes change = Iterables.getOnlyElement(notes);
    try {
      permissionBackend.currentUser().change(change).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException("Read not permitted for " + baseChange, e);
    }
    return psUtil.current(change);
  }

  private CodeReviewCommit createMergeCommit(
      MergePatchSetInput in,
      ProjectState projectState,
      BranchNameKey dest,
      Repository git,
      ObjectInserter oi,
      CodeReviewRevWalk rw,
      RevCommit currentPsCommit,
      RevCommit sourceCommit,
      PersonIdent author,
      ObjectId changeId)
      throws ResourceNotFoundException, MergeIdenticalTreeException, MergeConflictException,
          IOException {

    ObjectId parentCommit;
    if (in.inheritParent) {
      // inherit first parent from previous patch set
      parentCommit = currentPsCommit.getParent(0);
    } else if (!in.baseChange.isEmpty()) {
      parentCommit = currentPsCommit.getId();
    } else {
      // get the current branch tip of destination branch
      Ref destRef = git.getRefDatabase().exactRef(dest.branch());
      if (destRef != null) {
        parentCommit = destRef.getObjectId();
      } else {
        throw new ResourceNotFoundException("cannot find destination branch");
      }
    }
    RevCommit mergeTip = rw.parseCommit(parentCommit);

    String commitMsg;
    if (Strings.emptyToNull(in.subject) != null) {
      commitMsg = ChangeIdUtil.insertId(in.subject, changeId);
    } else {
      // reuse previous patch set commit message
      commitMsg = currentPsCommit.getFullMessage();
    }

    String mergeStrategy =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(in.merge.strategy),
            mergeUtilFactory.create(projectState).mergeStrategyName());

    return MergeUtil.createMergeCommit(
        oi,
        git.getConfig(),
        mergeTip,
        sourceCommit,
        mergeStrategy,
        in.merge.allowConflicts,
        author,
        commitMsg,
        rw);
  }

  private static String messageForChange(PatchSet.Id patchSetId, CodeReviewCommit commit) {
    StringBuilder stringBuilder =
        new StringBuilder(String.format("Uploaded patch set %s.", patchSetId.get()));

    if (!commit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:\n");
      commit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("* ").append(filePath).append("\n"));
    }

    return stringBuilder.toString();
  }
}
