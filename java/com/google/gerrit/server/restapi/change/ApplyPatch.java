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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.utils.CommitUtils;
import com.google.gerrit.server.submit.IntegrationConflictException;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.Applier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

@Singleton
public class ApplyPatch implements RestModifyView<ChangeResource, ApplyPatchInput>,
    UiAction<ChangeResource> {
// DO NOT SUBMIT
//  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> user;
  private final Provider<PersonIdent> serverIdent;
  private final GitRepositoryManager gitManager;
  private final NotifyResolver notifyResolver;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;


  @Inject
  ApplyPatch(PermissionBackend permissionBackend,
      ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache,
      Provider<IdentifiedUser> user,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      GitRepositoryManager gitManager,
      NotifyResolver notifyResolver,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetInserter.Factory patchSetInserterFactory) {
    this.permissionBackend = permissionBackend;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
    this.user = user;
    this.serverIdent = serverIdent;
    this.gitManager = gitManager;
    this.notifyResolver = notifyResolver;
    this.batchUpdateFactory = batchUpdateFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException, ConfigInvalidException, NoSuchProjectException, InvalidChangeOperationException {
    NameKey project = rsrc.getProject();
    contributorAgreements.check(project, rsrc.getUser());

    BranchNameKey destBranch = rsrc.getChange().getDest();
    permissionBackend.currentUser().project(project).ref(destBranch.branch())
        .check(RefPermission.CREATE_CHANGE);
    projectCache.get(project).orElseThrow(illegalState(rsrc.getProject())).checkStatePermitsWrite();

    IdentifiedUser identifiedUser = user.get();
    try (Repository repo = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the applied commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = repo.newObjectInserter();
        // DO NOT SUBMIT - pass the reader to the applier
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      Ref destRef = repo.getRefDatabase().exactRef(destBranch.branch());
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", destBranch.branch()));
      }
      ChangeData destChange = rsrc.getChangeData();
      if (destChange == null) {
        throw new InvalidChangeOperationException(
            "Apply-patch cannot be called for an non-existing change.");
      }

      if (destChange.change().isClosed()) {
        throw new InvalidChangeOperationException(String.format(
            "Apply-patch with Change-Id %s could not update the existing change %d "
                + "in destination branch %s of project %s, because the change was closed (%s)",
            destChange.getId(), destChange.getId().get(), destBranch.branch(), destBranch.project(),
            destChange.change().getStatus().name()));
      }

      String commitMessage = input.message != null ? input.message : destChange.commitMessage();
      commitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(commitMessage);

      ObjectId treeId;
      try {
        RevCommit baseCommit = revWalk.parseCommit(destRef.getObjectId());
        RevTree tip = baseCommit.getTree();
        InputStream patchStream = new ByteArrayInputStream(
            input.patch.getBytes(StandardCharsets.UTF_8));
        Applier applier = new Applier(repo, tip, oi);
        treeId = applier.applyPatch(patchStream);
      } catch (GitAPIException e) {
        throw new IOException("Cannot apply patch", e);
      }
      PersonIdent committerIdent = serverIdent.get();
      PersonIdent authorIdent = user.get().asIdentifiedUser()
          .newCommitterIdent(TimeUtil.now(), committerIdent.getTimeZone().toZoneId());

      CommitBuilder appliedCommit = new CommitBuilder();
      appliedCommit.setTreeId(treeId);
      appliedCommit.setParentId(destChange.currentPatchSet().commitId());
      appliedCommit.setAuthor(authorIdent);
      appliedCommit.setCommitter(committerIdent);
      appliedCommit.setMessage(commitMessage);
      matchAuthorToCommitterDate(
          projectCache.get(rsrc.getProject()).orElseThrow(illegalState(rsrc.getProject())),
          appliedCommit);
      CodeReviewCommit commit = revWalk.parseCommit(oi.insert(appliedCommit));
      commit.setFilesWithGitConflicts(commit.getFilesWithGitConflicts());
      oi.flush();

      try (BatchUpdate bu = batchUpdateFactory.create(project, identifiedUser, TimeUtil.now())) {
        bu.setRepository(repo, revWalk, oi);
        bu.setNotify(resolveNotify(input));
        Change.Id changeId;
        // The change key exists on the destination branch. The cherry pick
        // will be added as a new patch set.
        changeId =
            CommitUtils.insertPatchSet(
                bu,
                repo,
                patchSetInserterFactory,
                destChange.notes(),
                commit,
                input.topic,
                input.workInProgress,
                input.validationOptions);
        bu.execute();
        // DO NOT SUBMIT - where was that copied from?
        ChangeInfo changeInfo =
            json.noOptions().format(rsrc.getProject(), changeId);
        changeInfo.containsGitConflicts =
            !commit.getFilesWithGitConflicts().isEmpty() ? true : null;
        // DO NOT SUBMIT - should return another thing if there are conflicts. See CherryPickChange::cherryPick
        return Response.ok(changeInfo);
      }
    } catch (MergeIdenticalTreeException | MergeConflictException e) {
      throw new IntegrationConflictException("Cherry pick failed: " + e.getMessage(), e);
    } catch (
        InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (
        NoSuchChangeException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public Description getDescription(ChangeResource resource) throws Exception {
    // DO NOT SUBMIT - fill in.
    return null;
  }

  private static void matchAuthorToCommitterDate(ProjectState project, CommitBuilder commit) {
    if (project.is(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE)) {
      commit.setAuthor(new PersonIdent(commit.getAuthor(), commit.getCommitter().getWhen(),
          commit.getCommitter().getTimeZone()));
    }
  }

  private NotifyResolver.Result resolveNotify(ApplyPatchInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    return notifyResolver.resolve(
        firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);
  }
}
