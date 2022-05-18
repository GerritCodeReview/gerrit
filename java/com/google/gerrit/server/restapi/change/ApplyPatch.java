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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.SetCherryPickOp;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
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
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.IntegrationConflictException;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class ApplyPatch implements RestModifyView<ChangeResource, ApplyPatchInput>,
    UiAction<ChangeResource> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> user;
  private final Provider<PersonIdent> serverIdent;
  private final GitRepositoryManager gitManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final Sequences seq;
  private final ChangeNotes.Factory changeNotesFactory;
  private final NotifyResolver notifyResolver;

  @Inject
  ApplyPatch(PermissionBackend permissionBackend, ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements, ProjectCache projectCache,
      Provider<IdentifiedUser> user, @GerritPersonIdent Provider<PersonIdent> serverIdent,
      GitRepositoryManager gitManager, Provider<InternalChangeQuery> queryProvider,
      Sequences seq,
      ChangeNotes.Factory changeNotesFactory,
      NotifyResolver notifyResolver,
      ChangeInserter.Factory changeInserterFactory,
      BatchUpdate.Factory batchUpdateFactory) {
    this.permissionBackend = permissionBackend;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
    this.user = user;
    this.seq = seq;
    this.serverIdent = serverIdent;
    this.changeNotesFactory = changeNotesFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.gitManager = gitManager;
    this.notifyResolver = notifyResolver;
    this.queryProvider = queryProvider;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException, ConfigInvalidException, NoSuchProjectException, InvalidChangeOperationException {
    if (input.destinationBranch == null || input.destinationBranch.trim().isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    NameKey project = rsrc.getProject();
    String destBranchRefName = RefNames.fullName(input.destinationBranch.trim());
    BranchNameKey destBranch = BranchNameKey.create(rsrc.getProject(), destBranchRefName);
    contributorAgreements.check(project, rsrc.getUser());

    permissionBackend.currentUser().project(project).ref(destBranchRefName)
        .check(RefPermission.CREATE_CHANGE);
    projectCache.get(project).orElseThrow(illegalState(rsrc.getProject())).checkStatePermitsWrite();

    IdentifiedUser identifiedUser = user.get();
    try (Repository repo = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the applied commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = repo.newObjectInserter(); ObjectReader reader = oi.newReader(); CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(
        reader)) {
      Ref destRef = repo.getRefDatabase().exactRef(destBranch.branch());
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", destBranch.branch()));
      }

      String commitMessage = Strings.nullToEmpty(input.message);
      String destChangeId = CommitMessageUtil.getChangeIdFromCommitMessageFooter(commitMessage)
          .orElse(null);

      ChangeData destChange = null;
      if (destChangeId != null) {
        // DO NOT SUBMIT partial copy of getDestChangeWithVerification
        List<ChangeData> destChanges = queryProvider.get().setLimit(2)
            .byBranchKey(destBranch, Change.key(destChangeId));
        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException(
              "Several changes with key " + destChangeId + " reside on the same branch. "
                  + "Cannot create a new patch set.");
        }
        destChange = destChanges.size() == 1 ? destChanges.get(0) : null;

        if (destChange != null && destChange.change().isClosed()) {
          throw new InvalidChangeOperationException(String.format(
              "Cherry-pick with Change-Id %s could not update the existing change %d "
                  + "in destination branch %s of project %s, because the change was closed (%s)",
              destChangeId, destChange.getId().get(), destBranch.branch(), destBranch.project(),
              destChange.change().getStatus().name()));
        }
      }
      if (destChangeId == null) {
        // If commit message did not specify Change-Id, generate a new one and insert to the
        // message.
        commitMessage = ChangeIdUtil.insertId(commitMessage, CommitMessageUtil.generateChangeId(),
            true);
      }
      commitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(commitMessage);

      ApplyResult result;
      try (Git git = new Git(repo)) {
        InputStream patchStream = new ByteArrayInputStream(
            input.patch.getBytes(StandardCharsets.UTF_8));
        ApplyCommand applyCommand = git.apply().setPatch(patchStream);

        result = applyCommand.call();
        logger.atWarning().log("~~~~~~~~DNS result: %s", result.getUpdatedFiles());
      } catch (GitAPIException e) {
        throw new IOException("Cannot apply patch", e);
      }
      RevCommit baseCommit = getBaseCommit(destRef, project.get(), revWalk, input.base);
      PersonIdent committerIdent = serverIdent.get();
      PersonIdent authorIdent = user.get().asIdentifiedUser()
          .newCommitterIdent(TimeUtil.now(), committerIdent.getTimeZone());

      CommitBuilder appliedCommit = new CommitBuilder();
      appliedCommit.setTreeId(baseCommit.getTree());
      appliedCommit.setParentId(baseCommit);
      appliedCommit.setAuthor(authorIdent);
      appliedCommit.setCommitter(committerIdent);
      appliedCommit.setMessage(commitMessage);
      matchAuthorToCommitterDate(
          projectCache.get(rsrc.getProject()).orElseThrow(illegalState(rsrc.getProject())),
          appliedCommit);
      CodeReviewCommit commit = revWalk.parseCommit(oi.insert(appliedCommit));
      commit.setFilesWithGitConflicts(commit.getFilesWithGitConflicts());
      oi.flush();

      // DO NOT SUBMIT: Similar to CherryPickChange::createNewChange.

      try (BatchUpdate bu = batchUpdateFactory.create(project, identifiedUser, TimeUtil.now())) {
        bu.setRepository(repo, revWalk, oi);
        bu.setNotify(resolveNotify(input));
        Change.Id changeId = Change.id(seq.nextChangeId());
        ChangeInserter ins = changeInserterFactory.create(changeId, commit,
            destBranchRefName);
// DO NOT SUBMIT - should handle conflicts
        //        if (workInProgress != null) {
//          ins.setWorkInProgress(workInProgress);
//        } else {
//          ins.setWorkInProgress(
//              (sourceChange != null && sourceChange.isWorkInProgress())
//                  || !cherryPickCommit.getFilesWithGitConflicts().isEmpty());
//        }
        ins.setValidationOptions(getValidateOptionsAsMultimap(input.validationOptions));
        ins.setMessage("Uploaded patch set 1.")
            .setTopic(input.topic);
        // If there is a base, and the base is not merged, the groups will be overridden by the base's
        // groups.
        ins.setGroups(GroupCollector.getDefaultGroups(commit.getId()));
        if (input.base != null) {
          List<ChangeData> changes =
              queryProvider.get().setLimit(2)
                  .byBranchCommitOpen(project.get(), destBranchRefName, input.base);
          if (changes.size() > 1) {
            throw new InvalidChangeOperationException(
                "Several changes with key "
                    + input.base
                    + " reside on the same branch. "
                    + "Cannot cherry-pick on target branch.");
          }
          if (changes.size() == 1) {
            Change change = changes.get(0).change();
            ins.setGroups(changeNotesFactory.createChecked(change).getCurrentPatchSet().groups());
          }
        }
        bu.insertChange(ins);
        bu.execute();
        ChangeInfo changeInfo =
            json.noOptions().format(rsrc.getProject(), changeId);
        changeInfo.containsGitConflicts =
            !commit.getFilesWithGitConflicts().isEmpty() ? true : null;
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
    return null;
  }

  private RevCommit getBaseCommit(Ref destRef, String project, RevWalk revWalk, String base)
      throws RestApiException, IOException {
    RevCommit destRefTip = revWalk.parseCommit(destRef.getObjectId());
    // The tip commit of the destination ref is the default base for the newly created change.
    if (Strings.isNullOrEmpty(base)) {
      return destRefTip;
    }

    ObjectId baseObjectId;
    try {
      baseObjectId = ObjectId.fromString(base);
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException(String.format("Base %s doesn't represent a valid SHA-1", base),
          e);
    }

    RevCommit baseCommit;
    try {
      baseCommit = revWalk.parseCommit(baseObjectId);
    } catch (MissingObjectException e) {
      throw new UnprocessableEntityException(
          String.format("Base %s doesn't exist", baseObjectId.name()), e);
    }

    InternalChangeQuery changeQuery = queryProvider.get();
    changeQuery.enforceVisibility(true);
    List<ChangeData> changeDatas = changeQuery.byBranchCommit(project, destRef.getName(), base);

    if (changeDatas.isEmpty()) {
      if (revWalk.isMergedInto(baseCommit, destRefTip)) {
        // The base commit is a merged commit with no change associated.
        return baseCommit;
      }
      throw new UnprocessableEntityException(
          String.format("Commit %s does not exist on branch %s", base, destRef.getName()));
    } else if (changeDatas.size() != 1) {
      throw new ResourceConflictException("Multiple changes found for commit " + base);
    }

    Change change = changeDatas.get(0).change();
    if (!change.isAbandoned()) {
      // The base commit is a valid change revision.
      return baseCommit;
    }

    throw new ResourceConflictException(
        String.format("Change %s with commit %s is %s", change.getChangeId(), base,
            ChangeUtil.status(change)));
  }

  private static void matchAuthorToCommitterDate(ProjectState project, CommitBuilder commit) {
    if (project.is(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE)) {
      commit.setAuthor(new PersonIdent(commit.getAuthor(), commit.getCommitter().getWhen(),
          commit.getCommitter().getTimeZone()));
    }
  }

  private static ImmutableListMultimap<String, String> getValidateOptionsAsMultimap(
      @Nullable Map<String, String> validationOptions) {
    if (validationOptions == null) {
      return ImmutableListMultimap.of();
    }

    ImmutableListMultimap.Builder<String, String> validationOptionsBuilder =
        ImmutableListMultimap.builder();
    validationOptions
        .entrySet()
        .forEach(e -> validationOptionsBuilder.put(e.getKey(), e.getValue()));
    return validationOptionsBuilder.build();
  }

  private NotifyResolver.Result resolveNotify(ApplyPatchInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    return notifyResolver.resolve(
        firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);
  }
}
