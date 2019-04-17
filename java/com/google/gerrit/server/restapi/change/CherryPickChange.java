// Copyright (C) 2012 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
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
public class CherryPickChange {
  @AutoValue
  abstract static class Result {
    static Result create(Change.Id changeId, ImmutableSet<String> filesWithGitConflicts) {
      return new AutoValue_CherryPickChange_Result(changeId, filesWithGitConflicts);
    }

    abstract Change.Id changeId();

    abstract ImmutableSet<String> filesWithGitConflicts();
  }

  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<IdentifiedUser> user;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;
  private final NotifyResolver notifyResolver;

  @Inject
  CherryPickChange(
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<IdentifiedUser> user,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory changeNotesFactory,
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil,
      NotifyResolver notifyResolver) {
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
    this.notifyResolver = notifyResolver;
  }

  public Result cherryPick(
      BatchUpdate.Factory batchUpdateFactory,
      Change change,
      PatchSet patch,
      CherryPickInput input,
      Branch.NameKey dest)
      throws IOException, InvalidChangeOperationException, IntegrationException, UpdateException,
          RestApiException, ConfigInvalidException, NoSuchProjectException {
    return cherryPick(
        batchUpdateFactory,
        change,
        change.getProject(),
        ObjectId.fromString(patch.getRevision().get()),
        input,
        dest);
  }

  public Result cherryPick(
      BatchUpdate.Factory batchUpdateFactory,
      @Nullable Change sourceChange,
      Project.NameKey project,
      ObjectId sourceCommit,
      CherryPickInput input,
      Branch.NameKey dest)
      throws IOException, InvalidChangeOperationException, IntegrationException, UpdateException,
          RestApiException, ConfigInvalidException, NoSuchProjectException {

    IdentifiedUser identifiedUser = user.get();
    try (Repository git = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the cherry-picked commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      Ref destRef = git.getRefDatabase().exactRef(dest.branch());
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", dest.branch()));
      }

      RevCommit baseCommit = getBaseCommit(destRef, project.get(), revWalk, input.base);

      CodeReviewCommit commitToCherryPick = revWalk.parseCommit(sourceCommit);

      if (input.parent <= 0 || input.parent > commitToCherryPick.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                input.parent, commitToCherryPick.getParentCount()));
      }

      Timestamp now = TimeUtil.nowTs();
      PersonIdent committerIdent = identifiedUser.newCommitterIdent(now, serverTimeZone);

      final ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(
              commitToCherryPick.getTree(),
              baseCommit,
              commitToCherryPick.getAuthorIdent(),
              committerIdent,
              input.message);
      String commitMessage = ChangeIdUtil.insertId(input.message, computedChangeId).trim() + '\n';

      CodeReviewCommit cherryPickCommit;
      ProjectState projectState = projectCache.checkedGet(dest.project());
      if (projectState == null) {
        throw new NoSuchProjectException(dest.project());
      }
      try {
        MergeUtil mergeUtil;
        if (input.allowConflicts) {
          // allowConflicts requires to use content merge
          mergeUtil = mergeUtilFactory.create(projectState, true);
        } else {
          // use content merge only if it's configured on the project
          mergeUtil = mergeUtilFactory.create(projectState);
        }
        cherryPickCommit =
            mergeUtil.createCherryPickFromCommit(
                oi,
                git.getConfig(),
                baseCommit,
                commitToCherryPick,
                committerIdent,
                commitMessage,
                revWalk,
                input.parent - 1,
                false,
                input.allowConflicts);

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        Branch.NameKey newDest = Branch.nameKey(project, destRef.getName());
        List<ChangeData> destChanges =
            queryProvider.get().setLimit(2).byBranchKey(newDest, changeKey);
        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException(
              "Several changes with key "
                  + changeKey
                  + " reside on the same branch. "
                  + "Cannot create a new patch set.");
        }
        try (BatchUpdate bu = batchUpdateFactory.create(project, identifiedUser, now)) {
          bu.setRepository(git, revWalk, oi);
          bu.setNotify(resolveNotify(input));
          Change.Id changeId;
          if (destChanges.size() == 1) {
            // The change key exists on the destination branch. The cherry pick
            // will be added as a new patch set.
            changeId = insertPatchSet(bu, git, destChanges.get(0).notes(), cherryPickCommit);
          } else {
            // Change key not found on destination branch. We can create a new
            // change.
            String newTopic = null;
            if (sourceChange != null && !Strings.isNullOrEmpty(sourceChange.getTopic())) {
              newTopic = sourceChange.getTopic() + "-" + newDest.shortName();
            }
            changeId =
                createNewChange(
                    bu,
                    cherryPickCommit,
                    dest.branch(),
                    newTopic,
                    sourceChange,
                    sourceCommit,
                    input);
          }
          bu.execute();
          return Result.create(changeId, cherryPickCommit.getFilesWithGitConflicts());
        }
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationException("Cherry pick failed: " + e.getMessage());
      }
    }
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
      throw new BadRequestException(String.format("Base %s doesn't represent a valid SHA-1", base));
    }

    RevCommit baseCommit = revWalk.parseCommit(baseObjectId);
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
        String.format(
            "Change %s with commit %s is %s",
            change.getChangeId(), base, ChangeUtil.status(change)));
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu, Repository git, ChangeNotes destNotes, CodeReviewCommit cherryPickCommit)
      throws IOException {
    Change destChange = destNotes.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, cherryPickCommit);
    inserter.setMessage("Uploaded patch set " + inserter.getPatchSetId().get() + ".");
    bu.addOp(destChange.getId(), inserter);
    return destChange.getId();
  }

  private Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit cherryPickCommit,
      String refName,
      String topic,
      @Nullable Change sourceChange,
      ObjectId sourceCommit,
      CherryPickInput input)
      throws IOException {
    Change.Id changeId = new Change.Id(seq.nextChangeId());
    ChangeInserter ins = changeInserterFactory.create(changeId, cherryPickCommit, refName);
    Branch.NameKey sourceBranch = sourceChange == null ? null : sourceChange.getDest();
    ins.setMessage(
            messageForDestinationChange(
                ins.getPatchSetId(), sourceBranch, sourceCommit, cherryPickCommit))
        .setTopic(topic)
        .setWorkInProgress(
            (sourceChange != null && sourceChange.isWorkInProgress())
                || !cherryPickCommit.getFilesWithGitConflicts().isEmpty());
    if (input.keepReviewers && sourceChange != null) {
      ReviewerSet reviewerSet =
          approvalsUtil.getReviewers(changeNotesFactory.createChecked(sourceChange));
      Set<Account.Id> reviewers =
          new HashSet<>(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
      reviewers.add(sourceChange.getOwner());
      reviewers.remove(user.get().getAccountId());
      Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
      ccs.remove(user.get().getAccountId());
      ins.setReviewersAndCcs(reviewers, ccs);
    }
    bu.insertChange(ins);
    return changeId;
  }

  private NotifyResolver.Result resolveNotify(CherryPickInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    return notifyResolver.resolve(
        firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);
  }

  private String messageForDestinationChange(
      PatchSet.Id patchSetId,
      Branch.NameKey sourceBranch,
      ObjectId sourceCommit,
      CodeReviewCommit cherryPickCommit) {
    StringBuilder stringBuilder = new StringBuilder("Patch Set ").append(patchSetId.get());
    if (sourceBranch != null) {
      stringBuilder.append(": Cherry Picked from branch ").append(sourceBranch.shortName());
    } else {
      stringBuilder.append(": Cherry Picked from commit ").append(sourceCommit.getName());
    }
    stringBuilder.append(".");

    if (!cherryPickCommit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:");
      cherryPickCommit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("\n* ").append(filePath));
    }

    return stringBuilder.toString();
  }
}
