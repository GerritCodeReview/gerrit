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

import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
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
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ProjectCache projectCache;
  private final GitReferenceUpdated gitRefUpdated;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final MergeQueue mergeQueue;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final ApprovalsUtil approvalsUtil;

  private final Branch.NameKey destBranch;
  private ProjectState destProject;
  private final ListMultimap<SubmitType, CodeReviewCommit> toMerge;
  private final List<CodeReviewCommit> potentiallyStillSubmittable;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private final List<Change> toUpdate;
  private ReviewDb db;
  private Repository repo;
  private RevWalk rw;
  private RevFlag canMergeFlag;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private ObjectInserter inserter;
  private PersonIdent refLogIdent;

  private final ChangeHooks hooks;
  private final AccountCache accountCache;
  private final TagCache tagCache;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final WorkQueue workQueue;
  private final RequestScopePropagator requestScopePropagator;
  private final ChangeIndexer indexer;

  @Inject
  MergeOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ChangeNotes.Factory nf,
      final ProjectCache pc,
      final GitReferenceUpdated gru, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf,
      final PatchSetInfoFactory psif, final IdentifiedUser.GenericFactory iuf,
      final ChangeControl.GenericFactory changeControlFactory,
      final MergeQueue mergeQueue, @Assisted final Branch.NameKey branch,
      final ChangeHooks hooks, final AccountCache accountCache,
      final TagCache tagCache,
      final SubmitStrategyFactory submitStrategyFactory,
      final SubmoduleOp.Factory subOpFactory,
      final WorkQueue workQueue,
      final RequestScopePropagator requestScopePropagator,
      final ChangeIndexer indexer,
      final MergeValidators.Factory mergeValidatorsFactory,
      final ApprovalsUtil approvalsUtil) {
    repoManager = grm;
    schemaFactory = sf;
    notesFactory = nf;
    projectCache = pc;
    gitRefUpdated = gru;
    mergedSenderFactory = msf;
    mergeFailSenderFactory = mfsf;
    patchSetInfoFactory = psif;
    identifiedUserFactory = iuf;
    this.changeControlFactory = changeControlFactory;
    this.mergeQueue = mergeQueue;
    this.hooks = hooks;
    this.accountCache = accountCache;
    this.tagCache = tagCache;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.workQueue = workQueue;
    this.requestScopePropagator = requestScopePropagator;
    this.indexer = indexer;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.approvalsUtil = approvalsUtil;
    destBranch = branch;
    toMerge = ArrayListMultimap.create();
    potentiallyStillSubmittable = new ArrayList<CodeReviewCommit>();
    commits = new HashMap<Change.Id, CodeReviewCommit>();
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

  public void merge() throws MergeException {
    setDestProject();
    try {
      openSchema();
      openRepository();

      RefUpdate branchUpdate = openBranch();
      boolean reopen = false;

      final ListMultimap<SubmitType, Change> toSubmit =
          validateChangeList(db.changes().submitted(destBranch).toList());
      final ListMultimap<SubmitType, CodeReviewCommit> toMergeNextTurn =
          ArrayListMultimap.create();
      final List<CodeReviewCommit> potentiallyStillSubmittableOnNextRun =
          new ArrayList<CodeReviewCommit>();
      while (!toMerge.isEmpty()) {
        toMergeNextTurn.clear();
        final Set<SubmitType> submitTypes =
            new HashSet<Project.SubmitType>(toMerge.keySet());
        for (final SubmitType submitType : submitTypes) {
          if (reopen) {
            branchUpdate = openBranch();
          }
          final SubmitStrategy strategy = createStrategy(submitType);
          preMerge(strategy, toMerge.get(submitType));
          RefUpdate update = updateBranch(strategy, branchUpdate);
          reopen = true;

          updateChangeStatus(toSubmit.get(submitType));
          updateSubscriptions(toSubmit.get(submitType));
          if (update != null) {
            fireRefUpdated(update);
          }

          for (final Iterator<CodeReviewCommit> it =
              potentiallyStillSubmittable.iterator(); it.hasNext();) {
            final CodeReviewCommit commit = it.next();
            if (containsMissingCommits(toMerge, commit)
                || containsMissingCommits(toMergeNextTurn, commit)) {
              // change has missing dependencies, but all commits which are
              // missing are still attempted to be merged with another submit
              // strategy, retry to merge this commit in the next turn
              it.remove();
              commit.setStatusCode(null);
              commit.missing = null;
              toMergeNextTurn.put(submitType, commit);
            }
          }
          potentiallyStillSubmittableOnNextRun.addAll(potentiallyStillSubmittable);
          potentiallyStillSubmittable.clear();
        }
        toMerge.clear();
        toMerge.putAll(toMergeNextTurn);
      }

      updateChangeStatus(toUpdate);

      for (final CodeReviewCommit commit : potentiallyStillSubmittableOnNextRun) {
        final Capable capable = isSubmitStillPossible(commit);
        if (capable != Capable.OK) {
          sendMergeFail(commit.notes(),
              message(commit.change(), capable.getMessage()), false);
        }
      }
    } catch (NoSuchProjectException noProject) {
      log.warn(String.format(
          "Project %s no longer exists, abandoning open changes",
          destBranch.getParentKey().get()));
      abandonAllOpenChanges();
    } catch (OrmException e) {
      throw new MergeException("Cannot query the database", e);
    } finally {
      if (inserter != null) {
        inserter.close();
      }
      if (rw != null) {
        rw.close();
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
      final ListMultimap<SubmitType, CodeReviewCommit> map,
      final CodeReviewCommit commit) {
    if (!isSubmitForMissingCommitsStillPossible(commit)) {
      return false;
    }

    for (final CodeReviewCommit missingCommit : commit.missing) {
      if (!map.containsValue(missingCommit)) {
        return false;
      }
    }
    return true;
  }

  private boolean isSubmitForMissingCommitsStillPossible(final CodeReviewCommit commit) {
    if (commit.missing == null || commit.missing.isEmpty()) {
      return false;
    }

    for (CodeReviewCommit missingCommit : commit.missing) {
      try {
        loadChangeInfo(missingCommit);
      } catch (NoSuchChangeException | OrmException e) {
        log.error("Cannot check if missing commits can be submitted", e);
        return false;
      }

      if (missingCommit.getPatchsetId() == null) {
        // The commit doesn't have a patch set, so it cannot be
        // submitted to the branch.
        //
        return false;
      }

      if (!missingCommit.change().currentPatchSetId().equals(
          missingCommit.getPatchsetId())) {
        // If the missing commit is not the current patch set,
        // the change must be rebased to use the proper parent.
        //
        return false;
      }
    }

    return true;
  }

  private void preMerge(final SubmitStrategy strategy,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    mergeTip = strategy.run(branchTip, toMerge);
    refLogIdent = strategy.getRefLogIdent();
    commits.putAll(strategy.getNewCommits());
  }

  private SubmitStrategy createStrategy(final SubmitType submitType)
      throws MergeException, NoSuchProjectException {
    return submitStrategyFactory.create(submitType, db, repo, rw, inserter,
        canMergeFlag, getAlreadyAccepted(branchTip), destBranch);
  }

  private void openRepository() throws MergeException, NoSuchProjectException {
    final Project.NameKey name = destBranch.getParentKey();
    try {
      repo = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(name, notFound);
    } catch (IOException err) {
      final String m = "Error opening repository \"" + name.get() + '"';
      throw new MergeException(m, err);
    }

    rw = new RevWalk(repo) {
      @Override
      protected RevCommit createCommit(final AnyObjectId id) {
        return new CodeReviewCommit(id);
      }
    };
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.COMMIT_TIME_DESC, true);
    canMergeFlag = rw.newFlag("CAN_MERGE");

    inserter = repo.newObjectInserter();
  }

  private RefUpdate openBranch() throws MergeException, OrmException {
    try {
      final RefUpdate branchUpdate = repo.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
      } else if (repo.getFullBranch().equals(destBranch.get())) {
        branchTip = null;
        branchUpdate.setExpectedOldObjectId(ObjectId.zeroId());
      } else {
        for (final Change c : db.changes().submitted(destBranch).toList()) {
          setNew(c, message(c, "Your change could not be merged, "
              + "because the destination branch does not exist anymore."));
        }
      }
      return branchUpdate;
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private Set<RevCommit> getAlreadyAccepted(final CodeReviewCommit branchTip)
      throws MergeException {
    final Set<RevCommit> alreadyAccepted = new HashSet<RevCommit>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (final Ref r : repo.getRefDatabase().getRefs(ALL).values()) {
        if (r.getName().startsWith(Constants.R_HEADS)) {
          try {
            alreadyAccepted.add(rw.parseCommit(r.getObjectId()));
          } catch (IncorrectObjectTypeException iote) {
            // Not a commit? Skip over it.
          }
        }
      }
    } catch (IOException e) {
      throw new MergeException("Failed to determine already accepted commits.", e);
    }

    return alreadyAccepted;
  }

  private ListMultimap<SubmitType, Change> validateChangeList(
      final List<Change> submitted) throws MergeException {
    final ListMultimap<SubmitType, Change> toSubmit =
        ArrayListMultimap.create();

    final Map<String, Ref> allRefs;
    try {
      allRefs = repo.getRefDatabase().getRefs(ALL);
    } catch (IOException e) {
      throw new MergeException(e.getMessage(), e);
    }

    final Set<ObjectId> tips = new HashSet<ObjectId>();
    for (final Ref r : allRefs.values()) {
      tips.add(r.getObjectId());
    }

    int commitOrder = 0;
    for (final Change chg : submitted) {
      ChangeControl ctl;
      try {
        ctl = changeControlFactory.controlFor(chg,
            identifiedUserFactory.create(chg.getOwner()));
      } catch (NoSuchChangeException e) {
        throw new MergeException("Failed to validate changes", e);
      }
      final Change.Id changeId = chg.getId();
      if (chg.currentPatchSetId() == null) {
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      final PatchSet ps;
      try {
        ps = db.patchSets().get(chg.currentPatchSetId());
      } catch (OrmException e) {
        throw new MergeException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        toUpdate.add(chg);
        continue;
      }

      final String idstr = ps.getRevision().get();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
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
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        log.error("Invalid commit " + id.name() + " on " + chg.getKey(), e);
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        toUpdate.add(chg);
        continue;
      }

      commit.setControl(ctl);
      commit.setPatchsetId(ps.getId());
      commit.originalOrder = commitOrder++;
      commits.put(changeId, commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(repo, commit, destProject, destBranch, ps.getId());
      } catch (MergeValidationException mve) {
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
            commit.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
            try {
              setMerged(chg, null);
            } catch (OrmException e) {
              log.error("Cannot mark change " + chg.getId() + " merged", e);
            }
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      final SubmitType submitType = getSubmitType(commit.getControl(), ps);
      if (submitType == null) {
        commit.setStatusCode(CommitMergeStatus.NO_SUBMIT_TYPE);
        toUpdate.add(chg);
        continue;
      }

      commit.add(canMergeFlag);
      toMerge.put(submitType, commit);
      toSubmit.put(submitType, chg);
    }
    return toSubmit;
  }

  private SubmitType getSubmitType(ChangeControl ctl, PatchSet ps) {
    SubmitTypeRecord r = ctl.getSubmitTypeRecord(db, ps);
    if (r.status != SubmitTypeRecord.Status.OK) {
      log.error("Failed to get submit type for " + ctl.getChange().getKey());
      return null;
    }
    return r.type;
  }

  private RefUpdate updateBranch(final SubmitStrategy strategy,
      final RefUpdate branchUpdate) throws MergeException {
    if (branchTip == mergeTip || mergeTip == null) {
      // nothing to do
      return null;
    }

    if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
      try {
        ProjectConfig cfg =
            new ProjectConfig(destProject.getProject().getNameKey());
        cfg.load(repo, mergeTip);
      } catch (Exception e) {
        throw new MergeException("Submit would store invalid"
            + " project configuration " + mergeTip.name() + " for "
            + destProject.getProject().getName(), e);
      }
    }

    branchUpdate.setRefLogIdent(refLogIdent);
    branchUpdate.setForceUpdate(false);
    branchUpdate.setNewObjectId(mergeTip);
    branchUpdate.setRefLogMessage("merged", true);
    try {
      switch (branchUpdate.update(rw)) {
        case NEW:
        case FAST_FORWARD:
          if (branchUpdate.getResult() == RefUpdate.Result.FAST_FORWARD) {
            tagCache.updateFastForward(destBranch.getParentKey(),
                branchUpdate.getName(),
                branchUpdate.getOldObjectId(),
                mergeTip);
          }

          if (RefNames.REFS_CONFIG.equals(branchUpdate.getName())) {
            projectCache.evict(destProject.getProject());
            destProject = projectCache.get(destProject.getProject().getNameKey());
            repoManager.setProjectDescription(
                destProject.getProject().getNameKey(),
                destProject.getProject().getDescription());
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
          throw new IOException(branchUpdate.getResult().name() + ", " + msg);
        default:
          throw new IOException(branchUpdate.getResult().name());
      }
    } catch (IOException e) {
      throw new MergeException("Cannot update " + branchUpdate.getName(), e);
    }
  }

  private void fireRefUpdated(RefUpdate branchUpdate) {
    gitRefUpdated.fire(destBranch.getParentKey(), branchUpdate);
    hooks.doRefUpdatedHook(destBranch, branchUpdate, getAccount(mergeTip));
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

  private void updateChangeStatus(final List<Change> submitted) {
    for (final Change c : submitted) {
      final CodeReviewCommit commit = commits.get(c.getId());
      final CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        continue;
      }

      final String txt = s.getMessage();

      try {
        switch (s) {
          case CLEAN_MERGE:
            setMerged(c, message(c, txt));
            break;

          case CLEAN_REBASE:
          case CLEAN_PICK:
            setMerged(c, message(c, txt + " as " + commit.name()));
            break;

          case ALREADY_MERGED:
            setMerged(c, null);
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
            potentiallyStillSubmittable.add(commit);
            break;

          default:
            setNew(commit, message(c, "Unspecified merge failure: " + s.name()));
            break;
        }
      } catch (OrmException err) {
        log.warn("Error updating change status for " + c.getId(), err);
      } catch (IOException err) {
        log.warn("Error updating change status for " + c.getId(), err);
      }
    }
  }

  private void updateSubscriptions(final List<Change> submitted) {
    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      SubmoduleOp subOp =
          subOpFactory.create(destBranch, mergeTip, rw, repo,
              destProject.getProject(), submitted, commits,
              getAccount(mergeTip));
      try {
        subOp.update();
      } catch (SubmoduleException e) {
        log
            .error("The gitLinks were not updated according to the subscriptions "
                + e.getMessage());
      }
    }
  }

  private Capable isSubmitStillPossible(final CodeReviewCommit commit) {
    final Capable capable;
    final Change c = commit.change();
    final boolean submitStillPossible = isSubmitForMissingCommitsStillPossible(commit);
    final long now = TimeUtil.nowMs();
    final long waitUntil = c.getLastUpdatedOn().getTime() + DEPENDENCY_DELAY;
    if (submitStillPossible && now < waitUntil) {
      // If we waited a short while we might still be able to get
      // this change submitted. Reschedule an attempt in a bit.
      //
      mergeQueue.recheckAfter(destBranch, waitUntil - now, MILLISECONDS);
      capable = Capable.OK;
    } else if (submitStillPossible) {
      // It would be possible to submit the change if the missing
      // dependencies are also submitted. Perhaps the user just
      // forgot to submit those.
      //
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
      StringBuilder m = new StringBuilder();
      m.append("Change cannot be merged due to unsatisfiable dependencies.\n");
      m.append("\n");
      m.append("The following dependency errors were found:\n");
      m.append("\n");
      for (CodeReviewCommit missingCommit : commit.missing) {
        if (missingCommit.getPatchsetId() != null) {
          m.append("* Depends on patch set ");
          m.append(missingCommit.getPatchsetId().get());
          m.append(" of ");
          m.append(missingCommit.change().getKey().abbreviate());
          if (missingCommit.getPatchsetId().get() != missingCommit.change().currentPatchSetId().get()) {
            m.append(", however the current patch set is ");
            m.append(missingCommit.change().currentPatchSetId().get());
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

  private void loadChangeInfo(final CodeReviewCommit commit)
      throws NoSuchChangeException, OrmException {
    if (commit.getControl() == null) {
      List<PatchSet> matches =
          db.patchSets().byRevision(new RevId(commit.name())).toList();
      if (matches.size() == 1) {
        PatchSet ps = matches.get(0);
        commit.setPatchsetId(ps.getId());
        commit.setControl(changeControl(db.changes().get(ps.getId().getParentKey())));
      }
    }
  }

  private ChangeMessage message(final Change c, final String body) {
    final String uuid;
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

  private void setMerged(Change c, ChangeMessage msg)
      throws OrmException, IOException {
    try {
      db.changes().beginTransaction(c.getId());

      // We must pull the patchset out of commits, because the patchset ID is
      // modified when using the cherry-pick merge strategy.
      CodeReviewCommit commit = commits.get(c.getId());
      PatchSet.Id merged = commit.change().currentPatchSetId();
      c = setMergedPatchSet(c.getId(), merged);
      PatchSetApproval submitter =
          approvalsUtil.getSubmitter(db, commit.notes(), merged);
      addMergedMessage(submitter, msg);

      db.commit();

      sendMergedEmail(c, submitter);
      indexer.index(db, c);
      if (submitter != null) {
        try {
          hooks.doChangeMergedHook(c,
              accountCache.get(submitter.getAccountId()).getAccount(),
              db.patchSets().get(merged), db);
        } catch (OrmException ex) {
          log.error("Cannot run hook for submitted patch set " + c.getId(), ex);
        }
      }
    } finally {
      db.rollback();
    }
  }

  private Change setMergedPatchSet(Change.Id changeId, final PatchSet.Id merged)
      throws OrmException {
    return db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change c) {
        c.setStatus(Change.Status.MERGED);
        // It could be possible that the change being merged
        // has never had its mergeability tested. So we insure
        // merged changes has mergeable field true.
        c.setMergeable(true);
        if (!merged.equals(c.currentPatchSetId())) {
          // Uncool; the patch set changed after we merged it.
          // Go back to the patch set that was actually merged.
          //
          try {
            c.setCurrentPatchSet(patchSetInfoFactory.get(db, merged));
          } catch (PatchSetInfoNotAvailableException e1) {
            log.error("Cannot read merged patch set " + merged, e1);
          }
        }
        ChangeUtil.updated(c);
        return c;
      }
    });
  }

  private void addMergedMessage(PatchSetApproval submitter, ChangeMessage msg)
      throws OrmException {
    if (msg != null) {
      if (submitter != null && msg.getAuthor() == null) {
        msg.setAuthor(submitter.getAccountId());
      }
      db.changeMessages().insert(Collections.singleton(msg));
    }
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
          log.error("Cannot send email for submitted patch set " + c.getId(), e);
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
          log.error("Cannot send email for submitted patch set " + c.getId(), e);
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

  private void setNew(CodeReviewCommit c, ChangeMessage msg) {
    sendMergeFail(c.notes(), msg, true);
  }

  private void setNew(Change c, ChangeMessage msg) throws OrmException {
    sendMergeFail(notesFactory.create(c), msg, true);
  }

  private enum RetryStatus {
    UNSUBMIT, RETRY_NO_MESSAGE, RETRY_ADD_MESSAGE
  }

  private RetryStatus getRetryStatus(
      @Nullable PatchSetApproval submitter,
      ChangeMessage msg) {
    if (submitter != null
        && TimeUtil.nowMs() - submitter.getGranted().getTime()
          > MAX_SUBMIT_WINDOW) {
      return RetryStatus.UNSUBMIT;
    }

    try {
      ChangeMessage last = Iterables.getLast(db.changeMessages().byChange(
          msg.getPatchSetId().getParentKey()), null);
      if (last != null) {
        if (Objects.equal(last.getAuthor(), msg.getAuthor())
            && Objects.equal(last.getMessage(), msg.getMessage())) {
          long lastMs = last.getWrittenOn().getTime();
          long msgMs = msg.getWrittenOn().getTime();
          return msgMs - lastMs > MAX_SUBMIT_WINDOW
              ? RetryStatus.UNSUBMIT
              : RetryStatus.RETRY_NO_MESSAGE;
        }
      }
      return RetryStatus.RETRY_ADD_MESSAGE;
    } catch (OrmException err) {
      log.warn("Cannot check previous merge failure, unsubmitting", err);
      return RetryStatus.UNSUBMIT;
    }
  }

  private void sendMergeFail(ChangeNotes notes, final ChangeMessage msg,
      boolean makeNew) {
    PatchSetApproval submitter = null;
    try {
      submitter = approvalsUtil.getSubmitter(
          db, notes, notes.getChange().currentPatchSetId());
    } catch (Exception e) {
      log.error("Cannot get submitter", e);
    }

    if (!makeNew) {
      RetryStatus retryStatus = getRetryStatus(submitter, msg);
      if (retryStatus == RetryStatus.RETRY_NO_MESSAGE) {
        return;
      } else if (retryStatus == RetryStatus.UNSUBMIT) {
        makeNew = true;
      }
    }

    final boolean setStatusNew = makeNew;
    final Change c = notes.getChange();
    Change change = null;
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
        db.changeMessages().insert(Collections.singleton(msg));
        db.commit();
      } finally {
        db.rollback();
      }
    } catch (OrmException err) {
      log.warn("Cannot record merge failure message", err);
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
          log.error("Cannot send email notifications about merge failure", e);
          return;
        }

        try {
          final MergeFailSender cm = mergeFailSenderFactory.create(c);
          if (from != null) {
            cm.setFrom(from.getAccountId());
          }
          cm.setPatchSet(patchSet);
          cm.setChangeMessage(msg);
          cm.send();
        } catch (Exception e) {
          log.error("Cannot send email notifications about merge failure", e);
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
        log.error("Failed to index new change message", e);
      }
    }

    if (submitter != null) {
      try {
        hooks.doMergeFailedHook(c,
            accountCache.get(submitter.getAccountId()).getAccount(),
            db.patchSets().get(c.currentPatchSetId()), msg.getMessage(), db);
      } catch (OrmException ex) {
        log.error("Cannot run hook for merge failed " + c.getId(), ex);
      }
    }
  }

  private void abandonAllOpenChanges() {
    Exception err = null;
    try {
      openSchema();
      for (Change c : db.changes().byProjectOpenAll(destBranch.getParentKey())) {
        abandonOneChange(c);
      }
      db.close();
      db = null;
    } catch (IOException e) {
      err = e;
    } catch (OrmException e) {
      err = e;
    }
    if (err != null) {
      log.warn(String.format(
          "Cannot abandon changes for deleted project %s",
          destBranch.getParentKey().get()), err);
    }
  }

  private void abandonOneChange(Change change) throws OrmException,
      IOException {
    db.changes().beginTransaction(change.getId());
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
        db.changeMessages().insert(Collections.singleton(msg));
        db.commit();
        indexer.index(db, change);
      }
    } finally {
      db.rollback();
    }
  }
}
