// Copyright (C) 2019 The Android Open Source Project
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
import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevertSubmissionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.WalkSorter;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CherryPickChange.Result;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil psUtil;
  private final ContributorAgreementsChecker contributorAgreements;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final WalkSorter sorter;
  private final ChangeMessagesUtil cmUtil;
  private final CommitUtil commitUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeReverted changeReverted;
  private final RevertedSender.Factory revertedSenderFactory;
  private final Sequences seq;
  private final NotifyResolver notifyResolver;
  private final BatchUpdate.Factory updateFactory;

  private CherryPickInput cherryPickInput;
  private List<ChangeInfo> results;
  private static final Pattern patternRevertSubject = Pattern.compile("Revert \"(.+)\"");
  private static final Pattern patternRevertSubjectWithNum =
      Pattern.compile("Revert\\^(\\d+) \"(.+)\"");

  @Inject
  RevertSubmission(
      Provider<InternalChangeQuery> queryProvider,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil psUtil,
      ContributorAgreementsChecker contributorAgreements,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      WalkSorter sorter,
      ChangeMessagesUtil cmUtil,
      CommitUtil commitUtil,
      ChangeNotes.Factory changeNotesFactory,
      ChangeReverted changeReverted,
      RevertedSender.Factory revertedSenderFactory,
      Sequences seq,
      NotifyResolver notifyResolver,
      BatchUpdate.Factory updateFactory) {
    this.queryProvider = queryProvider;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
    this.repoManager = repoManager;
    this.sorter = sorter;
    this.cmUtil = cmUtil;
    this.commitUtil = commitUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.changeReverted = changeReverted;
    this.revertedSenderFactory = revertedSenderFactory;
    this.seq = seq;
    this.notifyResolver = notifyResolver;
    this.updateFactory = updateFactory;
    results = new ArrayList<>();
    cherryPickInput = null;
  }

  @Override
  public Response<RevertSubmissionInfo> apply(ChangeResource changeResource, RevertInput input)
      throws RestApiException, IOException, UpdateException, PermissionBackendException,
          NoSuchProjectException, ConfigInvalidException, StorageException {

    if (!changeResource.getChange().isMerged()) {
      throw new ResourceConflictException(
          String.format("change is %s.", ChangeUtil.status(changeResource.getChange())));
    }

    String submissionId = changeResource.getChange().getSubmissionId();
    if (submissionId == null) {
      throw new ResourceConflictException(
          "This change is merged but doesn't have a submission id,"
              + " meaning it was not submitted through Gerrit.");
    }
    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    checkPermissionsForAllChanges(changeResource, changeDatas);
    input.topic = createTopic(input.topic, submissionId);
    return Response.ok(revertSubmission(changeDatas, input));
  }

  private String createTopic(String topic, String submissionId) {
    if (topic != null) {
      topic = Strings.emptyToNull(topic.trim());
    }
    if (topic == null) {
      return String.format(
          "revert-%s-%s", submissionId, RandomStringUtils.randomAlphabetic(10).toUpperCase());
    }
    return topic;
  }

  private void checkPermissionsForAllChanges(
      ChangeResource changeResource, List<ChangeData> changeDatas)
      throws IOException, AuthException, PermissionBackendException, ResourceConflictException {
    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();

      // Might do the permission tests multiple times, but these are necessary to ensure that the
      // user has permissions to revert all changes. If they lack any permission, no revert will be
      // done.

      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.currentUser().ref(change.getDest()).check(CREATE_CHANGE);
      permissionBackend.currentUser().change(changeData).check(ChangePermission.READ);
      projectCache
          .get(change.getProject())
          .orElseThrow(illegalState(change.getProject()))
          .checkStatePermitsWrite();

      requireNonNull(
          psUtil.get(changeData.notes(), change.currentPatchSetId()),
          String.format(
              "current patch set %s of change %s not found",
              change.currentPatchSetId(), change.currentPatchSetId()));
    }
  }

  private RevertSubmissionInfo revertSubmission(
      List<ChangeData> changeData, RevertInput revertInput)
      throws RestApiException, IOException, UpdateException, ConfigInvalidException,
          StorageException {

    Multimap<BranchNameKey, ChangeData> changesPerProjectAndBranch = ArrayListMultimap.create();
    changeData.stream().forEach(c -> changesPerProjectAndBranch.put(c.change().getDest(), c));
    cherryPickInput = createCherryPickInput(revertInput);
    Timestamp timestamp = TimeUtil.nowTs();

    for (BranchNameKey projectAndBranch : changesPerProjectAndBranch.keySet()) {
      cherryPickInput.base = null;
      Project.NameKey project = projectAndBranch.project();
      cherryPickInput.destination = projectAndBranch.branch();
      Collection<ChangeData> changesInProjectAndBranch =
          changesPerProjectAndBranch.get(projectAndBranch);

      // Sort the changes topologically.
      Iterator<PatchSetData> sortedChangesInProjectAndBranch =
          sorter.sort(changesInProjectAndBranch).iterator();

      Set<ObjectId> commitIdsInProjectAndBranch =
          changesInProjectAndBranch.stream()
              .map(c -> c.currentPatchSet().commitId())
              .collect(Collectors.toSet());

      revertAllChangesInProjectAndBranch(
          revertInput,
          project,
          sortedChangesInProjectAndBranch,
          commitIdsInProjectAndBranch,
          timestamp);
    }
    results.sort(Comparator.comparing(c -> c.revertOf));
    RevertSubmissionInfo revertSubmissionInfo = new RevertSubmissionInfo();
    revertSubmissionInfo.revertChanges = results;
    return revertSubmissionInfo;
  }

  private void revertAllChangesInProjectAndBranch(
      RevertInput revertInput,
      Project.NameKey project,
      Iterator<PatchSetData> sortedChangesInProjectAndBranch,
      Set<ObjectId> commitIdsInProjectAndBranch,
      Timestamp timestamp)
      throws IOException, RestApiException, UpdateException, ConfigInvalidException {

    String groupName = null;
    String initialMessage = revertInput.message;
    while (sortedChangesInProjectAndBranch.hasNext()) {
      ChangeNotes changeNotes = sortedChangesInProjectAndBranch.next().data().notes();
      if (cherryPickInput.base == null) {
        cherryPickInput.base = getBase(changeNotes, commitIdsInProjectAndBranch).name();
      }

      revertInput.message = getMessage(initialMessage, changeNotes);
      if (cherryPickInput.base.equals(changeNotes.getCurrentPatchSet().commitId().getName())) {
        // This is the code in case this is the first revert of this project + branch, and the
        // revert would be on top of the change being reverted.
        craeteNormalRevert(revertInput, changeNotes, timestamp);
        groupName = cherryPickInput.base;
      } else {
        // This is the code in case this is the second revert (or more) of this project + branch.
        if (groupName == null) {
          groupName = cherryPickInput.base;
        }
        createCherryPickedRevert(revertInput, project, groupName, changeNotes, timestamp);
      }
    }
  }

  private void createCherryPickedRevert(
      RevertInput revertInput,
      Project.NameKey project,
      String groupName,
      ChangeNotes changeNotes,
      Timestamp timestamp)
      throws IOException, ConfigInvalidException, UpdateException, RestApiException {
    ObjectId revCommitId =
        commitUtil.createRevertCommit(revertInput.message, changeNotes, user.get(), timestamp);
    // TODO (paiking): As a future change, the revert should just be done directly on the
    // target rather than just creating a commit and then cherry-picking it.
    cherryPickInput.message = revertInput.message;
    ObjectId generatedChangeId = CommitMessageUtil.generateChangeId();
    Change.Id cherryPickRevertChangeId = Change.id(seq.nextChangeId());
    try (BatchUpdate bu = updateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.setNotify(
          notifyResolver.resolve(
              firstNonNull(cherryPickInput.notify, NotifyHandling.ALL),
              cherryPickInput.notifyDetails));
      bu.addOp(
          changeNotes.getChange().getId(),
          new CreateCherryPickOp(
              revCommitId, generatedChangeId, cherryPickRevertChangeId, groupName, timestamp));
      bu.addOp(changeNotes.getChange().getId(), new PostRevertedMessageOp(generatedChangeId));
      bu.addOp(
          cherryPickRevertChangeId,
          new NotifyOp(changeNotes.getChange(), cherryPickRevertChangeId));

      bu.execute();
    }
  }

  private void craeteNormalRevert(
      RevertInput revertInput, ChangeNotes changeNotes, Timestamp timestamp)
      throws IOException, RestApiException, UpdateException, ConfigInvalidException {

    Change.Id revertId =
        commitUtil.createRevertChange(changeNotes, user.get(), revertInput, timestamp);
    results.add(json.noOptions().format(changeNotes.getProjectName(), revertId));
    cherryPickInput.base =
        changeNotesFactory.createChecked(revertId).getCurrentPatchSet().commitId().getName();
  }

  private CherryPickInput createCherryPickInput(RevertInput revertInput) {
    cherryPickInput = new CherryPickInput();
    // To create a revert change, we create a revert commit that is then cherry-picked. The revert
    // change is created for the cherry-picked commit. Notifications are sent only for this change,
    // but not for the intermediately created revert commit.
    cherryPickInput.notify = revertInput.notify;
    cherryPickInput.notifyDetails = revertInput.notifyDetails;
    cherryPickInput.parent = 1;
    cherryPickInput.keepReviewers = true;
    cherryPickInput.topic = revertInput.topic;
    return cherryPickInput;
  }

  private String getMessage(String initialMessage, ChangeNotes changeNotes) {
    String subject = changeNotes.getChange().getSubject();
    if (subject.length() > 60) {
      subject = subject.substring(0, 56) + "...";
    }
    if (initialMessage == null) {
      initialMessage =
          MessageFormat.format(
              ChangeMessages.get().revertSubmissionDefaultMessage,
              changeNotes.getCurrentPatchSet().commitId().name());
    }

    // For performance purposes: Almost all cases will end here.
    if (!subject.startsWith("Revert")) {
      return MessageFormat.format(
          ChangeMessages.get().revertSubmissionUserMessage, subject, initialMessage);
    }

    Matcher matcher = patternRevertSubjectWithNum.matcher(subject);

    if (matcher.matches()) {
      return MessageFormat.format(
          ChangeMessages.get().revertSubmissionOfRevertSubmissionUserMessage,
          Integer.valueOf(matcher.group(1)) + 1,
          matcher.group(2),
          changeNotes.getCurrentPatchSet().commitId().name());
    }

    matcher = patternRevertSubject.matcher(subject);
    if (matcher.matches()) {
      return MessageFormat.format(
          ChangeMessages.get().revertSubmissionOfRevertSubmissionUserMessage,
          2,
          matcher.group(1),
          changeNotes.getCurrentPatchSet().commitId().name());
    }

    return MessageFormat.format(
        ChangeMessages.get().revertSubmissionUserMessage, subject, initialMessage);
  }

  /**
   * This function finds the base that the first revert in a project + branch should be based on. It
   * searches using BFS for the first commit that is either: 1. Has 2 or more parents, and has as
   * parents at least one commit that is part of the submission. 2. A commit that is part of the
   * submission. If neither of those are true, it just continues the search by going to the parents.
   *
   * <p>If 1 is true, if all the parents are part of the submission, it just returns that commit. If
   * only some of them are in the submission, the function changes and starts checking only the
   * commits that are not part of the submission. If all of them are part of the submission (or they
   * are also merge commits that have as parents only other merge commits, or other changes that are
   * part of the submission), we will return possibleMergeCommitToReturn which is the original
   * commit we started with when 1 was determined to be true.
   *
   * <p>If 2 is true, it will return the commit that WalkSorter has decided that it should be the
   * first commit reverted (e.g changeNotes, which is also the commit that is the first in the
   * topological sorting). Unless possibleMergeCommitToReturn is not null, which means we already
   * encountered a merge commit with a part of the submission earlier, which means we should return
   * that merge commit.
   *
   * <p>It doesn't run through the entire graph since it will stop once it finds at least one commit
   * that is part of the submission.
   *
   * @param changeNotes changeNotes for the change that is found by WalkSorter to be the first one
   *     that should be reverted, the first in the topological sorting.
   * @param commitIds The commitIds of this project and branch.
   * @return the base of the first revert.
   */
  private ObjectId getBase(ChangeNotes changeNotes, Set<ObjectId> commitIds)
      throws StorageException, IOException {
    try (Repository git = repoManager.openRepository(changeNotes.getProjectName());
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {

      ObjectId startCommit =
          git.getRefDatabase().findRef(changeNotes.getChange().getDest().branch()).getObjectId();
      Queue<ObjectId> commitsToSearch = new ArrayDeque<>();
      commitsToSearch.add(startCommit);

      while (!commitsToSearch.isEmpty()) {

        RevCommit revCommit = revWalk.parseCommit(commitsToSearch.poll());
        if (commitIds.contains(revCommit.getId())) {
          return changeNotes.getCurrentPatchSet().commitId();
        }
        if (revCommit.getParentCount() > 1) {
          List<RevCommit> parentsInSubmission =
              Arrays.stream(revCommit.getParents())
                  .filter(parent -> commitIds.contains(parent.getId()))
                  .collect(Collectors.toList());
          if (parentsInSubmission.size() > 1) {
            // Found a merge commit that has multiple parent commits that are part of the
            // submission.
            return revCommit.getId();
          }
          if (!parentsInSubmission.isEmpty()) {
            // Found a merge commit that has only one parent in this submission, but also other
            // parents not in the submission. Now we need to check if the others are merge commits
            // that have as parents only other merge commits, or other changes from the
            // submission.
            commitsToSearch.clear();
            commitsToSearch.addAll(
                Arrays.stream(revCommit.getParents())
                    .filter(parent -> !commitIds.contains(parent.getId()))
                    .collect(Collectors.toList()));

            if (isMergeCommitDescendantOfAllChangesInTheProjectAndBranchOfTheSubmission(
                commitsToSearch, commitIds, revWalk, revCommit, changeNotes)) {
              // Found a second commit of that submission that share the same merge commit.
              return revCommit.getId();
            }
            // Couldn't find a second commit of that submission that share the same merge commit.
            return changeNotes.getCurrentPatchSet().commitId();
          }
        }
        commitsToSearch.addAll(Arrays.asList(revCommit.getParents()));
      }
      // This should never happen since it can only happen if we go through the entire repository
      // without finding a single commit that matches any commit from the submission.
      throw new StorageException(
          String.format(
              "Couldn't find change %s in the repository %s",
              changeNotes.getChangeId(), changeNotes.getProjectName().get()));
    }
  }

  /**
   * This helper function calculates whether or not there exists a second commit that is part of the
   * submission and ancestor of the same merge commit.
   *
   * @param commitsToSearch Starts as all the parents of the potential merge commit, except the one
   *     that is part of the submission.
   * @param commitIds The commitIds of this project and branch.
   * @param revWalk Used for walking through the revisions.
   * @param potentialCommitToReturn The merge commit that is potentially a descendant of all changes
   *     in the project and branch of the submission.
   * @param changeNotes changeNotes for the change that is found by WalkSorter to be the first one
   *     that should be reverted, the first in the topological sorting.
   * @return True if found another commit of this submission, false if not found.
   */
  private boolean isMergeCommitDescendantOfAllChangesInTheProjectAndBranchOfTheSubmission(
      Queue<ObjectId> commitsToSearch,
      Set<ObjectId> commitIds,
      RevWalk revWalk,
      RevCommit potentialCommitToReturn,
      ChangeNotes changeNotes)
      throws StorageException, IOException {
    while (!commitsToSearch.isEmpty()) {
      RevCommit revCommit = revWalk.parseCommit(commitsToSearch.poll());
      if (revCommit.getParentCount() > 1) {
        List<RevCommit> parents = Arrays.asList(revCommit.getParents());
        if (parents.stream().anyMatch(p -> commitIds.contains(p.getId()))) {
          // found another commit with a common descendant.
          return true;
        }
        Arrays.asList(revCommit.getParents()).forEach(parent -> commitsToSearch.add(parent));
      } else {
        // We found a merge commit, we found that one of the parents is not a merge commit nor a
        // change of this submission. Therefore the merge commit is not useful, and we just
        // rebase on the most recent revert as usual.
        return false;
      }
    }
    // This should never happen since it can only happen if we go through the entire repository
    // encountering only merge commits after encountering the change whose submission we are
    // reverting.
    throw new StorageException(
        String.format(
            "Couldn't find a non-merge commit after encountering commit %s when trying to revert"
                + " the submission of change %d",
            potentialCommitToReturn.getName(), changeNotes.getChange().getChangeId()));
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite =
          projectCache.get(rsrc.getProject()).map(ProjectState::statePermitsWrite).orElse(false);
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel("Revert submission")
        .setTitle(
            "Revert this change and all changes that have been submitted together with this change")
        .setVisible(
            and(
                change.isMerged()
                    && change.getSubmissionId() != null
                    && isChangePartOfSubmission(change.getSubmissionId())
                    && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }

  /**
   * @param submissionId the submission id of the change.
   * @return True if the submission has more than one change, false otherwise.
   */
  private Boolean isChangePartOfSubmission(String submissionId) {
    return (queryProvider.get().setLimit(2).bySubmissionId(submissionId).size() > 1);
  }

  private class CreateCherryPickOp implements BatchUpdateOp {
    private final ObjectId revCommitId;
    private final ObjectId computedChangeId;
    private final Change.Id cherryPickRevertChangeId;
    private final String groupName;
    private final Timestamp timestamp;

    CreateCherryPickOp(
        ObjectId revCommitId,
        ObjectId computedChangeId,
        Change.Id cherryPickRevertChangeId,
        String groupName,
        Timestamp timestamp) {
      this.revCommitId = revCommitId;
      this.computedChangeId = computedChangeId;
      this.cherryPickRevertChangeId = cherryPickRevertChangeId;
      this.groupName = groupName;
      this.timestamp = timestamp;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();
      Result cherryPickResult =
          cherryPickChange.cherryPick(
              change,
              change.getProject(),
              revCommitId,
              cherryPickInput,
              BranchNameKey.create(
                  change.getProject(), RefNames.fullName(cherryPickInput.destination)),
              true,
              timestamp,
              change.getId(),
              computedChangeId,
              cherryPickRevertChangeId,
              groupName);
      // save the commit as base for next cherryPick of that branch
      cherryPickInput.base =
          changeNotesFactory
              .createChecked(cherryPickResult.changeId())
              .getCurrentPatchSet()
              .commitId()
              .getName();
      results.add(json.noOptions().format(change.getProject(), cherryPickResult.changeId()));
      return true;
    }
  }

  private class NotifyOp implements BatchUpdateOp {
    private final Change change;
    private final Change.Id revertChangeId;

    NotifyOp(Change change, Change.Id revertChangeId) {
      this.change = change;
      this.revertChangeId = revertChangeId;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      changeReverted.fire(
          change, changeNotesFactory.createChecked(revertChangeId).getChange(), ctx.getWhen());
      try {
        RevertedSender cm = revertedSenderFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setNotify(ctx.getNotify(change.getId()));
        cm.send();
      } catch (Exception err) {
        logger.atSevere().withCause(err).log(
            "Cannot send email for revert change %s", change.getId());
      }
    }
  }

  /**
   * create a message that describes the revert if the cherry-pick is successful, and point the
   * revert of the change towards the cherry-pick. The cherry-pick is the updated change that acts
   * as "revert-of" the original change.
   */
  private class PostRevertedMessageOp implements BatchUpdateOp {
    private final ObjectId computedChangeId;

    PostRevertedMessageOp(ObjectId computedChangeId) {
      this.computedChangeId = computedChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();
      PatchSet.Id patchSetId = change.currentPatchSetId();
      ChangeMessage changeMessage =
          ChangeMessagesUtil.newMessage(
              ctx,
              "Created a revert of this change as I" + computedChangeId.getName(),
              ChangeMessagesUtil.TAG_REVERT);
      cmUtil.addChangeMessage(ctx.getUpdate(patchSetId), changeMessage);
      return true;
    }
  }
}
