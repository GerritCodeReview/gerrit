// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges changes in submission order into a single branch.
 * <p>
 * Branches are reduced to the minimum number of heads needed to merge
 * everything. This allows commits to be entered into the queue in any order
 * (such as ancestors before descendants) and only the most recent commit on any
 * line of development will be merged. All unmerged commits along a line of
 * development must be in the submission queue in order to merge the tip of that
 * line.
 * <p>
 * Conflicts are handled by discarding the entire line of development and
 * marking it as conflicting, even if an earlier commit along that same line can
 * be merged cleanly.
 */
public class MergeOp {
  public interface Factory {
    MergeOp create(Branch.NameKey branch);
  }

  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  /** Amount of time to wait between submit and checking for missing deps. */
  private static final long DEPENDENCY_DELAY =
      MILLISECONDS.convert(15, MINUTES);

  private static final long LOCK_FAILURE_RETRY_DELAY =
      MILLISECONDS.convert(15, SECONDS);

  private static final long MAX_SUBMIT_WINDOW =
      MILLISECONDS.convert(12, HOURS);

  private final AccountCache accountCache;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeHooks hooks;
  private final ChangeIndexer indexer;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final MergeQueue mergeQueue;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ProjectCache projectCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final RequestScopePropagator requestScopePropagator;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final TagCache tagCache;
  private final WorkQueue workQueue;

  private final String logPrefix;
  private final Branch.NameKey destBranch;
  private final ListMultimap<SubmitType, CodeReviewCommit> toMerge;
  private final List<CodeReviewCommit> potentiallyStillSubmittable;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private final List<Change> toUpdate;

  private ProjectState destProject;
  private ReviewDb db;
  private Repository repo;
  private RevWalk rw;
  private RevFlag canMergeFlag;
  private CodeReviewCommit branchTip;
  private MergeTip mergeTip;
  private ObjectInserter inserter;
  private PersonIdent refLogIdent;

  @Inject
  MergeOp(AccountCache accountCache,
      ApprovalsUtil approvalsUtil,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeData.Factory changeDataFactory,
      ChangeHooks hooks,
      ChangeIndexer indexer,
      ChangeMessagesUtil cmUtil,
      ChangeNotes.Factory notesFactory,
      ChangeUpdate.Factory updateFactory,
      GitReferenceUpdated gitRefUpdated,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      MergedSender.Factory mergedSenderFactory,
      MergeFailSender.Factory mergeFailSenderFactory,
      MergeQueue mergeQueue,
      MergeValidators.Factory mergeValidatorsFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      ProjectCache projectCache,
      Provider<InternalChangeQuery> queryProvider,
      RequestScopePropagator requestScopePropagator,
      SchemaFactory<ReviewDb> schemaFactory,
      SubmitStrategyFactory submitStrategyFactory,
      SubmoduleOp.Factory subOpFactory,
      TagCache tagCache,
      WorkQueue workQueue,
      @Assisted Branch.NameKey branch) {
    this.accountCache = accountCache;
    this.approvalsUtil = approvalsUtil;
    this.changeControlFactory = changeControlFactory;
    this.changeDataFactory = changeDataFactory;
    this.hooks = hooks;
    this.indexer = indexer;
    this.cmUtil = cmUtil;
    this.notesFactory = notesFactory;
    this.updateFactory = updateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.repoManager = repoManager;
    this.identifiedUserFactory = identifiedUserFactory;
    this.mergedSenderFactory = mergedSenderFactory;
    this.mergeFailSenderFactory = mergeFailSenderFactory;
    this.mergeQueue = mergeQueue;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.projectCache = projectCache;
    this.queryProvider = queryProvider;
    this.requestScopePropagator = requestScopePropagator;
    this.schemaFactory = schemaFactory;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.tagCache = tagCache;
    this.workQueue = workQueue;
    logPrefix = String.format("[%s@%s]: ", branch.toString(),
        ISODateTimeFormat.hourMinuteSecond().print(TimeUtil.nowMs()));
    destBranch = branch;
    toMerge = ArrayListMultimap.create();
    potentiallyStillSubmittable = new ArrayList<>();
    commits = new HashMap<>();
    toUpdate = Lists.newArrayList();
  }

  private void setDestProject() throws MergeException {
    destProject = projectCache.get(destBranch.getParentKey());
    if (destProject == null) {
      throw new MergeException("No such project: " + destBranch.getParentKey());
    }
  }

  private void openSchema() throws OrmException {
    if (db == null) {
      db = schemaFactory.open();
    }
  }

  public void merge()
      throws MergeException, NoSuchChangeException, IOException {
    logDebug("Beginning merge attempt on {}", destBranch);
    setDestProject();
    try {
      openSchema();
      openRepository();

      RefUpdate branchUpdate = openBranch();
      boolean reopen = false;

      ListMultimap<SubmitType, Change> toSubmit =
          validateChangeList(queryProvider.get().submitted(destBranch));
      ListMultimap<SubmitType, CodeReviewCommit> toMergeNextTurn =
          ArrayListMultimap.create();
      List<CodeReviewCommit> potentiallyStillSubmittableOnNextRun =
          new ArrayList<>();
      while (!toMerge.isEmpty()) {
        logDebug("Beginning merge iteration with {} left to merge",
            toMerge.size());
        toMergeNextTurn.clear();
        Set<SubmitType> submitTypes = new HashSet<>(toMerge.keySet());
        for (SubmitType submitType : submitTypes) {
          if (reopen) {
            logDebug("Reopening branch");
            branchUpdate = openBranch();
          }
          SubmitStrategy strategy = createStrategy(submitType);
          MergeTip mergeTip = preMerge(strategy, toMerge.get(submitType));
          RefUpdate update = updateBranch(strategy, branchUpdate);
          reopen = true;

          updateChangeStatus(toSubmit.get(submitType), mergeTip);
          updateSubscriptions(toSubmit.get(submitType));
          if (update != null) {
            fireRefUpdated(update);
          }

          for (Iterator<CodeReviewCommit> it =
              potentiallyStillSubmittable.iterator(); it.hasNext();) {
            CodeReviewCommit commit = it.next();
            if (containsMissingCommits(toMerge, commit)
                || containsMissingCommits(toMergeNextTurn, commit)) {
              // change has missing dependencies, but all commits which are
              // missing are still attempted to be merged with another submit
              // strategy, retry to merge this commit in the next turn
              logDebug("Revision {} of patch set {} has missing dependencies"
                  + " with different submit types, reconsidering on next run",
                  commit.name(), commit.getPatchsetId());
              it.remove();
              commit.setStatusCode(null);
              commit.missing = null;
              toMergeNextTurn.put(submitType, commit);
            }
          }
          logDebug("Adding {} changes potentially submittable on next run",
              potentiallyStillSubmittable.size());
          potentiallyStillSubmittableOnNextRun.addAll(
              potentiallyStillSubmittable);
          potentiallyStillSubmittable.clear();
        }
        toMerge.clear();
        toMerge.putAll(toMergeNextTurn);
        logDebug("Adding {} changes to merge on next run", toMerge.size());
      }

      updateChangeStatus(toUpdate, mergeTip);

      for (CodeReviewCommit commit : potentiallyStillSubmittableOnNextRun) {
        Capable capable = isSubmitStillPossible(commit);
        if (capable != Capable.OK) {
          sendMergeFail(commit.notes(),
              message(commit.change(), capable.getMessage()), false);
        }
      }
    } catch (NoSuchProjectException noProject) {
      logWarn("Project " + destBranch.getParentKey() + " no longer exists,"
          + " abandoning open changes");
      abandonAllOpenChanges();
    } catch (OrmException e) {
      throw new MergeException("Cannot query the database", e);
    } finally {
      if (inserter != null) {
        inserter.release();
      }
      if (rw != null) {
        rw.release();
      }
      if (repo != null) {
        repo.close();
      }
      if (db != null) {
        db.close();
      }
    }
  }

  private boolean containsMissingCommits(
      ListMultimap<SubmitType, CodeReviewCommit> map, CodeReviewCommit commit) {
    if (!isSubmitForMissingCommitsStillPossible(commit)) {
      return false;
    }

    for (CodeReviewCommit missingCommit : commit.missing) {
      if (!map.containsValue(missingCommit)) {
        return false;
      }
    }
    return true;
  }

  private boolean isSubmitForMissingCommitsStillPossible(
      CodeReviewCommit commit) {
    PatchSet.Id psId = commit.getPatchsetId();
    if (commit.missing == null || commit.missing.isEmpty()) {
      logDebug("Patch set {} is not submittable: no list of missing commits",
          psId);
      return false;
    }

    for (CodeReviewCommit missingCommit : commit.missing) {
      try {
        loadChangeInfo(missingCommit);
      } catch (NoSuchChangeException | OrmException e) {
        logError("Cannot check if missing commits can be submitted", e);
        return false;
      }

      if (missingCommit.getPatchsetId() == null) {
        // The commit doesn't have a patch set, so it cannot be
        // submitted to the branch.
        //
        logDebug("Patch set {} is not submittable: dependency {} has no"
            + " associated patch set", psId, missingCommit.name());
        return false;
      }

      if (!missingCommit.change().currentPatchSetId().equals(
          missingCommit.getPatchsetId())) {
        PatchSet.Id missingId = missingCommit.getPatchsetId();
        // If the missing commit is not the current patch set,
        // the change must be rebased to use the proper parent.
        //
        logDebug("Patch set {} is not submittable: depends on patch set {} of"
            + " change {}, but current patch set is {}", psId, missingId,
            missingId.getParentKey(),
            missingCommit.change().currentPatchSetId());
        return false;
      }
    }

    return true;
  }

  private MergeTip preMerge(SubmitStrategy strategy,
      List<CodeReviewCommit> toMerge) throws MergeException {
    logDebug("Running submit strategy {} for {} commits",
        strategy.getClass().getSimpleName(), toMerge.size());
    mergeTip = strategy.run(branchTip, toMerge);
    refLogIdent = strategy.getRefLogIdent();
    logDebug("Produced {} new commits", strategy.getNewCommits().size());
    commits.putAll(strategy.getNewCommits());
    return mergeTip;
  }

  private SubmitStrategy createStrategy(SubmitType submitType)
      throws MergeException, NoSuchProjectException {
    return submitStrategyFactory.create(submitType, db, repo, rw, inserter,
        canMergeFlag, getAlreadyAccepted(branchTip), destBranch);
  }

  private void openRepository() throws MergeException, NoSuchProjectException {
    Project.NameKey name = destBranch.getParentKey();
    try {
      repo = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(name, notFound);
    } catch (IOException err) {
      String m = "Error opening repository \"" + name.get() + '"';
      throw new MergeException(m, err);
    }

    rw = CodeReviewCommit.newRevWalk(repo);
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.COMMIT_TIME_DESC, true);
    canMergeFlag = rw.newFlag("CAN_MERGE");

    inserter = repo.newObjectInserter();
  }

  private RefUpdate openBranch()
      throws MergeException, OrmException, NoSuchChangeException {
    try {
      RefUpdate branchUpdate = repo.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
      } else if (repo.getFullBranch().equals(destBranch.get())) {
        branchTip = null;
        branchUpdate.setExpectedOldObjectId(ObjectId.zeroId());
      } else {
        for (ChangeData cd : queryProvider.get().submitted(destBranch)) {
          try {
            Change c = cd.change();
            setNew(c, message(c, "Change could not be merged, "
                + "because the destination branch does not exist anymore."));
          } catch (OrmException e) {
            log.error("Error setting change new", e);
          }
        }
      }
      logDebug("Opened branch {}: {}", destBranch.get(), branchTip);
      return branchUpdate;
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private Set<RevCommit> getAlreadyAccepted(CodeReviewCommit branchTip)
      throws MergeException {
    Set<RevCommit> alreadyAccepted = new HashSet<>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (Ref r : repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
        try {
          alreadyAccepted.add(rw.parseCommit(r.getObjectId()));
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    } catch (IOException e) {
      throw new MergeException(
          "Failed to determine already accepted commits.", e);
    }

    logDebug("Found {} existing heads", alreadyAccepted.size());
    return alreadyAccepted;
  }

  private ListMultimap<SubmitType, Change> validateChangeList(
      List<ChangeData> submitted) throws MergeException {
    logDebug("Validating {} changes", submitted.size());
    ListMultimap<SubmitType, Change> toSubmit = ArrayListMultimap.create();

    Map<String, Ref> allRefs;
    try {
      allRefs = repo.getRefDatabase().getRefs(ALL);
    } catch (IOException e) {
      throw new MergeException(e.getMessage(), e);
    }

    Set<ObjectId> tips = new HashSet<>();
    for (Ref r : allRefs.values()) {
      tips.add(r.getObjectId());
    }

    for (ChangeData cd : submitted) {
      ChangeControl ctl;
      Change chg;
      try {
        ctl = cd.changeControl();
        // Reload change in case index was stale.
        chg = cd.reloadChange();
      } catch (OrmException e) {
        throw new MergeException("Failed to validate changes", e);
      }
      Change.Id changeId = cd.getId();
      if (chg.getStatus() != Change.Status.SUBMITTED) {
        logDebug("Change {} is not submitted: {}", changeId, chg.getStatus());
        continue;
      }
      if (chg.currentPatchSetId() == null) {
        logError("Missing current patch set on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      PatchSet ps;
      try {
        ps = cd.currentPatchSet();
      } catch (OrmException e) {
        throw new MergeException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        logError("Missing patch set or revision on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      String idstr = ps.getRevision().get();
      ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        logError("Invalid revision on patch set " + ps.getId());
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      if (!tips.contains(id)) {
        // TODO Technically the proper way to do this test is to use a
        // RevWalk on "$id --not --all" and test for an empty set. But
        // that is way slower than looking for a ref directly pointing
        // at the desired tip. We should always have a ref available.
        //
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.
        //
        logError("Revision " + idstr + " of patch set " + ps.getId()
            + " is not contained in any ref");
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        logError(
            "Invalid commit " + idstr + " on patch set " + ps.getId(), e);
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      // TODO(dborowitz): Consider putting ChangeData in CodeReviewCommit.
      commit.setControl(ctl);
      commit.setPatchsetId(ps.getId());
      commits.put(changeId, commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(
            repo, commit, destProject, destBranch, ps.getId());
      } catch (MergeValidationException mve) {
        logDebug("Revision {} of patch set {} failed validation: {}",
            idstr, ps.getId(), mve.getStatus());
        commit.setStatusCode(mve.getStatus());
        toUpdate.add(chg);
        continue;
      }

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. It's
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            logDebug("Revision {} of patch set {} is already merged",
                idstr, ps.getId());
            commit.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
            try {
              setMerged(chg, null, commit);
            } catch (OrmException e) {
              logError("Cannot mark change " + chg.getId() + " merged", e);
            }
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      SubmitType submitType;
      submitType = getSubmitType(commit.getControl(), ps);
      if (submitType == null) {
        logError("No submit type for revision " + idstr + " of patch set "
            + ps.getId());
        commit.setStatusCode(CommitMergeStatus.NO_SUBMIT_TYPE);
        toUpdate.add(chg);
        continue;
      }

      commit.add(canMergeFlag);
      toMerge.put(submitType, commit);
      toSubmit.put(submitType, chg);
    }
    logDebug("Submitting on this run: {}", toSubmit);
    return toSubmit;
  }

  private SubmitType getSubmitType(ChangeControl ctl, PatchSet ps) {
    try {
      ChangeData cd = changeDataFactory.create(db, ctl);
      SubmitTypeRecord r = new SubmitRuleEvaluator(cd).setPatchSet(ps)
          .getSubmitType();
      if (r.status != SubmitTypeRecord.Status.OK) {
        logError("Failed to get submit type for " + ctl.getChange().getKey());
        return null;
      }
      return r.type;
    } catch (OrmException e) {
      logError("Failed to get submit type for " + ctl.getChange().getKey(), e);
      return null;
    }
  }

  private RefUpdate updateBranch(SubmitStrategy strategy,
      RefUpdate branchUpdate) throws MergeException {
    CodeReviewCommit currentTip =
        mergeTip != null ? mergeTip.getCurrentTip() : null;
    if (Objects.equals(branchTip, currentTip)) {
      logDebug("Branch already at merge tip {}, no update to perform",
          currentTip.name());
      return null;
    } else if (currentTip == null) {
      logDebug("No merge tip, no update to perform");
      return null;
    }

    if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
      logDebug("Loading new configuration from {}", RefNames.REFS_CONFIG);
      try {
        ProjectConfig cfg =
            new ProjectConfig(destProject.getProject().getNameKey());
        cfg.load(repo, currentTip);
      } catch (Exception e) {
        throw new MergeException("Submit would store invalid"
            + " project configuration " + currentTip.name() + " for "
            + destProject.getProject().getName(), e);
      }
    }

    branchUpdate.setRefLogIdent(refLogIdent);
    branchUpdate.setForceUpdate(false);
    branchUpdate.setNewObjectId(currentTip);
    branchUpdate.setRefLogMessage("merged", true);
    try {
      RefUpdate.Result result = branchUpdate.update(rw);
      logDebug("Update of {}: {}..{} returned status {}",
          branchUpdate.getName(), branchUpdate.getOldObjectId(),
          branchUpdate.getNewObjectId(), result);
      switch (result) {
        case NEW:
        case FAST_FORWARD:
          if (branchUpdate.getResult() == RefUpdate.Result.FAST_FORWARD) {
            tagCache.updateFastForward(destBranch.getParentKey(),
                branchUpdate.getName(),
                branchUpdate.getOldObjectId(),
                currentTip);
          }

          if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
            Project p = destProject.getProject();
            projectCache.evict(p);
            destProject = projectCache.get(p.getNameKey());
            repoManager.setProjectDescription(
                p.getNameKey(), p.getDescription());
          }

          return branchUpdate;

        case LOCK_FAILURE:
          String msg;
          if (strategy.retryOnLockFailure()) {
            mergeQueue.recheckAfter(destBranch, LOCK_FAILURE_RETRY_DELAY,
                MILLISECONDS);
            msg = "will retry";
          } else {
            msg = "will not retry";
          }
          // TODO(dborowitz): Implement RefUpdate.toString().
          throw new IOException(branchUpdate.getResult().name() + ", " + msg
              + '\n' + branchUpdate);
        default:
          throw new IOException(branchUpdate.getResult().name()
              + '\n' + branchUpdate);
      }
    } catch (IOException e) {
      throw new MergeException("Cannot update " + branchUpdate.getName(), e);
    }
  }

  private void fireRefUpdated(RefUpdate branchUpdate) {
    logDebug("Firing ref updated hooks for {}", branchUpdate.getName());
    gitRefUpdated.fire(destBranch.getParentKey(), branchUpdate);
    hooks.doRefUpdatedHook(destBranch, branchUpdate,
        getAccount(mergeTip.getCurrentTip()));
  }

  private Account getAccount(CodeReviewCommit codeReviewCommit) {
    Account account = null;
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, codeReviewCommit.notes(), codeReviewCommit.getPatchsetId());
    if (submitter != null) {
      account = accountCache.get(submitter.getAccountId()).getAccount();
    }
    return account;
  }

  private String getByAccountName(CodeReviewCommit codeReviewCommit) {
    Account account = getAccount(codeReviewCommit);
    if (account != null && account.getFullName() != null) {
      return " by " + account.getFullName();
    }
    return "";
  }

  private void updateChangeStatus(List<Change> submitted, MergeTip mergeTip)
      throws NoSuchChangeException {
    logDebug("Updating change status for {} changes", submitted.size());
    for (Change c : submitted) {
      CodeReviewCommit commit = commits.get(c.getId());
      CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        logDebug("Submitted change {} did not appear in set of new commits"
            + " produced by merge strategy", c.getId());
        continue;
      }

      String txt = s.getMessage();
      logDebug("Status of change {} ({}) on {}: {}", c.getId(), commit.name(),
          c.getDest(), s);
      // If mergeTip is null merge failed and mergeResultRev will not be read.
      ObjectId mergeResultRev =
          mergeTip != null ? mergeTip.getMergeResults().get(commit) : null;
      try {
        switch (s) {
          case CLEAN_MERGE:
            setMerged(c, message(c, txt + getByAccountName(commit)),
                mergeResultRev);
            break;

          case CLEAN_REBASE:
          case CLEAN_PICK:
            setMerged(c, message(c, txt + " as " + commit.name()
                + getByAccountName(commit)), mergeResultRev);
            break;

          case ALREADY_MERGED:
            setMerged(c, null, mergeResultRev);
            break;

          case PATH_CONFLICT:
          case MANUAL_RECURSIVE_MERGE:
          case CANNOT_CHERRY_PICK_ROOT:
          case NOT_FAST_FORWARD:
          case INVALID_PROJECT_CONFIGURATION:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_PERMITTED:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_EDITABLE:
          case INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND:
          case INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT:
          case SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN:
            setNew(commit, message(c, txt));
            break;

          case MISSING_DEPENDENCY:
            logDebug("Change {} is missing dependency", c.getId());
            potentiallyStillSubmittable.add(commit);
            break;

          default:
            setNew(commit,
                message(c, "Unspecified merge failure: " + s.name()));
            break;
        }
      } catch (OrmException err) {
        logWarn("Error updating change status for " + c.getId(), err);
      } catch (IOException err) {
        logWarn("Error updating change status for " + c.getId(), err);
      }
    }
  }

  private void updateSubscriptions(List<Change> submitted) {
    if (mergeTip != null
        && (branchTip == null || branchTip != mergeTip.getCurrentTip())) {
      logDebug("Updating submodule subscriptions for {} changes",
          submitted.size());
      SubmoduleOp subOp =
          subOpFactory.create(destBranch, mergeTip.getCurrentTip(), rw, repo,
              destProject.getProject(), submitted, commits,
              getAccount(mergeTip.getCurrentTip()));
      try {
        subOp.update();
      } catch (SubmoduleException e) {
        logError(
            "The gitLinks were not updated according to the subscriptions" , e);
      }
    }
  }

  private Capable isSubmitStillPossible(CodeReviewCommit commit) {
    Capable capable;
    Change c = commit.change();
    boolean submitStillPossible =
        isSubmitForMissingCommitsStillPossible(commit);
    long now = TimeUtil.nowMs();
    long waitUntil = c.getLastUpdatedOn().getTime() + DEPENDENCY_DELAY;
    if (submitStillPossible && now < waitUntil) {
      long recheckIn = waitUntil - now;
      logDebug("Submit for {} is still possible; rechecking in {}ms",
          c.getId(), recheckIn);
      // If we waited a short while we might still be able to get
      // this change submitted. Reschedule an attempt in a bit.
      //
      mergeQueue.recheckAfter(destBranch, recheckIn, MILLISECONDS);
      capable = Capable.OK;
    } else if (submitStillPossible) {
      // It would be possible to submit the change if the missing
      // dependencies are also submitted. Perhaps the user just
      // forgot to submit those.
      //
      logDebug("Submit for {} is still possible after missing dependencies",
          c.getId());
      StringBuilder m = new StringBuilder();
      m.append("Change could not be merged because of a missing dependency.");
      m.append("\n");

      m.append("\n");

      m.append("The following changes must also be submitted:\n");
      m.append("\n");
      for (CodeReviewCommit missingCommit : commit.missing) {
        m.append("* ");
        m.append(missingCommit.change().getKey().get());
        m.append("\n");
      }
      capable = new Capable(m.toString());
    } else {
      // It is impossible to submit this change as-is. The author
      // needs to rebase it in order to work around the missing
      // dependencies.
      //
      logDebug("Submit for {} is not possible", c.getId());
      StringBuilder m = new StringBuilder();
      m.append("Change cannot be merged due to unsatisfiable dependencies.\n");
      m.append("\n");
      m.append("The following dependency errors were found:\n");
      m.append("\n");
      for (CodeReviewCommit missingCommit : commit.missing) {
        PatchSet.Id missingPsId = missingCommit.getPatchsetId();
        if (missingPsId != null) {
          m.append("* Depends on patch set ");
          m.append(missingPsId.get());
          m.append(" of ");
          m.append(missingCommit.change().getKey().abbreviate());
          PatchSet.Id currPsId = missingCommit.change().currentPatchSetId();
          if (!missingPsId.equals(currPsId)) {
            m.append(", however the current patch set is ");
            m.append(currPsId.get());
          }
          m.append(".\n");

        } else {
          m.append("* Depends on commit ");
          m.append(missingCommit.name());
          m.append(" which has no change associated with it.\n");
        }
      }
      m.append("\n");
      m.append("Please rebase the change and upload a replacement commit.");
      capable = new Capable(m.toString());
    }

    return capable;
  }

  private void loadChangeInfo(CodeReviewCommit commit)
      throws NoSuchChangeException, OrmException {
    if (commit.getControl() == null) {
      List<PatchSet> matches =
          db.patchSets().byRevision(new RevId(commit.name())).toList();
      if (matches.size() == 1) {
        PatchSet.Id psId = matches.get(0).getId();
        commit.setPatchsetId(psId);
        commit.setControl(changeControl(db.changes().get(psId.getParentKey())));
      }
    }
  }

  private ChangeMessage message(Change c, String body) {
    String uuid;
    try {
      uuid = ChangeUtil.messageUUID(db);
    } catch (OrmException e) {
      return null;
    }
    ChangeMessage m = new ChangeMessage(new ChangeMessage.Key(c.getId(), uuid),
        null, TimeUtil.nowTs(), c.currentPatchSetId());
    m.setMessage(body);
    return m;
  }

  private void setMerged(Change c, ChangeMessage msg, ObjectId mergeResultRev)
      throws OrmException, IOException {
    logDebug("Setting change {} merged", c.getId());
    ChangeUpdate update = null;
    PatchSetApproval submitter;
    PatchSet merged;
    try {
      db.changes().beginTransaction(c.getId());

      // We must pull the patchset out of commits, because the patchset ID is
      // modified when using the cherry-pick merge strategy.
      CodeReviewCommit commit = commits.get(c.getId());
      PatchSet.Id mergedId = commit.change().currentPatchSetId();
      merged = db.patchSets().get(mergedId);
      c = setMergedPatchSet(c.getId(), mergedId);
      submitter = approvalsUtil.getSubmitter(db, commit.notes(), mergedId);
      ChangeControl control = commit.getControl();
      update = updateFactory.create(control, c.getLastUpdatedOn());

      // TODO(yyonas): we need to be able to change the author of the message
      // is not the person for whom the change was made. addMergedMessage
      // did this in the past.
      if (msg != null) {
        cmUtil.addChangeMessage(db, update, msg);
      }
      db.commit();

    } finally {
      db.rollback();
    }
    update.commit();
    sendMergedEmail(c, submitter);
    indexer.index(db, c);
    // TODO: this fails
    if (submitter != null && mergeResultRev != null) {
      try {
        hooks.doChangeMergedHook(c,
            accountCache.get(submitter.getAccountId()).getAccount(),
            merged, db, mergeResultRev.name());
      } catch (OrmException ex) {
        logError("Cannot run hook for submitted patch set " + c.getId(), ex);
      }
    }
  }

  private Change setMergedPatchSet(Change.Id changeId, final PatchSet.Id merged)
      throws OrmException {
    return db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change c) {
        c.setStatus(Change.Status.MERGED);
        if (!merged.equals(c.currentPatchSetId())) {
          // Uncool; the patch set changed after we merged it.
          // Go back to the patch set that was actually merged.
          //
          try {
            c.setCurrentPatchSet(patchSetInfoFactory.get(db, merged));
          } catch (PatchSetInfoNotAvailableException e1) {
            logError("Cannot read merged patch set " + merged, e1);
          }
        }
        ChangeUtil.updated(c);
        return c;
      }
    });
  }

  private void sendMergedEmail(final Change c, final PatchSetApproval from) {
    workQueue.getDefaultQueue()
        .submit(requestScopePropagator.wrap(new Runnable() {
      @Override
      public void run() {
        PatchSet patchSet;
        try {
          ReviewDb reviewDb = schemaFactory.open();
          try {
            patchSet = reviewDb.patchSets().get(c.currentPatchSetId());
          } finally {
            reviewDb.close();
          }
        } catch (Exception e) {
          logError("Cannot send email for submitted patch set " + c.getId(), e);
          return;
        }

        try {
          MergedSender cm = mergedSenderFactory.create(changeControl(c));
          if (from != null) {
            cm.setFrom(from.getAccountId());
          }
          cm.setPatchSet(patchSet);
          cm.send();
        } catch (Exception e) {
          logError("Cannot send email for submitted patch set " + c.getId(), e);
        }
      }

      @Override
      public String toString() {
        return "send-email merged";
      }
    }));
  }

  private ChangeControl changeControl(Change c) throws NoSuchChangeException {
    return changeControlFactory.controlFor(
        c, identifiedUserFactory.create(c.getOwner()));
  }

  private void setNew(CodeReviewCommit c, ChangeMessage msg)
      throws NoSuchChangeException, IOException {
    sendMergeFail(c.notes(), msg, true);
  }

  private void setNew(Change c, ChangeMessage msg)
      throws NoSuchChangeException, IOException {
    sendMergeFail(notesFactory.create(c), msg, true);
  }

  private enum RetryStatus {
    UNSUBMIT, RETRY_NO_MESSAGE, RETRY_ADD_MESSAGE
  }

  private RetryStatus getRetryStatus(
      @Nullable PatchSetApproval submitter,
      ChangeMessage msg,
      ChangeNotes notes) {
    Change.Id id = notes.getChangeId();
    if (submitter != null) {
      long sinceMs = TimeUtil.nowMs() - submitter.getGranted().getTime();
      if (sinceMs > MAX_SUBMIT_WINDOW) {
        logDebug("Change {} submitted {}ms ago, unsubmitting", id, sinceMs);
        return RetryStatus.UNSUBMIT;
      } else {
        logDebug("Change {} submitted {}ms ago, within window", id, sinceMs);
      }
    } else {
      logDebug("No submitter for change {}", id);
    }

    try {
      ChangeMessage last = Iterables.getLast(cmUtil.byChange(db, notes));
      if (last != null) {
        if (Objects.equals(last.getAuthor(), msg.getAuthor())
            && Objects.equals(last.getMessage(), msg.getMessage())) {
          long lastMs = last.getWrittenOn().getTime();
          long msgMs = msg.getWrittenOn().getTime();
          long sinceMs = msgMs - lastMs;
          if (sinceMs > MAX_SUBMIT_WINDOW) {
            logDebug("Last message for change {} was {}ms ago, unsubmitting",
                id, sinceMs);
            return RetryStatus.UNSUBMIT;
          } else {
            logDebug("Last message for change {} was {}ms ago, within window",
                id, sinceMs);
            return RetryStatus.RETRY_NO_MESSAGE;
          }
        } else {
          logDebug("Last message for change {} differed, adding message", id);
        }
      }
      return RetryStatus.RETRY_ADD_MESSAGE;
    } catch (OrmException err) {
      logWarn("Cannot check previous merge failure, unsubmitting", err);
      return RetryStatus.UNSUBMIT;
    }
  }

  private void sendMergeFail(ChangeNotes notes, final ChangeMessage msg,
      boolean makeNew) throws NoSuchChangeException, IOException {
    logDebug("Possibly sending merge failure notification for {}",
        notes.getChangeId());
    PatchSetApproval submitter = null;
    try {
      submitter = approvalsUtil.getSubmitter(
          db, notes, notes.getChange().currentPatchSetId());
    } catch (Exception e) {
      logError("Cannot get submitter for change " + notes.getChangeId(), e);
    }

    if (!makeNew) {
      RetryStatus retryStatus = getRetryStatus(submitter, msg, notes);
      if (retryStatus == RetryStatus.RETRY_NO_MESSAGE) {
        return;
      } else if (retryStatus == RetryStatus.UNSUBMIT) {
        makeNew = true;
      }
    }

    final boolean setStatusNew = makeNew;
    final Change c = notes.getChange();
    Change change = null;
    ChangeUpdate update = null;
    try {
      db.changes().beginTransaction(c.getId());
      try {
        change = db.changes().atomicUpdate(
            c.getId(),
            new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (c.getStatus().isOpen()) {
              if (setStatusNew) {
                c.setStatus(Change.Status.NEW);
              }
              ChangeUtil.updated(c);
            }
            return c;
          }
        });
        ChangeControl control = changeControl(change);

        //TODO(yyonas): atomic change is not propagated.
        update = updateFactory.create(control, c.getLastUpdatedOn());
        if (msg != null) {
          cmUtil.addChangeMessage(db, update, msg);
        }
        db.commit();
      } finally {
        db.rollback();
      }
    } catch (OrmException err) {
      logWarn("Cannot record merge failure message", err);
    }
    if (update != null) {
      update.commit();
    }

    CheckedFuture<?, IOException> indexFuture;
    if (change != null) {
      indexFuture = indexer.indexAsync(change.getId());
    } else {
      indexFuture = null;
    }
    final PatchSetApproval from = submitter;
    workQueue.getDefaultQueue()
        .submit(requestScopePropagator.wrap(new Runnable() {
      @Override
      public void run() {
        PatchSet patchSet;
        try {
          ReviewDb reviewDb = schemaFactory.open();
          try {
            patchSet = reviewDb.patchSets().get(c.currentPatchSetId());
          } finally {
            reviewDb.close();
          }
        } catch (Exception e) {
          logError("Cannot send email notifications about merge failure", e);
          return;
        }

        try {
          MergeFailSender cm = mergeFailSenderFactory.create(c);
          if (from != null) {
            cm.setFrom(from.getAccountId());
          }
          cm.setPatchSet(patchSet);
          cm.setChangeMessage(msg);
          cm.send();
        } catch (Exception e) {
          logError("Cannot send email notifications about merge failure", e);
        }
      }

      @Override
      public String toString() {
        return "send-email merge-failed";
      }
    }));

    if (indexFuture != null) {
      try {
        indexFuture.checkedGet();
      } catch (IOException e) {
        logError("Failed to index new change message", e);
      }
    }

    if (submitter != null) {
      try {
        hooks.doMergeFailedHook(c,
            accountCache.get(submitter.getAccountId()).getAccount(),
            db.patchSets().get(c.currentPatchSetId()), msg.getMessage(), db);
      } catch (OrmException ex) {
        logError("Cannot run hook for merge failed " + c.getId(), ex);
      }
    }
  }

  private void abandonAllOpenChanges() throws NoSuchChangeException {
    Exception err = null;
    try {
      openSchema();
      for (ChangeData cd
          : queryProvider.get().byProjectOpen(destBranch.getParentKey())) {
        abandonOneChange(cd.change());
      }
      db.close();
      db = null;
    } catch (IOException e) {
      err = e;
    } catch (OrmException e) {
      err = e;
    }
    if (err != null) {
      logWarn("Cannot abandon changes for deleted project "
          + destBranch.getParentKey().get(), err);
    }
  }

  private void abandonOneChange(Change change) throws OrmException,
    NoSuchChangeException,  IOException {
    db.changes().beginTransaction(change.getId());

    //TODO(dborowitz): support InternalUser in ChangeUpdate
    ChangeControl control = changeControlFactory.controlFor(change,
        identifiedUserFactory.create(change.getOwner()));
    ChangeUpdate update = updateFactory.create(control);
    try {
      change = db.changes().atomicUpdate(
        change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.setStatus(Change.Status.ABANDONED);
              return change;
            }
            return null;
          }
        });

      if (change != null) {
        ChangeMessage msg = new ChangeMessage(
            new ChangeMessage.Key(
                change.getId(),
                ChangeUtil.messageUUID(db)),
            null,
            change.getLastUpdatedOn(),
            change.currentPatchSetId());
        msg.setMessage("Project was deleted.");

        //TODO(yyonas): atomic change is not propagated.
        cmUtil.addChangeMessage(db, update, msg);
        db.commit();
        indexer.index(db, change);
      }
    } finally {
      db.rollback();
    }
    update.commit();
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(logPrefix + msg, args);
    }
  }

  private void logWarn(String msg, Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn(logPrefix + msg, t);
    }
  }

  private void logWarn(String msg) {
    if (log.isWarnEnabled()) {
      log.warn(logPrefix + msg);
    }
  }

  private void logError(String msg, Throwable t) {
    if (log.isErrorEnabled()) {
      log.error(logPrefix + msg, t);
    }
  }

  private void logError(String msg) {
    if (log.isErrorEnabled()) {
      log.error(logPrefix + msg);
    }
  }
}
