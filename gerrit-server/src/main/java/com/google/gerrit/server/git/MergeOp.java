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

import static com.google.gerrit.server.git.MergeUtil.computeMergeCommitAuthor;
import static com.google.gerrit.server.git.MergeUtil.getSubmitter;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
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
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
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

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ProjectCache projectCache;
  private final FunctionState.Factory functionState;
  private final GitReferenceUpdated replication;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final ApprovalTypes approvalTypes;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final MergeQueue mergeQueue;

  private final PersonIdent myIdent;
  private final Branch.NameKey destBranch;
  private Project destProject;
  private final Map<SubmitType, List<CodeReviewCommit>> toMerge;
  private final List<CodeReviewCommit> potentiallyStillSubmittable;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private ReviewDb db;
  private Repository repo;
  private RevWalk rw;
  private RevFlag canMergeFlag;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private PersonIdent refLogIdent;
  private ObjectInserter inserter;

  private final ChangeHooks hooks;
  private final AccountCache accountCache;
  private final TagCache tagCache;
  private final CreateCodeReviewNotes.Factory codeReviewNotesFactory;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final WorkQueue workQueue;
  private final RequestScopePropagator requestScopePropagator;

  @Inject
  MergeOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final FunctionState.Factory fs,
      final GitReferenceUpdated rq, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf, final ApprovalTypes approvalTypes,
      final PatchSetInfoFactory psif, final IdentifiedUser.GenericFactory iuf,
      final ChangeControl.GenericFactory changeControlFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      final MergeQueue mergeQueue, @Assisted final Branch.NameKey branch,
      final ChangeHooks hooks, final AccountCache accountCache,
      final TagCache tagCache, final CreateCodeReviewNotes.Factory crnf,
      final SubmitStrategyFactory submitStrategyFactory,
      final SubmoduleOp.Factory subOpFactory, final WorkQueue workQueue,
      final RequestScopePropagator requestScopePropagator) {
    repoManager = grm;
    schemaFactory = sf;
    functionState = fs;
    projectCache = pc;
    replication = rq;
    mergedSenderFactory = msf;
    mergeFailSenderFactory = mfsf;
    this.approvalTypes = approvalTypes;
    patchSetInfoFactory = psif;
    identifiedUserFactory = iuf;
    this.changeControlFactory = changeControlFactory;
    this.mergeQueue = mergeQueue;
    this.hooks = hooks;
    this.accountCache = accountCache;
    this.tagCache = tagCache;
    codeReviewNotesFactory = crnf;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.workQueue = workQueue;
    this.requestScopePropagator = requestScopePropagator;
    this.myIdent = myIdent;
    destBranch = branch;
    toMerge = new HashMap<SubmitType, List<CodeReviewCommit>>();
    potentiallyStillSubmittable = new ArrayList<CodeReviewCommit>();
    commits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  public void verifyMergeability(Change change) {
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
        final Entry<SubmitType, List<CodeReviewCommit>> entry =
            toMerge.entrySet().iterator().next();
        preMerge(entry.getKey(), entry.getValue());

        // update sha1 tested merge.
        if (destBranchRef != null) {
          change.setLastSha1MergeTested(new RevId(destBranchRef
              .getObjectId().getName()));
        } else {
          change.setLastSha1MergeTested(new RevId(""));
        }
        change.setMergeable(isMergeable(change));
        db.changes().update(Collections.singleton(change));
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
    final ProjectState pe = projectCache.get(destBranch.getParentKey());
    if (pe == null) {
      throw new MergeException("No such project: " + destBranch.getParentKey());
    }
    destProject = pe.getProject();
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
      openBranch();
      final Map<SubmitType, List<Change>> toSubmit =
          validateChangeList(db.changes().submitted(destBranch).toList());

      final Map<SubmitType, List<CodeReviewCommit>> toMergeNextTurn =
          new HashMap<SubmitType, List<CodeReviewCommit>>();
      final List<CodeReviewCommit> potentiallyStillSubmittableOnNextRun =
          new ArrayList<CodeReviewCommit>();
      while (!toMerge.isEmpty()) {
        toMergeNextTurn.clear();
        for (final Entry<SubmitType, List<CodeReviewCommit>> e : toMerge.entrySet()) {
          final SubmitType submitType = e.getKey();
          final RefUpdate branchUpdate = openBranch();
          preMerge(submitType, e.getValue());
          updateBranch(branchUpdate);
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
              getList(submitType, toMergeNextTurn).add(commit);
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
      final Map<SubmitType, List<CodeReviewCommit>> map,
      final CodeReviewCommit commit) {
    if (!isSubmitForMissingCommitsStillPossible(commit)) {
      return false;
    }

    for (final CodeReviewCommit missingCommit : commit.missing) {
      boolean found = false;
      for (final List<CodeReviewCommit> list : map.values()) {
        if (list.contains(missingCommit)) {
          found = true;
          break;
        }
      }
      if (!found) {
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

  private void preMerge(final SubmitType submitType,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    final SubmitStrategy strategy = createStrategy(submitType);
    mergeTip = strategy.run(branchTip, toMerge);
    refLogIdent = strategy.getRefLogIdent();
    commits.putAll(strategy.getNewCommits());
  }

  private SubmitStrategy createStrategy(final SubmitType submitType) throws MergeException {
    return submitStrategyFactory.create(submitType, db, repo, rw, inserter,
        canMergeFlag, getAlreadyAccepted(branchTip), destBranch,
        destProject.isUseContentMerge());
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

  private RefUpdate openBranch() throws MergeException {
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
          throw new MergeException("Destination branch \""
              + branchUpdate.getRef().getName() + "\" does not exist");
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

  private Map<SubmitType, List<Change>> validateChangeList(
      final List<Change> submitted) throws MergeException {
    final Map<SubmitType, List<Change>> toSubmit =
        new HashMap<Project.SubmitType, List<Change>>();

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
      getList(submitType, toMerge).add(commit);
      getList(submitType, toSubmit).add(chg);
    }
    return toSubmit;
  }

  private SubmitType getSubmitType(final Change change, final PatchSet ps) {
    try {
      return changeControlFactory.controlFor(change,
          identifiedUserFactory.create(change.getOwner())).getSubmitAction(db,
          ps);
    } catch (NoSuchChangeException e) {
      log.error("Failed to get submit action for " + change.getKey());
      return null;
    }
  }

  private static <K, T> List<T> getList(final K key, final Map<K, List<T>> map) {
    List<T> list = map.get(key);
    if (list == null) {
      list = new ArrayList<T>();
      map.put(key, list);
    }
    return list;
  }

  private void updateBranch(final RefUpdate branchUpdate) throws MergeException {
    if ((branchTip == null && mergeTip == null) || branchTip == mergeTip) {
      // nothing to do
      return;
    }

    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      if (GitRepositoryManager.REF_CONFIG.equals(branchUpdate.getName())) {
        try {
          ProjectConfig cfg = new ProjectConfig(destProject.getNameKey());
          cfg.load(repo, mergeTip);
        } catch (Exception e) {
          throw new MergeException("Submit would store invalid"
              + " project configuration " + mergeTip.name() + " for "
              + destProject.getName(), e);
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
              projectCache.evict(destProject);
              ProjectState ps = projectCache.get(destProject.getNameKey());
              repoManager.setProjectDescription(destProject.getNameKey(), //
                  ps.getProject().getDescription());
            }

            replication.fire(destBranch.getParentKey(), branchUpdate.getName());

            Account account = null;
            final PatchSetApproval submitter = getSubmitter(db, mergeTip.patchsetId);
            if (submitter != null) {
              account = accountCache.get(submitter.getAccountId()).getAccount();
            }
            hooks.doRefUpdatedHook(destBranch, branchUpdate, account);
            break;

          default:
            throw new IOException(branchUpdate.getResult().name());
        }
      } catch (IOException e) {
        throw new MergeException("Cannot update " + branchUpdate.getName(), e);
      }
    }
  }

  private boolean isMergeable(Change c) {
    final CodeReviewCommit commit = commits.get(c.getId());
    final CommitMergeStatus s = commit != null ? commit.statusCode : null;
    boolean isMergeable = false;
    if (s != null
        && (s.equals(CommitMergeStatus.CLEAN_MERGE)
            || s.equals(CommitMergeStatus.CLEAN_PICK) || s
            .equals(CommitMergeStatus.ALREADY_MERGED))) {
      isMergeable = true;
    }

    return isMergeable;
  }

  private void updateChangeStatus(final List<Change> submitted) {
    List<CodeReviewCommit> merged = new ArrayList<CodeReviewCommit>();

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

      switch (s) {
        case CLEAN_MERGE: {
          setMerged(c, message(c, txt));
          merged.add(commit);
          break;
        }

        case CLEAN_PICK: {
          setMerged(c, message(c, txt + " as " + commit.name()));
          merged.add(commit);
          break;
        }

        case ALREADY_MERGED:
          setMerged(c, null);
          merged.add(commit);
          break;

        case PATH_CONFLICT:
        case CRISS_CROSS_MERGE:
        case CANNOT_CHERRY_PICK_ROOT:
        case NOT_FAST_FORWARD: {
          setNew(c, message(c, txt));
          break;
        }

        case MISSING_DEPENDENCY: {
          potentiallyStillSubmittable.add(commit);
          break;
        }

        default:
          setNew(c, message(c, "Unspecified merge failure: " + s.name()));
          break;
      }
    }

    CreateCodeReviewNotes codeReviewNotes =
        codeReviewNotesFactory.create(db, repo);
    try {
      codeReviewNotes.create(
          merged,
          computeMergeCommitAuthor(db, identifiedUserFactory, myIdent, rw,
              merged));
    } catch (CodeReviewNoteCreationException e) {
      log.error(e.getMessage());
    }
    replication.fire(destBranch.getParentKey(),
        GitRepositoryManager.REFS_NOTES_REVIEW);
  }

  private void updateSubscriptions(final List<Change> submitted) {
    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      SubmoduleOp subOp =
          subOpFactory.create(destBranch, mergeTip, rw, repo, destProject,
              submitted, commits);
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
      String txt =
          "Change could not be merged because of a missing dependency.";
      if (!isAlreadySent(c, txt)) {
        StringBuilder m = new StringBuilder();
        m.append(txt);
        m.append("\n");

        m.append("\n");

        m.append("The following changes must also be submitted:\n");
        m.append("\n");
        for (CodeReviewCommit missingCommit : commit.missing) {
          m.append("* ");
          m.append(missingCommit.change.getKey().get());
          m.append("\n");
        }
        txt = m.toString();
      }
      capable = new Capable(txt);
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
          m.append(", however the current patch set is ");
          m.append(missingCommit.change.currentPatchSetId().get());
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

  private boolean isAlreadySent(final Change c, final String prefix) {
    try {
      final List<ChangeMessage> msgList =
          db.changeMessages().byChange(c.getId()).toList();
      if (msgList.size() > 0) {
        final ChangeMessage last = msgList.get(msgList.size() - 1);
        if (last.getAuthor() == null && last.getMessage().startsWith(prefix)) {
          // The last message was written by us, and it said this
          // same message already. Its unlikely anything has changed
          // that would cause us to need to repeat ourselves.
          //
          return true;
        }
      }

      // The last message was not sent by us, or doesn't match the text
      // we are about to send.
      //
      return false;
    } catch (OrmException e) {
      return true;
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

  private void setMerged(final Change c, final ChangeMessage msg) {
    final Change.Id changeId = c.getId();
    // We must pull the patchset out of commits, because the patchset ID is
    // modified when using the cherry-pick merge strategy.
    final CodeReviewCommit commit = commits.get(c.getId());
    final PatchSet.Id merged = commit.change.currentPatchSetId();

    try {
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
    } catch (OrmConcurrencyException err) {
    } catch (OrmException err) {
      log.warn("Cannot update change status", err);
    }

    // Flatten out all existing approvals based upon the current
    // permissions. Once the change is closed the approvals are
    // not updated at presentation view time, so we need to make.
    // sure they are accurate now. This way if permissions get
    // modified in the future, historical records stay accurate.
    //
    PatchSetApproval submitter = null;
    try {
      c.setStatus(Change.Status.MERGED);
      final List<PatchSetApproval> approvals =
          db.patchSetApprovals().byChange(changeId).toList();
      final FunctionState fs = functionState.create(
          changeControlFactory.controlFor(
              c,
              identifiedUserFactory.create(c.getOwner())),
              merged, approvals);
      for (ApprovalType at : approvalTypes.getApprovalTypes()) {
        CategoryFunction.forCategory(at.getCategory()).run(at, fs);
      }
      for (PatchSetApproval a : approvals) {
        if (a.getValue() > 0
            && ApprovalCategory.SUBMIT.equals(a.getCategoryId())
            && a.getPatchSetId().equals(merged)) {
          if (submitter == null
              || a.getGranted().compareTo(submitter.getGranted()) > 0) {
            submitter = a;
          }
        }
        a.cache(c);
      }
      db.patchSetApprovals().update(approvals);
    } catch (NoSuchChangeException err) {
      log.warn("Cannot normalize approvals for change " + changeId, err);
    } catch (OrmException err) {
      log.warn("Cannot normalize approvals for change " + changeId, err);
    }

    if (msg != null) {
      if (submitter != null && msg.getAuthor() == null) {
        msg.setAuthor(submitter.getAccountId());
      }
      try {
        db.changeMessages().insert(Collections.singleton(msg));
      } catch (OrmException err) {
        log.warn("Cannot store message on change", err);
      }
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
          log.error("Cannot send email for submitted patch set " + c.getId(), e);
          return;
        }

        try {
          final MergedSender cm = mergedSenderFactory.create(c);
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


    try {
      hooks.doChangeMergedHook(c, //
          accountCache.get(submitter.getAccountId()).getAccount(), //
          db.patchSets().get(c.currentPatchSetId()), db);
    } catch (OrmException ex) {
      log.error("Cannot run hook for submitted patch set " + c.getId(), ex);
    }
  }

  private void setNew(Change c, ChangeMessage msg) {
    sendMergeFail(c, msg, true);
  }

  private void sendMergeFail(final Change c, final ChangeMessage msg,
      final boolean makeNew) {
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

    workQueue.getDefaultQueue()
        .submit(requestScopePropagator.wrap(new Runnable() {
      @Override
      public void run() {
        PatchSet patchSet;
        PatchSetApproval submitter;
        try {
          ReviewDb reviewDb = schemaFactory.open();
          try {
            patchSet = reviewDb.patchSets().get(c.currentPatchSetId());
            submitter = getSubmitter(reviewDb, c.currentPatchSetId());
          } finally {
            reviewDb.close();
          }
        } catch (Exception e) {
          log.error("Cannot send email notifications about merge failure", e);
          return;
        }

        try {
          final MergeFailSender cm = mergeFailSenderFactory.create(c);
          if (submitter != null) {
            cm.setFrom(submitter.getAccountId());
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
  }
}
