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
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
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
import com.google.gerrit.server.git.strategy.CommitMergeStatus;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.index.ChangeIndexer;
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

  public static class CommitStatus {
    private final Map<Change.Id, CodeReviewCommit> commits = new HashMap<>();
    private final Multimap<Change.Id, String> problems =
        MultimapBuilder.treeKeys(
          Ordering.natural().onResultOf(new Function<Change.Id, Integer>() {
            @Override
            public Integer apply(Change.Id in) {
              return in.get();
            }
          })).arrayListValues(1).build();

    public CodeReviewCommit get(Change.Id changeId) {
      return commits.get(changeId);
    }

    public void put(CodeReviewCommit c) {
      commits.put(c.change().getId(), c);
    }

    public void problem(Change.Id id, String problem) {
      problems.put(id, problem);
    }

    public void logProblem(Change.Id id, Throwable t) {
      String msg = "Error reading change";
      log.error(msg + " " + id, t);
      problems.put(id, msg);
    }

    public void logProblem(Change.Id id, String msg) {
      log.error(msg + " " + id);
      problems.put(id, msg);
    }

    boolean isOk() {
      return problems.isEmpty();
    }

    ImmutableMultimap<Change.Id, String> getProblems() {
      return ImmutableMultimap.copyOf(problems);
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
  private final CommitStatus commits;

  private final Map<Project.NameKey, OpenRepo> openRepos;

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
  private Timestamp ts;
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

    openRepos = new HashMap<>();
    commits = new CommitStatus();
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
      results = new SubmitRuleEvaluator(cd).evaluate();
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
          throw new ResourceConflictException("change is closed");

        case RULE_ERROR:
          throw new ResourceConflictException(
              "submit rule error: " + record.errorMessage);

        case NOT_READY:
          throw new ResourceConflictException(
              describeLabels(cd, record.labels));

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

  private static String describeLabels(ChangeData cd,
      List<SubmitRecord.Label> labels) throws OrmException {
    List<String> labelResults = new ArrayList<>();
    for (SubmitRecord.Label lbl : labels) {
      switch (lbl.status) {
        case OK:
        case MAY:
          break;

        case REJECT:
          labelResults.add("blocked by " + lbl.label);
          break;

        case NEED:
          labelResults.add("needs " + lbl.label);
          break;

        case IMPOSSIBLE:
          labelResults.add(
              "needs " + lbl.label + " (check project access)");
          break;

        default:
          throw new IllegalStateException(String.format(
              "Unsupported SubmitRecord.Label %s for %s in %s",
              lbl,
              cd.change().currentPatchSetId(),
              cd.change().getProject()));
      }
    }
    return Joiner.on("; ").join(labelResults);
  }

  private void checkSubmitRulesAndState(ChangeSet cs) {
    for (ChangeData cd : cs.changes()) {
      try {
        if (cd.change().getStatus() != Change.Status.NEW) {
          commits.problem(cd.getId(), "Change " + cd.getId() + " is "
              + cd.change().getStatus().toString().toLowerCase());
        } else {
          checkSubmitRule(cd);
        }
      } catch (ResourceConflictException e) {
        commits.problem(cd.getId(), e.getMessage());
      } catch (OrmException e) {
        String msg = "Error checking submit rules for change";
        log.warn(msg + " " + cd.getId(), e);
        commits.problem(cd.getId(), msg);
      }
    }
  }

  private void updateSubmissionId(Change change) {
    Hasher h = Hashing.sha1().newHasher();
    h.putLong(Thread.currentThread().getId())
        .putUnencodedChars(MACHINE_ID);
    ts = TimeUtil.nowTs();
    submissionId = change.getId().get() + "-" + ts.getTime() +
        "-" + h.hash().toString().substring(0, 8);
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
        failFast(cs); // Done checks that don't involve opening repo.
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

  private void failFast(ChangeSet cs) throws ResourceConflictException {
    if (commits.isOk()) {
      return;
    }
    String msg = "Failed to submit " + cs.size() + " change"
        + (cs.size() > 1 ? "s" : "") + " due to the following problems:\n";
    Multimap<Change.Id, String> problems = commits.getProblems();
    List<String> ps = new ArrayList<>(problems.keySet().size());
    for (Change.Id id : problems.keySet()) {
      ps.add("Change " + id + ": " + Joiner.on("; ").join(problems.get(id)));
    }
    throw new ResourceConflictException(msg + Joiner.on('\n').join(ps));
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
      for (Branch.NameKey branch : cbb.keySet()) {
        OpenRepo or = openRepo(branch.getParentKey());
        toSubmit.put(branch, validateChangeList(or, cbb.get(branch)));
      }
      failFast(cs); // Done checks that don't involve running submit strategies.

      for (Branch.NameKey branch : cbb.keySet()) {
        OpenRepo or = openRepo(branch.getParentKey());
        OpenBranch ob = or.getBranch(branch);
        BranchBatch submitting = toSubmit.get(branch);
        SubmitStrategy strategy = createStrategy(or, branch,
            submitting.submitType(), ob.oldTip, caller);
        ob.mergeTip = preMerge(strategy, submitting.changes(), ob.oldTip);
      }
      checkMergeStrategyResults(cs, toSubmit.values());
      for (Project.NameKey project : br.keySet()) {
        openRepo(project).ins.flush();
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
          updateChangeStatus(ob, submitting.changes(), caller);
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
      CodeReviewCommit commit = commits.get(cd.getId());
      checkState(commit != null,
          "commit for %s not found by validateChangeList", cd.change().getId());
      toMerge.add(commit);
    }
    return strategy.run(branchTip, toMerge);
  }

  private SubmitStrategy createStrategy(OpenRepo or,
      Branch.NameKey destBranch, SubmitType submitType,
      CodeReviewCommit branchTip, IdentifiedUser caller)
      throws IntegrationException, NoSuchProjectException {
    return submitStrategyFactory.create(submitType, db, or.repo, or.rw, or.ins,
        or.canMergeFlag, getAlreadyAccepted(or, branchTip), destBranch, caller,
        commits);
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
      Change.Id changeId = cd.getId();
      ChangeControl ctl;
      Change chg;
      try {
        ctl = cd.changeControl();
        chg = cd.change();
      } catch (OrmException e) {
        commits.logProblem(changeId, e);
        continue;
      }
      if (chg.currentPatchSetId() == null) {
        String msg = "Missing current patch set on change";
        logError(msg + " " + changeId);
        commits.problem(changeId, msg);
        continue;
      }

      PatchSet ps;
      Branch.NameKey destBranch = chg.getDest();
      try {
        ps = cd.currentPatchSet();
      } catch (OrmException e) {
        commits.logProblem(changeId, e);
        continue;
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        commits.logProblem(changeId, "Missing patch set or revision on change");
        continue;
      }

      String idstr = ps.getRevision().get();
      ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException e) {
        commits.logProblem(changeId, e);
        continue;
      }

      if (!revisions.containsEntry(id, ps.getId())) {
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.
        //
        commits.logProblem(changeId, "Revision " + idstr + " of patch set "
            + ps.getPatchSetId() + " does not match " + ps.getId().toRefName()
            + " for change");
        continue;
      }

      CodeReviewCommit commit;
      try {
        commit = or.rw.parseCommit(id);
      } catch (IOException e) {
        commits.logProblem(changeId, e);
        continue;
      }

      // TODO(dborowitz): Consider putting ChangeData in CodeReviewCommit.
      commit.setControl(ctl);
      commit.setPatchsetId(ps.getId());
      commits.put(commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(
            or.repo, commit, or.project, destBranch, ps.getId());
      } catch (MergeValidationException mve) {
        commits.problem(changeId, mve.getMessage());
        continue;
      }

      SubmitType st = getSubmitType(cd);
      if (st == null) {
        commits.logProblem(changeId, "No submit type for change");
        continue;
      }
      if (submitType == null) {
        submitType = st;
        choseSubmitTypeFrom = cd;
      } else if (st != submitType) {
        commits.problem(changeId, String.format(
            "Change has submit type %s, but previously chose submit type %s "
            + "from change %s in the same batch",
            st, submitType, choseSubmitTypeFrom.getId()));
        continue;
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

  private SubmitType getSubmitType(ChangeData cd) {
    try {
      SubmitTypeRecord str = cd.submitTypeRecord();
      return str.isOk() ? str.type : null;
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

  private Iterable<ChangeData> flattenBatches(Collection<BranchBatch> batches) {
    return FluentIterable.from(batches)
        .transformAndConcat(new Function<BranchBatch, List<ChangeData>>() {
          @Override
          public List<ChangeData> apply(BranchBatch batch) {
            return batch.changes();
          }
        });
  }

  private void checkMergeStrategyResults(ChangeSet cs,
      Collection<BranchBatch> batches) throws ResourceConflictException {
    for (ChangeData cd : flattenBatches(batches)) {
      Change.Id id = cd.getId();
      CodeReviewCommit commit = commits.get(id);
      CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        commits.problem(id,
            "internal error: change not processed by merge strategy");
        continue;
      }
      switch (s) {
        case CLEAN_MERGE:
        case CLEAN_REBASE:
        case CLEAN_PICK:
        case ALREADY_MERGED:
          break; // Merge strategy accepted this change.

        case PATH_CONFLICT:
        case REBASE_MERGE_CONFLICT:
        case MANUAL_RECURSIVE_MERGE:
        case CANNOT_CHERRY_PICK_ROOT:
        case NOT_FAST_FORWARD:
          // TODO(dborowitz): Reformat these messages to be more appropriate for
          // short problem descriptions.
          commits.problem(id,
              CharMatcher.is('\n').collapseFrom(s.getMessage(), ' '));
          break;

        case MISSING_DEPENDENCY:
          commits.problem(id, "depends on change that was not submitted");
          break;

        default:
          commits.problem(id, "unspecified merge failure: " + s);
          break;
      }
    }
    failFast(cs);
  }

  private void updateChangeStatus(OpenBranch ob, List<ChangeData> submitted,
      IdentifiedUser caller) throws ResourceConflictException {
    List<Change.Id> problemChanges = new ArrayList<>(submitted.size());
    logDebug("Updating change status for {} changes", submitted.size());

    for (ChangeData cd : submitted) {
      Change.Id id = cd.getId();
      try {
        Change c = cd.change();
        CodeReviewCommit commit = commits.get(id);
        CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
        logDebug("Status of change {} ({}) on {}: {}", id, commit.name(),
            c.getDest(), s);
        checkState(s != null,
            "status not set for change %s; expected to previously fail fast",
            id);
        setApproval(cd, caller);

        ObjectId mergeResultRev = ob.mergeTip != null
            ? ob.mergeTip.getMergeResults().get(commit) : null;
        String txt = s.getMessage();

        // The change notes must be forcefully reloaded so that the SUBMIT
        // approval that we added earlier is visible
        commit.notes().reload();
        if (s == CommitMergeStatus.CLEAN_MERGE) {
          setMerged(c, message(c, txt + getByAccountName(commit)),
              mergeResultRev);
        } else if (s == CommitMergeStatus.CLEAN_REBASE
            || s == CommitMergeStatus.CLEAN_PICK) {
          setMerged(c, message(c, txt + " as " + commit.name()
              + getByAccountName(commit)), mergeResultRev);
        } else if (s == CommitMergeStatus.ALREADY_MERGED) {
          setMerged(c, null, mergeResultRev);
        } else {
          throw new IllegalStateException("unexpected status " + s +
              " for change " + c.getId() + "; expected to previously fail fast");
        }
      } catch (OrmException | IOException err) {
        logWarn("Error updating change status for " + id, err);
        problemChanges.add(id);
      }
    }

    if (problemChanges.isEmpty()) {
      return;
    }
    StringBuilder msg = new StringBuilder("Error updating status of change");
    if (problemChanges.size() == 1) {
      msg.append(' ').append(problemChanges.iterator().next());
    } else {
      msg.append('s').append(Joiner.on(", ").join(problemChanges));
    }
    throw new ResourceConflictException(msg.toString());
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
