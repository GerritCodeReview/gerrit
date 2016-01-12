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

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.strategy.SubmitStrategyListener;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
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
import java.util.LinkedHashSet;
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

  private class OpenRepo {
    final Repository repo;
    final CodeReviewRevWalk rw;
    final RevFlag canMergeFlag;
    final ObjectInserter ins;

    ProjectState project;
    BatchUpdate update;

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

    BatchUpdate getUpdate() {
      if (update == null) {
        update = batchUpdateFactory.create(db, getProjectName(), caller, ts);
        update.setRepository(repo, rw, ins);
      }
      return update;
    }

    void close() {
      if (update != null) {
        update.close();
      }
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
  }

  public static class CommitStatus {
    private final ChangeSet cs;
    private final Map<Change.Id, CodeReviewCommit> commits;
    private final Multimap<Change.Id, String> problems;

    private CommitStatus(ChangeSet cs) {
      this.cs = cs;
      commits = new HashMap<>();
      problems = MultimapBuilder.treeKeys(
          Ordering.natural().onResultOf(new Function<Change.Id, Integer>() {
            @Override
            public Integer apply(Change.Id in) {
              return in.get();
            }
          })).arrayListValues(1).build();
    }

    public ChangeSet getChanges() {
      return cs;
    }

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

    public boolean isOk() {
      return problems.isEmpty();
    }

    public ImmutableMultimap<Change.Id, String> getProblems() {
      return ImmutableMultimap.copyOf(problems);
    }

    public void maybeFailVerbose() throws ResourceConflictException {
      if (isOk()) {
        return;
      }
      String msg = "Failed to submit " + cs.size() + " change"
          + (cs.size() > 1 ? "s" : "") + " due to the following problems:\n";
      List<String> ps = new ArrayList<>(problems.keySet().size());
      for (Change.Id id : problems.keySet()) {
        ps.add("Change " + id + ": " + Joiner.on("; ").join(problems.get(id)));
      }
      throw new ResourceConflictException(msg + Joiner.on('\n').join(ps));
    }

    public void maybeFail(String msgPrefix) throws ResourceConflictException {
      if (isOk()) {
        return;
      }
      StringBuilder msg = new StringBuilder(msgPrefix).append(" of change");
      Set<Change.Id> ids = problems.keySet();
      if (problems.size() == 1) {
        msg.append(" ").append(ids.iterator().next());
      } else {
        msg.append("s ").append(Joiner.on(", ").join(ids));
      }
      throw new ResourceConflictException(msg.toString());
    }
  }

  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeIndexer indexer;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final MergeSuperSet mergeSuperSet;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final ProjectCache projectCache;
  private final InternalChangeQuery internalChangeQuery;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final Provider<SubmoduleOp> subOpProvider;

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
  private IdentifiedUser caller;

  private CommitStatus commits;
  private ReviewDb db;

  @Inject
  MergeOp(ChangeControl.GenericFactory changeControlFactory,
      ChangeIndexer indexer,
      ChangeMessagesUtil cmUtil,
      ChangeUpdate.Factory changeUpdateFactory,
      BatchUpdate.Factory batchUpdateFactory,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      MergeSuperSet mergeSuperSet,
      MergeValidators.Factory mergeValidatorsFactory,
      ProjectCache projectCache,
      InternalChangeQuery internalChangeQuery,
      SubmitStrategyFactory submitStrategyFactory,
      Provider<SubmoduleOp> subOpProvider) {
    this.changeControlFactory = changeControlFactory;
    this.indexer = indexer;
    this.cmUtil = cmUtil;
    this.changeUpdateFactory = changeUpdateFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.repoManager = repoManager;
    this.identifiedUserFactory = identifiedUserFactory;
    this.mergeSuperSet = mergeSuperSet;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.projectCache = projectCache;
    this.internalChangeQuery = internalChangeQuery;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpProvider = subOpProvider;

    openRepos = new HashMap<>();
  }

  private OpenRepo getRepo(Project.NameKey project) {
    OpenRepo or = openRepos.get(project);
    checkState(or != null, "repo not yet opened: %s", project);
    return or;
  }

  private void openRepo(Project.NameKey project)
      throws NoSuchProjectException, IOException {
    checkState(!openRepos.containsKey(project),
        "repo already opened: %s", project);
    ProjectState projectState = projectCache.get(project);
    if (projectState == null) {
      throw new NoSuchProjectException(project);
    }
    try {
      OpenRepo or =
          new OpenRepo(repoManager.openRepository(project), projectState);
      openRepos.put(project, or);
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchProjectException(project);
    }
  }

  @Override
  public void close() {
    for (OpenRepo repo : openRepos.values()) {
      repo.close();
    }
  }

  public static Optional<SubmitRecord> findOkRecord(
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
      boolean checkSubmitRules) throws OrmException, RestApiException {
    this.caller = caller;
    updateSubmissionId(change);
    this.db = db;
    logDebug("Beginning integration of {}", change);
    try {
      ChangeSet cs = mergeSuperSet.completeChangeSet(db, change);
      this.commits = new CommitStatus(cs);
      reloadChanges(cs);
      logDebug("Calculated to merge {}", cs);
      if (checkSubmitRules) {
        logDebug("Checking submit rules and state");
        checkSubmitRulesAndState(cs);
        failFast(cs); // Done checks that don't involve opening repo.
      }
      try {
        integrateIntoHistory(cs);
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

  private void integrateIntoHistory(ChangeSet cs)
      throws IntegrationException, RestApiException {
    logDebug("Beginning merge attempt on {}", cs);
    Map<Branch.NameKey, BranchBatch> toSubmit = new HashMap<>();
    logDebug("Perform the merges");
    try {
      Multimap<Project.NameKey, Branch.NameKey> br = cs.branchesByProject();
      Multimap<Branch.NameKey, ChangeData> cbb = cs.changesByBranch();
      openRepos(br.keySet());
      for (Branch.NameKey branch : cbb.keySet()) {
        OpenRepo or = getRepo(branch.getParentKey());
        toSubmit.put(branch, validateChangeList(or, cbb.get(branch)));
      }
      failFast(cs); // Done checks that don't involve running submit strategies.

      Collection<Branch.NameKey> branches = cbb.keySet();
      List<SubmitStrategy> strategies = new ArrayList<>(branches.size());
      for (Branch.NameKey branch : cbb.keySet()) {
        OpenRepo or = getRepo(branch.getParentKey());
        OpenBranch ob = or.getBranch(branch);
        BranchBatch submitting = toSubmit.get(branch);
        Set<CodeReviewCommit> commitsToSubmit = commits(submitting.changes());
        ob.mergeTip = new MergeTip(ob.oldTip, commitsToSubmit);
        SubmitStrategy strategy = createStrategy(or, ob.mergeTip, branch,
            submitting.submitType(), ob.oldTip);
        strategies.add(strategy);
        strategy.addOps(or.getUpdate(), commitsToSubmit);
      }

      BatchUpdate.execute(
          batchUpdates(br.keySet()),
          new SubmitStrategyListener(strategies, commits));

      SubmoduleOp subOp = subOpProvider.get();
      for (Branch.NameKey branch : cbb.keySet()) {
        OpenBranch ob = getRepo(branch.getParentKey()).getBranch(branch);
        updateSubmoduleSubscriptions(ob, subOp);
      }
      updateSuperProjects(subOp, br.values());
    } catch (UpdateException | OrmException e) {
      throw new IntegrationException("Error submitting changes", e);
    }
  }

  private List<BatchUpdate> batchUpdates(Collection<Project.NameKey> projects) {
    List<BatchUpdate> updates = new ArrayList<>(projects.size());
    for (Project.NameKey project : projects) {
      updates.add(getRepo(project).getUpdate());
    }
    return updates;
  }

  private Set<CodeReviewCommit> commits(List<ChangeData> cds) throws OrmException {
    LinkedHashSet<CodeReviewCommit> result =
        Sets.newLinkedHashSetWithExpectedSize(cds.size());
    for (ChangeData cd : cds) {
      CodeReviewCommit commit = commits.get(cd.getId());
      checkState(commit != null,
          "commit for %s not found by validateChangeList", cd.change().getId());
      result.add(commit);
    }
    return result;
  }

  private SubmitStrategy createStrategy(OpenRepo or,
      MergeTip mergeTip, Branch.NameKey destBranch, SubmitType submitType,
      CodeReviewCommit branchTip) throws IntegrationException {
    return submitStrategyFactory.create(submitType, db, or.repo, or.rw, or.ins,
        or.canMergeFlag, getAlreadyAccepted(or, branchTip), destBranch, caller,
        mergeTip, commits, submissionId);
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
            or.repo, commit, or.project, destBranch, ps.getId(), caller);
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

  private void openRepos(Collection<Project.NameKey> projects)
      throws IntegrationException {
    for (Project.NameKey project : projects) {
      try {
        openRepo(project);
      } catch (NoSuchProjectException noProject) {
        logWarn("Project " + noProject.project() + " no longer exists, "
            + "abandoning open changes");
        abandonAllOpenChanges(noProject.project());
      } catch (IOException e) {
        throw new IntegrationException("Error opening project " + project, e);
      }
    }
  }

  private void abandonAllOpenChanges(Project.NameKey destProject) {
    try {
      for (ChangeData cd : internalChangeQuery.byProjectOpen(destProject)) {
        abandonOneChange(cd.change());
      }
    } catch (NoSuchChangeException | IOException | OrmException e) {
      logWarn("Cannot abandon changes for deleted project " + destProject, e);
    }
  }

  private void abandonOneChange(Change change) throws OrmException,
      NoSuchChangeException, IOException {
    db.changes().beginTransaction(change.getId());

    //TODO(dborowitz): support InternalUser in ChangeUpdate
    ChangeControl control = changeControlFactory.controlFor(change,
        identifiedUserFactory.create(change.getOwner()));
    // TODO(dborowitz): Convert to BatchUpdate.
    ChangeUpdate update = changeUpdateFactory.create(control);
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
