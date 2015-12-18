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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.index.ChangeIndexer;
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
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
public class MergeOp implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  private static class OpenRepo {
    final Repository repo;
    final CodeReviewRevWalk rw;
    final RevFlag canMergeFlag;
    final ObjectInserter ins;
    ProjectState project;

    private final Map<Branch.NameKey, OpenBranch> branches;

    OpenRepo(Repository repo, ProjectState project) {
      this.repo = repo;
      this.project = project;
      rw = CodeReviewCommit.newRevWalk(repo);
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.COMMIT_TIME_DESC, true);
      rw.setRetainBody(false);
      canMergeFlag = rw.newFlag("CAN_MERGE");

      ins = repo.newObjectInserter();
      branches = Maps.newHashMapWithExpectedSize(1);
    }

    OpenBranch getBranch(Branch.NameKey branch) throws IntegrationException {
      OpenBranch ob = branches.get(branch);
      if (ob == null) {
        ob = new OpenBranch(this, branch);
        branches.put(branch, ob);
      }
      return ob;
    }

    Project.NameKey getProjectName() {
      return project.getProject().getNameKey();
    }

    void close() {
      ins.close();
      rw.close();
      repo.close();
    }
  }

  private static class OpenBranch {
    final Branch.NameKey name;
    final RefUpdate update;
    final CodeReviewCommit oldTip;
    MergeTip mergeTip;

    OpenBranch(OpenRepo or, Branch.NameKey name) throws IntegrationException {
      this.name = name;
      try {
        update = or.repo.updateRef(name.get());
        if (update.getOldObjectId() != null) {
          oldTip = or.rw.parseCommit(update.getOldObjectId());
        } else if (Objects.equals(or.repo.getFullBranch(), name.get())) {
          oldTip = null;
          update.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
          throw new IntegrationException("The destination branch "
              + name + " does not exist anymore.");
        }
      } catch (IOException e) {
        throw new IntegrationException("Cannot open branch " + name, e);
      }
    }

    CodeReviewCommit getCurrentTip() {
      return mergeTip != null ? mergeTip.getCurrentTip() : oldTip;
    }
  }

  private final AccountCache accountCache;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeHooks hooks;
  private final ChangeIndexer indexer;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final LabelNormalizer labelNormalizer;
  private final EmailMerge.Factory mergedSenderFactory;
  private final MergeSuperSet mergeSuperSet;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ProjectCache projectCache;
  private final InternalChangeQuery internalChangeQuery;
  private final PersonIdent serverIdent;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final Provider<SubmoduleOp> subOpProvider;
  private final TagCache tagCache;

  private final Map<Project.NameKey, OpenRepo> openRepos;
  private final Map<Change.Id, CodeReviewCommit> commits;

  private static final String MACHINE_ID;
  static {
    String id;
    try {
      id = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      id = "unknown";
    }
    MACHINE_ID = id;
  }
  private String staticSubmissionId;
  private String submissionId;

  private ReviewDb db;

  @Inject
  MergeOp(AccountCache accountCache,
      ApprovalsUtil approvalsUtil,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeHooks hooks,
      ChangeIndexer indexer,
      ChangeMessagesUtil cmUtil,
      ChangeUpdate.Factory updateFactory,
      GitReferenceUpdated gitRefUpdated,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      LabelNormalizer labelNormalizer,
      EmailMerge.Factory mergedSenderFactory,
      MergeSuperSet mergeSuperSet,
      MergeValidators.Factory mergeValidatorsFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      ProjectCache projectCache,
      InternalChangeQuery internalChangeQuery,
      @GerritPersonIdent PersonIdent serverIdent,
      SubmitStrategyFactory submitStrategyFactory,
      Provider<SubmoduleOp> subOpProvider,
      TagCache tagCache) {
    this.accountCache = accountCache;
    this.approvalsUtil = approvalsUtil;
    this.changeControlFactory = changeControlFactory;
    this.hooks = hooks;
    this.indexer = indexer;
    this.cmUtil = cmUtil;
    this.updateFactory = updateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.repoManager = repoManager;
    this.identifiedUserFactory = identifiedUserFactory;
    this.labelNormalizer = labelNormalizer;
    this.mergedSenderFactory = mergedSenderFactory;
    this.mergeSuperSet = mergeSuperSet;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.projectCache = projectCache;
    this.internalChangeQuery = internalChangeQuery;
    this.serverIdent = serverIdent;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpProvider = subOpProvider;
    this.tagCache = tagCache;

    commits = new HashMap<>();
    openRepos = new HashMap<>();
  }

  private OpenRepo openRepo(Project.NameKey project)
      throws NoSuchProjectException, IOException {
    OpenRepo repo = openRepos.get(project);
    if (repo == null) {
      ProjectState projectState = projectCache.get(project);
      if (projectState == null) {
        throw new NoSuchProjectException(project);
      }
      try {
        repo = new OpenRepo(repoManager.openRepository(project), projectState);
      } catch (RepositoryNotFoundException e) {
        throw new NoSuchProjectException(project);
      }
      openRepos.put(project, repo);
    }
    return repo;
  }

  @Override
  public void close() {
    for (OpenRepo repo : openRepos.values()) {
      repo.close();
    }
  }

  private static Optional<SubmitRecord> findOkRecord(
      Collection<SubmitRecord> in) {
    if (in == null) {
      return Optional.absent();
    }
    return Iterables.tryFind(in, new Predicate<SubmitRecord>() {
      @Override
      public boolean apply(SubmitRecord input) {
        return input.status == SubmitRecord.Status.OK;
      }
    });
  }

  public static void checkSubmitRule(ChangeData cd)
      throws ResourceConflictException, OrmException {
    PatchSet patchSet = cd.currentPatchSet();
    if (patchSet == null) {
      throw new ResourceConflictException(
          "missing current patch set for change " + cd.getId());
    }
    List<SubmitRecord> results = cd.getSubmitRecords();
    if (results == null) {
      results = new SubmitRuleEvaluator(cd)
          .setPatchSet(patchSet)
          .evaluate();
      cd.setSubmitRecords(results);
    }
    if (findOkRecord(results).isPresent()) {
      // Rules supplied a valid solution.
      return;
    } else if (results.isEmpty()) {
      throw new IllegalStateException(String.format(
          "SubmitRuleEvaluator.evaluate for change %s " +
          "returned empty list for %s in %s",
          cd.getId(),
          patchSet.getId(),
          cd.change().getProject().get()));
    }

    for (SubmitRecord record : results) {
      switch (record.status) {
        case CLOSED:
          throw new ResourceConflictException(String.format(
              "change %s is closed", cd.getId()));

        case RULE_ERROR:
          throw new ResourceConflictException(String.format(
              "rule error for change %s: %s",
              cd.getId(), record.errorMessage));

        case NOT_READY:
          StringBuilder msg = new StringBuilder();
          msg.append(cd.getId() + ":");
          for (SubmitRecord.Label lbl : record.labels) {
            switch (lbl.status) {
              case OK:
              case MAY:
                continue;

              case REJECT:
                msg.append(" blocked by ").append(lbl.label);
                msg.append(";");
                continue;

              case NEED:
                msg.append(" needs ").append(lbl.label);
                msg.append(";");
                continue;

              case IMPOSSIBLE:
                msg.append(" needs ").append(lbl.label)
                .append(" (check project access)");
                msg.append(";");
                continue;

              default:
                throw new IllegalStateException(String.format(
                    "Unsupported SubmitRecord.Label %s for %s in %s in %s",
                    lbl.toString(),
                    patchSet.getId(),
                    cd.getId(),
                    cd.change().getProject().get()));
            }
          }
          throw new ResourceConflictException(msg.toString());

        default:
          throw new IllegalStateException(String.format(
              "Unsupported SubmitRecord %s for %s in %s",
              record,
              patchSet.getId().getId(),
              cd.change().getProject().get()));
      }
    }
    throw new IllegalStateException();
  }

  private void checkSubmitRulesAndState(ChangeSet cs)
      throws ResourceConflictException, OrmException {

    StringBuilder msgbuf = new StringBuilder();
    List<Change.Id> problemChanges = new ArrayList<>();
    for (ChangeData cd : cs.changes()) {
      try {
        if (cd.change().getStatus() != Change.Status.NEW){
          throw new ResourceConflictException("Change " +
              cd.change().getChangeId() + " is in state " +
              cd.change().getStatus());
        } else {
          checkSubmitRule(cd);
        }
      } catch (ResourceConflictException e) {
        msgbuf.append(e.getMessage() + "\n");
        problemChanges.add(cd.getId());
      }
    }
    String reason = msgbuf.toString();
    if (!reason.isEmpty()) {
        throw new ResourceConflictException("The change could not be " +
            "submitted because it depends on change(s) " +
            problemChanges.toString() + ", which could not be submitted " +
            "because:\n" + reason);
    }
  }

  private void updateSubmissionId(Change change) {
    Hasher h = Hashing.sha1().newHasher();
    h.putLong(Thread.currentThread().getId())
        .putUnencodedChars(MACHINE_ID);
    staticSubmissionId = h.hash().toString().substring(0, 8);
    submissionId = change.getId().get() + "-" + TimeUtil.nowMs() +
        "-" + staticSubmissionId;
  }

  public void merge(ReviewDb db, Change change, IdentifiedUser caller,
      boolean checkSubmitRules) throws NoSuchChangeException,
      OrmException, ResourceConflictException {
    updateSubmissionId(change);
    this.db = db;
    logDebug("Beginning integration of {}", change);
    try {
      ChangeSet cs = mergeSuperSet.completeChangeSet(db, change);
      reloadChanges(cs);
      logDebug("Calculated to merge {}", cs);
      if (checkSubmitRules) {
        logDebug("Checking submit rules and state");
        checkSubmitRulesAndState(cs);
      }
      try {
        integrateIntoHistory(cs, caller);
      } catch (IntegrationException e) {
        logError("Merge Conflict", e);
        throw new ResourceConflictException("Merge Conflict", e);
      }
    } catch (IOException e) {
      // Anything before the merge attempt is an error
      throw new OrmException(e);
    }
  }

  private static void reloadChanges(ChangeSet cs) throws OrmException {
    // Reload changes in case index was stale.
    for (ChangeData cd : cs.changes()) {
      cd.reloadChange();
    }
  }

  private void integrateIntoHistory(ChangeSet cs, IdentifiedUser caller)
      throws IntegrationException, NoSuchChangeException,
      ResourceConflictException {
    logDebug("Beginning merge attempt on {}", cs);
    Map<Branch.NameKey, BranchBatch> toSubmit = new HashMap<>();
    logDebug("Perform the merges");
    try {
      Multimap<Project.NameKey, Branch.NameKey> br = cs.branchesByProject();
      Multimap<Branch.NameKey, ChangeData> cbb = cs.changesByBranch();
      for (Project.NameKey project : br.keySet()) {
        OpenRepo or = openRepo(project);
        for (Branch.NameKey branch : br.get(project)) {
          BranchBatch submitting = validateChangeList(or, cbb.get(branch));
          toSubmit.put(branch, submitting);

          OpenBranch ob = or.getBranch(branch);
          SubmitStrategy strategy = createStrategy(or, branch,
              submitting.submitType(), ob.oldTip, caller);
          ob.mergeTip = preMerge(strategy, submitting.changes(), ob.oldTip);
          updateChangeStatus(ob, submitting.changes(), true, caller);
          or.ins.flush();
        }
      }
      Set<Branch.NameKey> done =
          Sets.newHashSetWithExpectedSize(cbb.keySet().size());
      logDebug("Write out the new branch tips");
      SubmoduleOp subOp = subOpProvider.get();
      for (Project.NameKey project : br.keySet()) {
        OpenRepo or = openRepo(project);
        for (Branch.NameKey branch : br.get(project)) {
          OpenBranch ob = or.getBranch(branch);
          boolean updated = updateBranch(or, branch, caller);

          BranchBatch submitting = toSubmit.get(branch);
          updateChangeStatus(ob, submitting.changes(), false, caller);
          updateSubmoduleSubscriptions(ob, subOp);
          if (updated) {
            fireRefUpdated(ob);
          }
          done.add(branch);
        }
      }

      updateSuperProjects(subOp, br.values());
      checkState(done.equals(cbb.keySet()), "programmer error: did not process"
          + " all branches in input set.\nExpected: %s\nActual: %s",
          done, cbb.keySet());
    } catch (NoSuchProjectException noProject) {
      logWarn("Project " + noProject.project() + " no longer exists, "
          + "abandoning open changes");
      abandonAllOpenChanges(noProject.project());
    } catch (OrmException e) {
      throw new IntegrationException("Cannot query the database", e);
    } catch (IOException e) {
      throw new IntegrationException("Cannot query the database", e);
    }
  }

  private MergeTip preMerge(SubmitStrategy strategy,
      List<ChangeData> submitted, CodeReviewCommit branchTip)
      throws IntegrationException, OrmException {
    logDebug("Running submit strategy {} for {} commits {}",
        strategy.getClass().getSimpleName(), submitted.size(), submitted);
    List<CodeReviewCommit> toMerge = new ArrayList<>(submitted.size());
    for (ChangeData cd : submitted) {
      CodeReviewCommit commit = commits.get(cd.change().getId());
      checkState(commit != null,
          "commit for %s not found by validateChangeList", cd.change().getId());
      toMerge.add(commit);
    }
    MergeTip mergeTip = strategy.run(branchTip, toMerge);
    logDebug("Produced {} new commits", strategy.getNewCommits().size());
    commits.putAll(strategy.getNewCommits());
    return mergeTip;
  }

  private SubmitStrategy createStrategy(OpenRepo or,
      Branch.NameKey destBranch, SubmitType submitType,
      CodeReviewCommit branchTip, IdentifiedUser caller)
      throws IntegrationException, NoSuchProjectException {
    return submitStrategyFactory.create(submitType, db, or.repo, or.rw, or.ins,
        or.canMergeFlag, getAlreadyAccepted(or, branchTip), destBranch, caller);
  }

  private Set<RevCommit> getAlreadyAccepted(OpenRepo or,
      CodeReviewCommit branchTip) throws IntegrationException {
    Set<RevCommit> alreadyAccepted = new HashSet<>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (Ref r : or.repo.getRefDatabase().getRefs(Constants.R_HEADS)
          .values()) {
        try {
          alreadyAccepted.add(or.rw.parseCommit(r.getObjectId()));
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    } catch (IOException e) {
      throw new IntegrationException(
          "Failed to determine already accepted commits.", e);
    }

    logDebug("Found {} existing heads", alreadyAccepted.size());
    return alreadyAccepted;
  }

  @AutoValue
  static abstract class BranchBatch {
    abstract SubmitType submitType();
    abstract List<ChangeData> changes();
  }

  private BranchBatch validateChangeList(OpenRepo or,
      Collection<ChangeData> submitted) throws IntegrationException {
    logDebug("Validating {} changes", submitted.size());
    List<ChangeData> toSubmit = new ArrayList<>(submitted.size());
    Multimap<ObjectId, PatchSet.Id> revisions = getRevisions(or, submitted);

    SubmitType submitType = null;
    ChangeData choseSubmitTypeFrom = null;
    for (ChangeData cd : submitted) {
      ChangeControl ctl;
      Change chg;
      try {
        ctl = cd.changeControl();
        chg = cd.change();
      } catch (OrmException e) {
        throw new IntegrationException("Failed to validate changes", e);
      }
      Change.Id changeId = cd.getId();
      if (chg.getStatus() != Change.Status.NEW) {
        logDebug("Change {} is not new: {}", changeId, chg.getStatus());
        continue;
      }
      if (chg.currentPatchSetId() == null) {
        logError("Missing current patch set on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        continue;
      }

      PatchSet ps;
      Branch.NameKey destBranch = chg.getDest();
      try {
        ps = cd.currentPatchSet();
      } catch (OrmException e) {
        throw new IntegrationException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        logError("Missing patch set or revision on change " + changeId);
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        continue;
      }

      String idstr = ps.getRevision().get();
      ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        logError("Invalid revision on patch set " + ps.getId());
        commits.put(changeId, CodeReviewCommit.noPatchSet(ctl));
        continue;
      }

      if (!revisions.containsEntry(id, ps.getId())) {
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.
        //
        logError("Revision " + idstr + " of patch set " + ps.getId()
            + " does not match " + ps.getId().toRefName());
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        continue;
      }

      CodeReviewCommit commit;
      try {
        commit = or.rw.parseCommit(id);
      } catch (IOException e) {
        logError("Invalid commit " + idstr + " on patch set " + ps.getId(), e);
        commits.put(changeId, CodeReviewCommit.revisionGone(ctl));
        continue;
      }

      // TODO(dborowitz): Consider putting ChangeData in CodeReviewCommit.
      commit.setControl(ctl);
      commit.setPatchsetId(ps.getId());
      commits.put(changeId, commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(
            or.repo, commit, or.project, destBranch, ps.getId());
      } catch (MergeValidationException mve) {
        logDebug("Revision {} of patch set {} failed validation: {}",
            idstr, ps.getId(), mve.getStatus());
        commit.setStatusCode(mve.getStatus());
        continue;
      }

      SubmitType st = getSubmitType(cd, ps);
      if (st == null) {
        logError("No submit type for revision " + idstr + " of patch set "
            + ps.getId());
        throw new IntegrationException(
            "No submit type for change " + cd.getId());
      }
      if (submitType == null) {
        submitType = st;
        choseSubmitTypeFrom = cd;
      } else if (st != submitType) {
        throw new IntegrationException(String.format(
            "Change %s has submit type %s, but previously chose submit type %s "
            + "from change %s in the same batch",
            cd.getId(), st, submitType, choseSubmitTypeFrom.getId()));
      }
      commit.add(or.canMergeFlag);
      toSubmit.add(cd);
    }
    logDebug("Submitting on this run: {}", toSubmit);
    return new AutoValue_MergeOp_BranchBatch(submitType, toSubmit);
  }

  private Multimap<ObjectId, PatchSet.Id> getRevisions(OpenRepo or,
      Collection<ChangeData> cds) throws IntegrationException {
    try {
      List<String> refNames = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        Change c = cd.change();
        if (c != null) {
          refNames.add(c.currentPatchSetId().toRefName());
        }
      }
      Multimap<ObjectId, PatchSet.Id> revisions =
          HashMultimap.create(cds.size(), 1);
      for (Map.Entry<String, Ref> e : or.repo.getRefDatabase().exactRef(
          refNames.toArray(new String[refNames.size()])).entrySet()) {
        revisions.put(
            e.getValue().getObjectId(), PatchSet.Id.fromRef(e.getKey()));
      }
      return revisions;
    } catch (IOException | OrmException e) {
      throw new IntegrationException("Failed to validate changes", e);
    }
  }

  private SubmitType getSubmitType(ChangeData cd, PatchSet ps) {
    try {
      SubmitTypeRecord r = new SubmitRuleEvaluator(cd).setPatchSet(ps)
          .getSubmitType();
      if (r.status != SubmitTypeRecord.Status.OK) {
        logError("Failed to get submit type for " + cd.getId());
        return null;
      }
      return r.type;
    } catch (OrmException e) {
      logError("Failed to get submit type for " + cd.getId(), e);
      return null;
    }
  }

  private boolean updateBranch(OpenRepo or, Branch.NameKey destBranch,
      IdentifiedUser caller) throws IntegrationException {
    OpenBranch ob = or.getBranch(destBranch);
    CodeReviewCommit currentTip = ob.getCurrentTip();
    if (Objects.equals(ob.oldTip, currentTip)) {
      if (currentTip != null) {
        logDebug("Branch already at merge tip {}, no update to perform",
            currentTip.name());
      } else {
        logDebug("Both branch and merge tip are nonexistent, no update");
      }
      return false;
    } else if (currentTip == null) {
      logDebug("No merge tip, no update to perform");
      return false;
    }

    if (RefNames.REFS_CONFIG.equals(ob.update.getName())) {
      logDebug("Loading new configuration from {}", RefNames.REFS_CONFIG);
      try {
        ProjectConfig cfg = new ProjectConfig(or.getProjectName());
        cfg.load(or.repo, currentTip);
      } catch (Exception e) {
        throw new IntegrationException("Submit would store invalid"
            + " project configuration " + currentTip.name() + " for "
            + or.getProjectName(), e);
      }
    }

    ob.update.setRefLogIdent(
        identifiedUserFactory.create(caller.getAccountId()).newRefLogIdent());
    ob.update.setForceUpdate(false);
    ob.update.setNewObjectId(currentTip);
    ob.update.setRefLogMessage("merged", true);
    try {
      RefUpdate.Result result = ob.update.update(or.rw);
      logDebug("Update of {}: {}..{} returned status {}",
          ob.update.getName(), ob.update.getOldObjectId(),
          ob.update.getNewObjectId(), result);
      switch (result) {
        case NEW:
        case FAST_FORWARD:
          if (ob.update.getResult() == RefUpdate.Result.FAST_FORWARD) {
            tagCache.updateFastForward(destBranch.getParentKey(),
                ob.update.getName(),
                ob.update.getOldObjectId(),
                currentTip);
          }

          if (RefNames.REFS_CONFIG.equals(ob.update.getName())) {
            Project p = or.project.getProject();
            projectCache.evict(p);
            or.project = projectCache.get(p.getNameKey());
            repoManager.setProjectDescription(
                p.getNameKey(), p.getDescription());
          }

          return true;

        case LOCK_FAILURE:
          throw new IntegrationException(
              "Failed to lock " + ob.update.getName());
        default:
          throw new IOException(
              ob.update.getResult().name() + '\n' + ob.update);
      }
    } catch (IOException e) {
      throw new IntegrationException("Cannot update " + ob.update.getName(), e);
    }
  }

  private void fireRefUpdated(OpenBranch ob) {
    logDebug("Firing ref updated hooks for {}", ob.name);
    gitRefUpdated.fire(ob.name.getParentKey(), ob.update);
    hooks.doRefUpdatedHook(ob.name, ob.update,
        getAccount(ob.mergeTip.getCurrentTip()));
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

  private void updateChangeStatus(OpenBranch ob, List<ChangeData> submitted,
      boolean dryRun, IdentifiedUser caller) throws NoSuchChangeException,
      IntegrationException, ResourceConflictException, OrmException {
    if (!dryRun) {
      logDebug("Updating change status for {} changes", submitted.size());
    } else {
      logDebug("Checking change state for {} changes in a dry run",
          submitted.size());
    }

    for (ChangeData cd : submitted) {
      Change c = cd.change();
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

      if (!dryRun) {
        try {
          setApproval(cd, caller);
        } catch (IOException e) {
          throw new OrmException(e);
        }
      }

      String txt = s.getMessage();
      logDebug("Status of change {} ({}) on {}: {}", c.getId(), commit.name(),
          c.getDest(), s);
      // If mergeTip is null merge failed and mergeResultRev will not be read.
      ObjectId mergeResultRev = ob.mergeTip != null
          ? ob.mergeTip.getMergeResults().get(commit) : null;
      // The change notes must be forcefully reloaded so that the SUBMIT
      // approval that we added earlier is visible
      commit.notes().reload();
      try {
        ChangeMessage msg;
        switch (s) {
          case CLEAN_MERGE:
            if (!dryRun) {
              setMerged(c, message(c, txt + getByAccountName(commit)),
                  mergeResultRev);
            }
            break;

          case CLEAN_REBASE:
          case CLEAN_PICK:
            if (!dryRun) {
              setMerged(c, message(c, txt + " as " + commit.name()
                  + getByAccountName(commit)), mergeResultRev);
            }
            break;

          case ALREADY_MERGED:
            if (!dryRun) {
              setMerged(c, null, mergeResultRev);
            }
            break;

          case PATH_CONFLICT:
          case REBASE_MERGE_CONFLICT:
          case MANUAL_RECURSIVE_MERGE:
          case CANNOT_CHERRY_PICK_ROOT:
          case NOT_FAST_FORWARD:
          case INVALID_PROJECT_CONFIGURATION:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_PERMITTED:
          case INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_EDITABLE:
          case INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND:
          case INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT:
          case SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN:
            setNew(commit.notes(), message(c, txt));
            throw new ResourceConflictException("Cannot merge " + commit.name()
                + "\n" + s.getMessage());

          case MISSING_DEPENDENCY:
            logDebug("Change {} is missing dependency", c.getId());
            throw new IntegrationException(
                "Cannot merge " + commit.name() + "\n" + s.getMessage());

          case REVISION_GONE:
            logDebug("Commit not found for change {}", c.getId());
            msg = new ChangeMessage(
                new ChangeMessage.Key(
                    c.getId(),
                    ChangeUtil.messageUUID(db)),
                null,
                TimeUtil.nowTs(),
                c.currentPatchSetId());
            msg.setMessage("Failed to read commit for this patch set");
            setNew(commit.notes(), msg);
            throw new IntegrationException(msg.getMessage());

          default:
            msg = message(c, "Unspecified merge failure: " + s.name());
            setNew(commit.notes(), msg);
            throw new IntegrationException(msg.getMessage());
        }
      } catch (OrmException | IOException err) {
        logWarn("Error updating change status for " + c.getId(), err);
      }
    }
  }

  private void updateSubmoduleSubscriptions(OpenBranch ob, SubmoduleOp subOp) {
    CodeReviewCommit branchTip = ob.oldTip;
    MergeTip mergeTip = ob.mergeTip;
    if (mergeTip != null
        && (branchTip == null || branchTip != mergeTip.getCurrentTip())) {
      logDebug("Updating submodule subscriptions for branch {}", ob.name);
      try {
        subOp.updateSubmoduleSubscriptions(db, ob.name);
      } catch (SubmoduleException e) {
        logError("The submodule subscriptions were not updated according"
            + "to the .gitmodules files", e);
      }
    }
  }

  private void updateSuperProjects(SubmoduleOp subOp,
      Collection<Branch.NameKey> branches) {
    logDebug("Updating superprojects");
    try {
      subOp.updateSuperProjects(db, branches);
    } catch (SubmoduleException e) {
      logError("The gitlinks were not updated according to the "
          + "subscriptions", e);
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
    final PatchSetApproval submitter;
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
    indexer.index(db, c);

    try {
      mergedSenderFactory.create(
          c.getId(),
          submitter != null ? submitter.getAccountId() : null).sendAsync();
    } catch (Exception e) {
      log.error("Cannot email merged notification for " + c.getId(), e);
    }
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
        c.setSubmissionId(submissionId);
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

  private void setApproval(ChangeData cd, IdentifiedUser user)
      throws OrmException, IOException {
    Timestamp timestamp = TimeUtil.nowTs();
    ChangeControl control = cd.changeControl();
    PatchSet.Id psId = cd.currentPatchSet().getId();
    PatchSet.Id psIdNewRev = commits.get(cd.change().getId())
        .change().currentPatchSetId();

    logDebug("Add approval for " + cd + " from user " + user);
    ChangeUpdate update = updateFactory.create(control, timestamp);
    update.putReviewer(user.getAccountId(), REVIEWER);
    Optional<SubmitRecord> okRecord = findOkRecord(cd.getSubmitRecords());
    if (okRecord.isPresent()) {
      update.merge(ImmutableList.of(okRecord.get()));
    }
    db.changes().beginTransaction(cd.change().getId());
    try {
      BatchMetaDataUpdate batch = approve(control, psId, user,
          update, timestamp);
      batch.write(update, new CommitBuilder());

      // If the submit strategy created a new revision (rebase, cherry-pick)
      // approve that as well
      if (!psIdNewRev.equals(psId)) {
        update.setPatchSetId(psId);
        update.commit();
        // Create a new ChangeUpdate instance because we need to store meta data
        // on another patch set (psIdNewRev).
        update = updateFactory.create(control, timestamp);
        batch = approve(control, psIdNewRev, user,
            update, timestamp);
        // Write update commit after all normalized label commits.
        batch.write(update, new CommitBuilder());
      }
      db.commit();
    } finally {
      db.rollback();
    }
    update.commit();
    indexer.index(db, cd.change());
  }

  private BatchMetaDataUpdate approve(ChangeControl control, PatchSet.Id psId,
      IdentifiedUser user, ChangeUpdate update, Timestamp timestamp)
          throws OrmException {
    Map<PatchSetApproval.Key, PatchSetApproval> byKey = Maps.newHashMap();
    for (PatchSetApproval psa :
      approvalsUtil.byPatchSet(db, control, psId)) {
      if (!byKey.containsKey(psa.getKey())) {
        byKey.put(psa.getKey(), psa);
      }
    }

    PatchSetApproval submit = new PatchSetApproval(
          new PatchSetApproval.Key(
              psId,
              user.getAccountId(),
              LabelId.SUBMIT),
              (short) 1, TimeUtil.nowTs());
    byKey.put(submit.getKey(), submit);
    submit.setValue((short) 1);
    submit.setGranted(timestamp);

    // Flatten out existing approvals for this patch set based upon the current
    // permissions. Once the change is closed the approvals are not updated at
    // presentation view time, except for zero votes used to indicate a reviewer
    // was added. So we need to make sure votes are accurate now. This way if
    // permissions get modified in the future, historical records stay accurate.
    LabelNormalizer.Result normalized =
        labelNormalizer.normalize(control, byKey.values());

    // TODO(dborowitz): Don't use a label in notedb; just check when status
    // change happened.
    update.putApproval(submit.getLabel(), submit.getValue());
    logDebug("Adding submit label " + submit);

    db.patchSetApprovals().upsert(normalized.getNormalized());
    db.patchSetApprovals().delete(normalized.deleted());

    try {
      return saveToBatch(control, update, normalized, timestamp);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private BatchMetaDataUpdate saveToBatch(ChangeControl ctl,
      ChangeUpdate callerUpdate, LabelNormalizer.Result normalized,
      Timestamp timestamp) throws IOException {
    Table<Account.Id, String, Optional<Short>> byUser = HashBasedTable.create();
    for (PatchSetApproval psa : normalized.updated()) {
      byUser.put(psa.getAccountId(), psa.getLabel(),
          Optional.of(psa.getValue()));
    }
    for (PatchSetApproval psa : normalized.deleted()) {
      byUser.put(psa.getAccountId(), psa.getLabel(), Optional.<Short> absent());
    }

    BatchMetaDataUpdate batch = callerUpdate.openUpdate();
    for (Account.Id accountId : byUser.rowKeySet()) {
      if (!accountId.equals(callerUpdate.getUser().getAccountId())) {
        ChangeUpdate update = updateFactory.create(
            ctl.forUser(identifiedUserFactory.create(accountId)), timestamp);
        update.setSubject("Finalize approvals at submit");
        putApprovals(update, byUser.row(accountId));

        CommitBuilder commit = new CommitBuilder();
        commit.setCommitter(new PersonIdent(serverIdent, timestamp));
        batch.write(update, commit);
      }
    }

    putApprovals(callerUpdate,
        byUser.row(callerUpdate.getUser().getAccountId()));
    return batch;
  }

  private static void putApprovals(ChangeUpdate update,
      Map<String, Optional<Short>> approvals) {
    for (Map.Entry<String, Optional<Short>> e : approvals.entrySet()) {
      if (e.getValue().isPresent()) {
        update.putApproval(e.getKey(), e.getValue().get());
      } else {
        update.removeApproval(e.getKey());
      }
    }
  }

  private ChangeControl changeControl(Change c) throws NoSuchChangeException {
    return changeControlFactory.controlFor(
        c, identifiedUserFactory.create(c.getOwner()));
  }

  private void setNew(ChangeNotes notes, final ChangeMessage msg)
      throws NoSuchChangeException, IOException {
    Change c = notes.getChange();

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
              c.setStatus(Change.Status.NEW);
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
    indexer.index(db, change);

    PatchSetApproval submitter = null;
    try {
      submitter = approvalsUtil.getSubmitter(
          db, notes, notes.getChange().currentPatchSetId());
    } catch (Exception e) {
      logError("Cannot get submitter for change " + notes.getChangeId(), e);
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

  private void abandonAllOpenChanges(Project.NameKey destProject)
      throws NoSuchChangeException {
    try {
      for (ChangeData cd : internalChangeQuery.byProjectOpen(destProject)) {
        abandonOneChange(cd.change());
      }
    } catch (IOException | OrmException e) {
      logWarn("Cannot abandon changes for deleted project ", e);
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
      log.debug("[" + submissionId + "]" + msg, args);
    }
  }

  private void logWarn(String msg, Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn("[" + submissionId + "]" + msg, t);
    }
  }

  private void logWarn(String msg) {
    if (log.isWarnEnabled()) {
      log.warn("[" + submissionId + "]" + msg);
    }
  }

  private void logError(String msg, Throwable t) {
    if (log.isErrorEnabled()) {
      if (t != null) {
        log.error("[" + submissionId + "]" + msg, t);
      } else {
        log.error("[" + submissionId + "]" + msg);
      }
    }
  }

  private void logError(String msg) {
    logError(msg, null);
  }
}
