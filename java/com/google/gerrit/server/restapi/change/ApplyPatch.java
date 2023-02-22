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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Strings;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ApplyPatch implements RestModifyView<ChangeResource, ApplyPatchPatchSetInput> {
  private final ChangeJson.Factory jsonFactory;
  private final ContributorAgreementsChecker contributorAgreements;
  private final Provider<IdentifiedUser> user;
  private final GitRepositoryManager gitManager;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ZoneId serverZoneId;

  @Inject
  ApplyPatch(
      ChangeJson.Factory jsonFactory,
      ContributorAgreementsChecker contributorAgreements,
      Provider<IdentifiedUser> user,
      GitRepositoryManager gitManager,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent) {
    this.jsonFactory = jsonFactory;
    this.contributorAgreements = contributorAgreements;
    this.user = user;
    this.gitManager = gitManager;
    this.batchUpdateFactory = batchUpdateFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.queryProvider = queryProvider;
    this.serverZoneId = myIdent.getZoneId();
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchPatchSetInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException, InvalidChangeOperationException {
    NameKey project = rsrc.getProject();
    contributorAgreements.check(project, rsrc.getUser());
    BranchNameKey destBranch = rsrc.getChange().getDest();

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
            "patch:apply cannot be called without a destination change.");
      }

      if (destChange.change().isClosed()) {
        throw new PreconditionFailedException(
            String.format(
                "patch:apply with Change-Id %s could not update the existing change %d "
                    + "in destination branch %s of project %s, because the change was closed (%s)",
                destChange.getId(),
                destChange.getId().get(),
                destBranch.branch(),
                destBranch.project(),
                destChange.change().getStatus().name()));
      }

      // When no explicit base is provided, the patch should be applied on top of the latest
      // patch-set. However, the result patch-set should be based on top of the latest patch-set
      // -parent-.
      RevCommit patchsetBaseCommit;
      RevCommit changeBaseCommit;
      if (!Strings.isNullOrEmpty(input.base)) {
        patchsetBaseCommit =
            CommitUtil.getBaseCommit(
                project.get(), queryProvider.get(), revWalk, destRef, input.base);
        changeBaseCommit = patchsetBaseCommit;
      } else {
        patchsetBaseCommit = revWalk.parseCommit(destChange.currentPatchSet().commitId());
        if (patchsetBaseCommit.getParentCount() != 1) {
          throw new BadRequestException(
              String.format(
                  "Cannot parse base commit for a change with none or multiple parents. Change ID: %s.",
                  destChange.getId()));
        }
        changeBaseCommit = revWalk.parseCommit(patchsetBaseCommit.getParent(0));
      }
      ObjectId treeId = ApplyPatchUtil.applyPatch(repo, oi, input.patch, patchsetBaseCommit);

      Instant now = TimeUtil.now();
      PersonIdent committerIdent = user.get().newCommitterIdent(now, serverZoneId);
      PersonIdent authorIdent =
          input.author == null
              ? committerIdent
              : new PersonIdent(input.author.name, input.author.email, now, serverZoneId);
      String commitMessage =
          CommitMessageUtil.checkAndSanitizeCommitMessage(
              input.commitMessage != null
                  ? input.commitMessage
                  : "The following patch was applied:\n>\t"
                      + input.patch.patch.replaceAll("\n", "\n>\t"));

      ObjectId appliedCommit =
          CommitUtil.createCommitWithTree(
              oi, authorIdent, committerIdent, changeBaseCommit, commitMessage, treeId);
      CodeReviewCommit commit = revWalk.parseCommit(appliedCommit);
      oi.flush();

      Change resultChange;
      try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
        bu.setRepository(repo, revWalk, oi);
        resultChange =
            insertPatchSet(bu, repo, patchSetInserterFactory, destChange.notes(), commit);
      } catch (NoSuchChangeException | RepositoryNotFoundException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      ChangeJson json = jsonFactory.create(ListChangesOption.CURRENT_REVISION);
      ChangeInfo changeInfo = json.format(resultChange);
      return Response.ok(changeInfo);
    }
  }

  private static Change insertPatchSet(
      BatchUpdate bu,
      Repository git,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeNotes destNotes,
      CodeReviewCommit commit)
      throws IOException, UpdateException, RestApiException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      Change destChange = destNotes.getChange();
      PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
      PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, commit);
      inserter.setMessage(buildMessageForPatchSet(psId));
      bu.addOp(destChange.getId(), inserter);
      bu.execute();
      return inserter.getChange();
    }
  }

  private static String buildMessageForPatchSet(PatchSet.Id psId) {
    return new StringBuilder(String.format("Uploaded patch set %s.", psId.get())).toString();
  }
}
