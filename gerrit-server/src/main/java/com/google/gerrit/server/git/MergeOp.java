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

import static com.google.gerrit.server.git.MergeUtil.getSubmitter;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmConcurrencyException;
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
import java.util.Map.Entry;
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

  private static final long DUPLICATE_MESSAGE_INTERVAL =
      MILLISECONDS.convert(1, DAYS);

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ProjectCache projectCache;
  private final LabelNormalizer labelNormalizer;
  private final GitReferenceUpdated gitRefUpdated;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final MergeQueue mergeQueue;

  private final Branch.NameKey destBranch;
  private ProjectState destProject;
  private final ListMultimap<SubmitType, CodeReviewCommit> toMerge;
  private final List<CodeReviewCommit> potentiallyStillSubmittable;
  private final Map<Change.Id, CodeReviewCommit> commits;
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
  private final AllProjectsName allProjectsName;

  @Inject
  MergeOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final LabelNormalizer fs,
      final GitReferenceUpdated gru, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf,
      final LabelTypes labelTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      final ChangeControl.GenericFactory changeControlFactory,
      final MergeQueue mergeQueue, @Assisted final Branch.NameKey branch,
      final ChangeHooks hooks, final AccountCache accountCache,
      final TagCache tagCache,
      final SubmitStrategyFactory submitStrategyFactory,
      final SubmoduleOp.Factory subOpFactory,
      final WorkQueue workQueue,
      final RequestScopePropagator requestScopePropagator,
      final AllProjectsName allProjectsName) {
    repoManager = grm;
    schemaFactory = sf;
    labelNormalizer = fs;
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
    this.allProjectsName = allProjectsName;
    destBranch = branch;
    toMerge = ArrayListMultimap.create();
    potentiallyStillSubmittable = new ArrayList<CodeReviewCommit>();
    commits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  public void verifyMergeability(Change change) throws NoSuchProjectException {
    try {
      setDestProject();
      openRepository();
      final Ref destBranchRef = repo.getRef(destBranch.get());

      // Test mergeability of the change if the last merged sha1
      // in the branch is different from the last sha1
      // the change was tested against.
      if ((destBranchRef == null && change.getLastSha1MergeTested() == null)
          || change.getLastSha1MergeTested() == null
          || (destBranchRef != null && !destBranchRef.getObjectId().getName()
              .equals(change.getLastSha1MergeTested().get()))) {
        openSchema();
        openBranch();
        validateChangeList(Collections.singletonList(change));
        if (!toMerge.isEmpty()) {
          final Entry<SubmitType, CodeReviewCommit> e =
              toMerge.entries().iterator().next();
          final boolean isMergeable =
              createStrategy(e.getKey()).dryRun(branchTip, e.getValue());

          // update sha1 tested merge.
          if (destBranchRef != null) {
            change.setLastSha1MergeTested(new RevId(destBranchRef
                .getObjectId().getName()));
          } else {
            change.setLastSha1MergeTested(new RevId(""));
          }
          change.setMergeable(isMergeable);
          db.changes().update(Collections.singleton(change));
        } else {
          log.error("Test merge attempt for change: " + change.getId()
              + " failed");
        }
      }
    } catch (MergeException e) {
      log.error("Test merge attempt for change: " + change.getId()
          + " failed", e);
    } catch (OrmException e) {
      log.error("Test merge attempt for change: " + change.getId()
          + " failed: Not able to query the database", e);
    } catch (IOException e) {
      log.error("Test merge attempt for change: " + change.getId()
          + " failed", e);
    } finally {
      if (repo != null) {
        repo.close();
      }
      if (db != null) {
        db.close();
      }
    }
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

  public void merge() throws MergeException, NoSuchProjectException {
    setDestProject();
    try {
      openSchema();
      openRepository();
      openBranch();
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
          final RefUpdate branchUpdate = openBranch();
          final SubmitStrategy strategy = createStrategy(submitType);
          preMerge(strategy, toMerge.get(submitType));
          updateBranch(strategy, branchUpdate);
          updateChangeStatus(toSubmit.get(submitType));
          updateSubscriptions(toSubmit.get(submitType));

          for (final Iterator<CodeReviewCommit> it =
              potentiallyStillSubmittable.iterator(); it.hasNext();) {
            final CodeReviewCommit commit = it.next();
            if (containsMissingCommits(toMerge, commit)
                || containsMissingCommits(toMergeNextTurn, commit)) {
              // change has missing dependencies, but all commits which are
              // missing are still attempted to be merged with another submit
              // strategy, retry to merge this commit in the next turn
              it.remove();
              commit.statusCode = null;
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

      for (final CodeReviewCommit commit : potentiallyStillSubmittableOnNextRun) {
        final Capable capable = isSubmitStillPossible(commit);
        if (capable != Capable.OK) {
          sendMergeFail(commit.change,
              message(commit.change, capable.getMessage()), false);
        }
      }
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
      loadChangeInfo(missingCommit);

      if (missingCommit.patchsetId == null) {
        // The commit doesn't have a patch set, so it cannot be
        // submitted to the branch.
        //
        return false;
      }

      if (!missingCommit.change.currentPatchSetId().equals(
          missingCommit.patchsetId)) {
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

  private void openRepository() throws MergeException {
    final Project.NameKey name = destBranch.getParentKey();
    try {
      repo = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notGit) {
      final String m = "Repository \"" + name.get() + "\" unknown.";
      throw new MergeException(m, notGit);
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
      } else {
        branchTip = null;
      }

      try {
        final Ref destRef = repo.getRef(destBranch.get());
        if (destRef != null) {
          branchUpdate.setExpectedOldObjectId(destRef.getObjectId());
        } else if (repo.getFullBranch().equals(destBranch.get())) {
          branchUpdate.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
          for (final Change c : db.changes().submitted(destBranch).toList()) {
            setNew(c, message(c, "Your change could not be merged, "
                + "because the destination branch does not exist anymore."));
          }
        }
      } catch (IOException e) {
        throw new MergeException(
            "Failed to check existence of destination branch", e);
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
      for (final Ref r : repo.getAllRefs().values()) {
        if (r.getName().startsWith(Constants.R_HEADS)
            || r.getName().startsWith(Constants.R_TAGS)) {
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

    final Set<ObjectId> tips = new HashSet<ObjectId>();
    for (final Ref r : repo.getAllRefs().values()) {
      tips.add(r.getObjectId());
    }

    int commitOrder = 0;
    for (final Change chg : submitted) {
      final Change.Id changeId = chg.getId();
      if (chg.currentPatchSetId() == null) {
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
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
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
        continue;
      }

      final String idstr = ps.getRevision().get();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
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
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.REVISION_GONE));
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        log.error("Invalid commit " + id.name() + " on " + chg.getKey(), e);
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.REVISION_GONE));
        continue;
      }

      if (GitRepositoryManager.REF_CONFIG.equals(destBranch.get())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg =
              new ProjectConfig(destProject.getProject().getNameKey());
          cfg.load(repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
        } catch (Exception e) {
          commits.put(changeId, CodeReviewCommit
              .error(CommitMergeStatus.INVALID_PROJECT_CONFIGURATION));
          continue;
        }
        final Project.NameKey oldParent =
            destProject.getProject().getParent(allProjectsName);
        if (oldParent == null) {
          // update of the 'All-Projects' project
          if (newParent != null) {
            commits.put(changeId, CodeReviewCommit
                .error(CommitMergeStatus.INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT));
            continue;
          }
        } else {
          if (!oldParent.equals(newParent)) {
            final PatchSetApproval psa = getSubmitter(db, ps.getId());
            if (psa == null) {
              commits.put(changeId, CodeReviewCommit
                  .error(CommitMergeStatus.SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN));
              continue;
            }
            final IdentifiedUser submitter =
                identifiedUserFactory.create(psa.getAccountId());
            if (!submitter.getCapabilities().canAdministrateServer()) {
              commits.put(changeId, CodeReviewCommit
                  .error(CommitMergeStatus.SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN));
              continue;
            }

            if (projectCache.get(newParent) == null) {
              commits.put(changeId, CodeReviewCommit
                  .error(CommitMergeStatus.INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND));
              continue;
            }
          }
        }
      }

      commit.change = chg;
      commit.patchsetId = ps.getId();
      commit.originalOrder = commitOrder++;
      commits.put(changeId, commit);

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. It's
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            commit.statusCode = CommitMergeStatus.ALREADY_MERGED;
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      final SubmitType submitType = getSubmitType(chg, ps);
      if (submitType == null) {
        commits.put(changeId,
            CodeReviewCommit.error(CommitMergeStatus.NO_SUBMIT_TYPE));
        continue;
      }

      commit.add(canMergeFlag);
      toMerge.put(submitType, commit);
      toSubmit.put(submitType, chg);
    }
    return toSubmit;
  }

  private SubmitType getSubmitType(final Change change, final PatchSet ps) {
    try {
      final SubmitTypeRecord r =
          changeControlFactory.controlFor(change,
              identifiedUserFactory.create(change.getOwner()))
              .getSubmitTypeRecord(db, ps);
      if (r.status != SubmitTypeRecord.Status.OK) {
        log.error("Failed to get submit type for " + change.getKey());
        return null;
      }
      return r.type;
    } catch (NoSuchChangeException e) {
      log.error("Failed to get submit type for " + change.getKey(), e);
      return null;
    }
  }

  private void updateBranch(final SubmitStrategy strategy,
      final RefUpdate branchUpdate) throws MergeException {
    if ((branchTip == null && mergeTip == null) || branchTip == mergeTip) {
      // nothing to do
      return;
    }

    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      if (GitRepositoryManager.REF_CONFIG.equals(branchUpdate.getName())) {
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

            if (GitRepositoryManager.REF_CONFIG.equals(branchUpdate.getName())) {
              projectCache.evict(destProject.getProject());
              destProject = projectCache.get(destProject.getProject().getNameKey());
              repoManager.setProjectDescription(
                  destProject.getProject().getNameKey(),
                  destProject.getProject().getDescription());
            }

            gitRefUpdated.fire(destBranch.getParentKey(), branchUpdate);

            Account account = null;
            final PatchSetApproval submitter = getSubmitter(db, mergeTip.patchsetId);
            if (submitter != null) {
              account = accountCache.get(submitter.getAccountId()).getAccount();
            }
            hooks.doRefUpdatedHook(destBranch, branchUpdate, account);
            break;

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
  }

  private void updateChangeStatus(final List<Change> submitted) {
    for (final Change c : submitted) {
      final CodeReviewCommit commit = commits.get(c.getId());
      final CommitMergeStatus s = commit != null ? commit.statusCode : null;
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
          case INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND:
          case INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT:
          case SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN:
            setNew(c, message(c, txt));
            break;

          case MISSING_DEPENDENCY:
            potentiallyStillSubmittable.add(commit);
            break;

          default:
            setNew(c, message(c, "Unspecified merge failure: " + s.name()));
            break;
        }
      } catch (OrmException err) {
        log.warn("Error updating change status for " + c.getId(), err);
      }
    }
  }

  private void updateSubscriptions(final List<Change> submitted) {
    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      SubmoduleOp subOp =
          subOpFactory.create(destBranch, mergeTip, rw, repo,
              destProject.getProject(), submitted, commits);
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
    final Change c = commit.change;
    final boolean submitStillPossible = isSubmitForMissingCommitsStillPossible(commit);
    final long now = System.currentTimeMillis();
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
        m.append(missingCommit.change.getKey().get());
        m.append("\n");
      }
      capable = new Capable(m.toString());
    } else {
      // It is impossible to submit this change as-is. The author
      // needs to rebase it in order to work around the missing
      // dependencies.
      //
      StringBuilder m = new StringBuilder();
      m.append("Change cannot be merged due"
          + " to unsatisfiable dependencies.\n");
      m.append("\n");
      m.append("The following dependency errors were found:\n");
      m.append("\n");
      for (CodeReviewCommit missingCommit : commit.missing) {
        if (missingCommit.patchsetId != null) {
          m.append("* Depends on patch set ");
          m.append(missingCommit.patchsetId.get());
          m.append(" of ");
          m.append(missingCommit.change.getKey().abbreviate());
          if (missingCommit.patchsetId.get() != missingCommit.change.currentPatchSetId().get()) {
            m.append(", however the current patch set is ");
            m.append(missingCommit.change.currentPatchSetId().get());
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

  private void loadChangeInfo(final CodeReviewCommit commit) {
    if (commit.patchsetId == null) {
      try {
        List<PatchSet> matches =
            db.patchSets().byRevision(new RevId(commit.name())).toList();
        if (matches.size() == 1) {
          final PatchSet ps = matches.get(0);
          commit.patchsetId = ps.getId();
          commit.change = db.changes().get(ps.getId().getParentKey());
        }
      } catch (OrmException e) {
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
    final ChangeMessage m =
        new ChangeMessage(new ChangeMessage.Key(c.getId(), uuid), null,
            c.currentPatchSetId());
    m.setMessage(body);
    return m;
  }

  private void setMerged(final Change c, final ChangeMessage msg)
      throws OrmException {
    try {
      db.changes().beginTransaction(c.getId());

      // We must pull the patchset out of commits, because the patchset ID is
      // modified when using the cherry-pick merge strategy.
      CodeReviewCommit commit = commits.get(c.getId());
      PatchSet.Id merged = commit.change.currentPatchSetId();
      setMergedPatchSet(c.getId(), merged);
      PatchSetApproval submitter = saveApprovals(c, merged);
      addMergedMessage(submitter, msg);

      db.commit();

      sendMergedEmail(c, submitter);
      if (submitter != null) {
        try {
          hooks.doChangeMergedHook(c,
              accountCache.get(submitter.getAccountId()).getAccount(),
              db.patchSets().get(c.currentPatchSetId()), db);
        } catch (OrmException ex) {
          log.error("Cannot run hook for submitted patch set " + c.getId(), ex);
        }
      }
    } finally {
      db.rollback();
    }
  }

  private void setMergedPatchSet(Change.Id changeId, final PatchSet.Id merged)
      throws OrmException {
    db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
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

  private PatchSetApproval saveApprovals(Change c, PatchSet.Id merged)
      throws OrmException {
    // Flatten out existing approvals for this patch set based upon the current
    // permissions. Once the change is closed the approvals are not updated at
    // presentation view time, except for zero votes used to indicate a reviewer
    // was added. So we need to make sure votes are accurate now. This way if
    // permissions get modified in the future, historical records stay accurate.
    PatchSetApproval submitter = null;
    try {
      c.setStatus(Change.Status.MERGED);

      List<PatchSetApproval> approvals =
          db.patchSetApprovals().byPatchSet(merged).toList();
      Set<PatchSetApproval.Key> toDelete =
          Sets.newHashSetWithExpectedSize(approvals.size());
      for (PatchSetApproval a : approvals) {
        if (a.getValue() != 0) {
          toDelete.add(a.getKey());
        }
      }

      approvals = labelNormalizer.normalize(c, approvals);
      for (PatchSetApproval a : approvals) {
        toDelete.remove(a.getKey());
        if (a.getValue() > 0 && a.isSubmit()) {
          if (submitter == null
              || a.getGranted().compareTo(submitter.getGranted()) > 0) {
            submitter = a;
          }
        }
        a.cache(c);
      }
      db.patchSetApprovals().update(approvals);
      db.patchSetApprovals().deleteKeys(toDelete);
    } catch (NoSuchChangeException err) {
      throw new OrmException(err);
    }
    return submitter;
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
          final ChangeControl control = changeControlFactory.controlFor(c,
              identifiedUserFactory.create(c.getOwner()));
          final MergedSender cm = mergedSenderFactory.create(control);
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

  private void setNew(Change c, ChangeMessage msg) {
    sendMergeFail(c, msg, true);
  }

  private boolean isDuplicate(ChangeMessage msg) {
    try {
      ChangeMessage last = Iterables.getLast(db.changeMessages().byChange(
          msg.getPatchSetId().getParentKey()), null);
      if (last != null) {
        long lastMs = last.getWrittenOn().getTime();
        long msgMs = msg.getWrittenOn().getTime();
        if (Objects.equal(last.getAuthor(), msg.getAuthor())
            && Objects.equal(last.getMessage(), msg.getMessage())
            && msgMs - lastMs < DUPLICATE_MESSAGE_INTERVAL) {
          return true;
        }
      }
    } catch (OrmException err) {
      log.warn("Cannot check previous merge failure message", err);
    }
    return false;
  }

  private void sendMergeFail(final Change c, final ChangeMessage msg,
      final boolean makeNew) {
    if (isDuplicate(msg)) {
      return;
    }

    try {
      db.changeMessages().insert(Collections.singleton(msg));
    } catch (OrmException err) {
      log.warn("Cannot record merge failure message", err);
    }

    if (makeNew) {
      try {
        db.changes().atomicUpdate(c.getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (c.getStatus().isOpen()) {
              c.setStatus(Change.Status.NEW);
              ChangeUtil.updated(c);
            }
            return c;
          }
        });
      } catch (OrmConcurrencyException err) {
      } catch (OrmException err) {
        log.warn("Cannot update change status", err);
      }
    } else {
      try {
        ChangeUtil.touch(c, db);
      } catch (OrmException err) {
        log.warn("Cannot update change timestamp", err);
      }
    }

    PatchSetApproval submitter = null;
    try {
      submitter = getSubmitter(db, c.currentPatchSetId());
    } catch (Exception e) {
      log.error("Cannot get submitter", e);
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
}
