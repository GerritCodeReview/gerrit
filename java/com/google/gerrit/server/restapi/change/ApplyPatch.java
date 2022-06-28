// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchAsPatchSetInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ApplyPatch
    implements RestModifyView<ChangeResource, ApplyPatchAsPatchSetInput>, UiAction<ChangeResource> {
  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory jsonFactory;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> user;
  private final Provider<PersonIdent> serverIdent;
  private final GitRepositoryManager gitManager;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;

  @Inject
  ApplyPatch(
      PermissionBackend permissionBackend,
      ChangeJson.Factory jsonFactory,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache,
      Provider<IdentifiedUser> user,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      GitRepositoryManager gitManager,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetInserter.Factory patchSetInserterFactory) {
    this.permissionBackend = permissionBackend;
    this.jsonFactory = jsonFactory;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
    this.user = user;
    this.serverIdent = serverIdent;
    this.gitManager = gitManager;
    this.batchUpdateFactory = batchUpdateFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchAsPatchSetInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException, InvalidChangeOperationException {
    NameKey project = rsrc.getProject();
    contributorAgreements.check(project, rsrc.getUser());

    BranchNameKey destBranch = rsrc.getChange().getDest();
    permissionBackend
        .currentUser()
        .project(project)
        .ref(destBranch.branch())
        .check(RefPermission.CREATE_CHANGE);
    projectCache.get(project).orElseThrow(illegalState(rsrc.getProject())).checkStatePermitsWrite();

    try (Repository repo = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the applied commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      Ref destRef = repo.getRefDatabase().exactRef(destBranch.branch());
      if (destRef == null) {
        throw new ResourceNotFoundException(
            String.format("Branch %s does not exist.", destBranch.branch()));
      }
      ChangeData destChange = rsrc.getChangeData();
      if (destChange == null) {
        throw new PreconditionFailedException(
            "Apply-patch cannot be called without a destination change.");
      }

      if (destChange.change().isClosed()) {
        throw new PreconditionFailedException(
            String.format(
                "Apply-patch with Change-Id %s could not update the existing change %d "
                    + "in destination branch %s of project %s, because the change was closed (%s)",
                destChange.getId(),
                destChange.getId().get(),
                destBranch.branch(),
                destBranch.project(),
                destChange.change().getStatus().name()));
      }

      RevCommit baseCommit = revWalk.parseCommit(destRef.getObjectId());
      ObjectId treeId = ApplyPatchUtil.applyPatchToTree(repo, oi, input.patch, baseCommit);

      PersonIdent committerIdent = serverIdent.get();
      PersonIdent authorIdent =
          user.get()
              .asIdentifiedUser()
              .newCommitterIdent(TimeUtil.now(), committerIdent.getTimeZone().toZoneId());
      String commitMessage =
          CommitMessageUtil.checkAndSanitizeCommitMessage(destChange.commitMessage());

      CommitBuilder appliedCommit = new CommitBuilder();
      appliedCommit.setTreeId(treeId);
      appliedCommit.setParentId(destChange.currentPatchSet().commitId());
      appliedCommit.setCommitter(committerIdent);
      appliedCommit.setAuthor(authorIdent);
      matchAuthorToCommitterDate(
          projectCache.get(rsrc.getProject()).orElseThrow(illegalState(rsrc.getProject())),
          appliedCommit);
      appliedCommit.setMessage(commitMessage);

      CodeReviewCommit commit = revWalk.parseCommit(oi.insert(appliedCommit));
      commit.setFilesWithGitConflicts(commit.getFilesWithGitConflicts());
      oi.flush();

      try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
        bu.setRepository(repo, revWalk, oi);
        Change c = insertPatchSet(bu, repo, patchSetInserterFactory, destChange.notes(), commit);
        ChangeJson json = jsonFactory.create(ListChangesOption.CURRENT_REVISION);
        ChangeInfo changeInfo = json.format(c);
        changeInfo.containsGitConflicts =
            !commit.getFilesWithGitConflicts().isEmpty() ? true : null;
        return Response.ok(changeInfo);
      }
    } catch (NoSuchChangeException | RepositoryNotFoundException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) throws Exception {
    boolean projectStatePermitsWrite =
        projectCache.get(rsrc.getProject()).map(ProjectState::statePermitsWrite).orElse(false);
    return new Description()
        .setLabel("Apply patch")
        .setTitle("Applies the supplied patch into the current change.")
        .setVisible(
            and(
                projectStatePermitsWrite,
                permissionBackend
                    .currentUser()
                    .project(rsrc.getProject())
                    .testCond(ProjectPermission.CREATE_CHANGE)));
  }

  private static void matchAuthorToCommitterDate(ProjectState project, CommitBuilder commit) {
    if (project.is(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE)) {
      commit.setAuthor(
          new PersonIdent(
              commit.getAuthor(),
              commit.getCommitter().getWhen(),
              commit.getCommitter().getTimeZone()));
    }
  }

  private static Change insertPatchSet(
      BatchUpdate bu,
      Repository git,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeNotes destNotes,
      CodeReviewCommit commit)
      throws IOException, UpdateException, RestApiException {
    Change destChange = destNotes.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, commit);
    inserter.setMessage(buildMessageForPatchSet(psId, commit));
    bu.addOp(destChange.getId(), inserter);
    bu.execute();
    return inserter.getChange();
  }

  private static String buildMessageForPatchSet(PatchSet.Id psId, CodeReviewCommit commit) {
    StringBuilder stringBuilder =
        new StringBuilder(String.format("Uploaded patch set %s.", psId.get()));

    if (!commit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:\n");
      commit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("* ").append(filePath).append("\n"));
    }
    return stringBuilder.toString();
  }
}
