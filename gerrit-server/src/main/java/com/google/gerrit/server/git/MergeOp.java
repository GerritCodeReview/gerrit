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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenBranch;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.git.strategy.SubmitStrategyListener;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges changes in submission order into a single branch.
 *
 * <p>Branches are reduced to the minimum number of heads needed to merge everything. This allows
 * commits to be entered into the queue in any order (such as ancestors before descendants) and only
 * the most recent commit on any line of development will be merged. All unmerged commits along a
 * line of development must be in the submission queue in order to merge the tip of that line.
 *
 * <p>Conflicts are handled by discarding the entire line of development and marking it as
 * conflicting, even if an earlier commit along that same line can be merged cleanly.
 */
public class MergeOp implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  private static final SubmitRuleOptions SUBMIT_RULE_OPTIONS = SubmitRuleOptions.defaults().build();

  public static class CommitStatus {
    private final ImmutableMap<Change.Id, ChangeData> changes;
    private final ImmutableSetMultimap<Branch.NameKey, Change.Id> byBranch;
    private final Map<Change.Id, CodeReviewCommit> commits;
    private final Multimap<Change.Id, String> problems;

    private CommitStatus(ChangeSet cs) throws OrmException {
      checkArgument(
          !cs.furtherHiddenChanges(), "CommitStatus must not be called with hidden changes");
      changes = cs.changesById();
      ImmutableSetMultimap.Builder<Branch.NameKey, Change.Id> bb = ImmutableSetMultimap.builder();
      for (ChangeData cd : cs.changes()) {
        bb.put(cd.change().getDest(), cd.getId());
      }
      byBranch = bb.build();
      commits = new HashMap<>();
      problems = MultimapBuilder.treeKeys(comparing(Change.Id::get)).arrayListValues(1).build();
    }

    public ImmutableSet<Change.Id> getChangeIds() {
      return changes.keySet();
    }

    public ImmutableSet<Change.Id> getChangeIds(Branch.NameKey branch) {
      return byBranch.get(branch);
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

    public List<SubmitRecord> getSubmitRecords(Change.Id id) {
      // Use the cached submit records from the original ChangeData in the input
      // ChangeSet, which were checked earlier in the integrate process. Even in
      // the case of a race where the submit records may have changed, it makes
      // more sense to store the original results of the submit rule evaluator
      // than to fail at this point.
      //
      // However, do NOT expose that ChangeData directly, as it is way out of
      // date by this point.
      ChangeData cd = checkNotNull(changes.get(id), "ChangeData for %s", id);
      return checkNotNull(
          cd.getSubmitRecords(SUBMIT_RULE_OPTIONS),
          "getSubmitRecord only valid after submit rules are evalutated");
    }

    public void maybeFailVerbose() throws ResourceConflictException {
      if (isOk()) {
        return;
      }
      String msg =
          "Failed to submit "
              + changes.size()
              + " change"
              + (changes.size() > 1 ? "s" : "")
              + " due to the following problems:\n";
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
      if (ids.size() == 1) {
        msg.append(" ").append(ids.iterator().next());
      } else {
        msg.append("s ").append(Joiner.on(", ").join(ids));
      }
      throw new ResourceConflictException(msg.toString());
    }
  }

  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final InternalUser.Factory internalUserFactory;
  private final MergeSuperSet mergeSuperSet;
  private final MergeValidators.Factory mergeValidatorsFactory;
  private final InternalChangeQuery internalChangeQuery;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final MergeOpRepoManager orm;

  private Timestamp ts;
  private RequestId submissionId;
  private IdentifiedUser caller;

  private CommitStatus commits;
  private ReviewDb db;
  private SubmitInput submitInput;
  private Set<Project.NameKey> allProjects;
  private boolean dryrun;

  @Inject
  MergeOp(
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      InternalUser.Factory internalUserFactory,
      MergeSuperSet mergeSuperSet,
      MergeValidators.Factory mergeValidatorsFactory,
      InternalChangeQuery internalChangeQuery,
      SubmitStrategyFactory submitStrategyFactory,
      SubmoduleOp.Factory subOpFactory,
      MergeOpRepoManager orm) {
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.internalUserFactory = internalUserFactory;
    this.mergeSuperSet = mergeSuperSet;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.internalChangeQuery = internalChangeQuery;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.orm = orm;
  }

  @Override
  public void close() {
    orm.close();
  }

  public static void checkSubmitRule(ChangeData cd) throws ResourceConflictException, OrmException {
    PatchSet patchSet = cd.currentPatchSet();
    if (patchSet == null) {
      throw new ResourceConflictException("missing current patch set for change " + cd.getId());
    }
    List<SubmitRecord> results = getSubmitRecords(cd);
    if (SubmitRecord.findOkRecord(results).isPresent()) {
      // Rules supplied a valid solution.
      return;
    } else if (results.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "SubmitRuleEvaluator.evaluate for change %s " + "returned empty list for %s in %s",
              cd.getId(), patchSet.getId(), cd.change().getProject().get()));
    }

    for (SubmitRecord record : results) {
      switch (record.status) {
        case CLOSED:
          throw new ResourceConflictException("change is closed");

        case RULE_ERROR:
          throw new ResourceConflictException("submit rule error: " + record.errorMessage);

        case NOT_READY:
          throw new ResourceConflictException(describeLabels(cd, record.labels));

        case FORCED:
        case OK:
        default:
          throw new IllegalStateException(
              String.format(
                  "Unexpected SubmitRecord status %s for %s in %s",
                  record.status, patchSet.getId().getId(), cd.change().getProject().get()));
      }
    }
    throw new IllegalStateException();
  }

  private static List<SubmitRecord> getSubmitRecords(ChangeData cd) throws OrmException {
    return cd.submitRecords(SUBMIT_RULE_OPTIONS);
  }

  private static String describeLabels(ChangeData cd, List<SubmitRecord.Label> labels)
      throws OrmException {
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
          labelResults.add("needs " + lbl.label + " (check project access)");
          break;

        default:
          throw new IllegalStateException(
              String.format(
                  "Unsupported SubmitRecord.Label %s for %s in %s",
                  lbl, cd.change().currentPatchSetId(), cd.change().getProject()));
      }
    }
    return Joiner.on("; ").join(labelResults);
  }

  private void checkSubmitRulesAndState(ChangeSet cs) throws ResourceConflictException {
    checkArgument(
        !cs.furtherHiddenChanges(), "checkSubmitRulesAndState called for topic with hidden change");
    for (ChangeData cd : cs.changes()) {
      try {
        if (cd.change().getStatus() != Change.Status.NEW) {
          commits.problem(
              cd.getId(),
              "Change " + cd.getId() + " is " + cd.change().getStatus().toString().toLowerCase());
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
    commits.maybeFailVerbose();
  }

  private void bypassSubmitRules(ChangeSet cs) {
    checkArgument(
        !cs.furtherHiddenChanges(), "cannot bypass submit rules for topic with hidden change");
    for (ChangeData cd : cs.changes()) {
      List<SubmitRecord> records;
      try {
        records = new ArrayList<>(getSubmitRecords(cd));
      } catch (OrmException e) {
        log.warn("Error checking submit rules for change " + cd.getId(), e);
        records = new ArrayList<>(1);
      }
      SubmitRecord forced = new SubmitRecord();
      forced.status = SubmitRecord.Status.FORCED;
      records.add(forced);
      cd.setSubmitRecords(SUBMIT_RULE_OPTIONS, records);
    }
  }

  /**
   * Merges the given change.
   *
   * <p>Depending on the server configuration, more changes may be affected, e.g. by submission of a
   * topic or via superproject subscriptions. All affected changes are integrated using the projects
   * integration strategy.
   *
   * @param db the review database.
   * @param change the change to be merged.
   * @param caller the identity of the caller
   * @param checkSubmitRules whether the prolog submit rules should be evaluated
   * @param submitInput parameters regarding the merge
   * @throws OrmException an error occurred reading or writing the database.
   * @throws RestApiException if an error occurred.
   */
  public void merge(
      ReviewDb db,
      Change change,
      IdentifiedUser caller,
      boolean checkSubmitRules,
      SubmitInput submitInput,
      boolean dryrun)
      throws OrmException, RestApiException {
    this.submitInput = submitInput;
    this.dryrun = dryrun;
    this.caller = caller;
    this.ts = TimeUtil.nowTs();
    submissionId = RequestId.forChange(change);
    this.db = db;
    orm.setContext(db, ts, caller, submissionId);

    logDebug("Beginning integration of {}", change);
    try {
      ChangeSet cs = mergeSuperSet.setMergeOpRepoManager(orm).completeChangeSet(db, change, caller);
      checkState(
          cs.ids().contains(change.getId()), "change %s missing from %s", change.getId(), cs);
      if (cs.furtherHiddenChanges()) {
        throw new AuthException(
            "A change to be submitted with " + change.getId() + " is not visible");
      }
      this.commits = new CommitStatus(cs);
      MergeSuperSet.reloadChanges(cs);
      logDebug("Calculated to merge {}", cs);
      if (checkSubmitRules) {
        logDebug("Checking submit rules and state");
        checkSubmitRulesAndState(cs);
      } else {
        logDebug("Bypassing submit rules");
        bypassSubmitRules(cs);
      }
      try {
        integrateIntoHistory(cs);
      } catch (IntegrationException e) {
        logError("Error from integrateIntoHistory", e);
        throw new ResourceConflictException(e.getMessage(), e);
      }
    } catch (IOException e) {
      // Anything before the merge attempt is an error
      throw new OrmException(e);
    }
  }

  private void integrateIntoHistory(ChangeSet cs) throws IntegrationException, RestApiException {
    checkArgument(!cs.furtherHiddenChanges(), "cannot integrate hidden changes into history");
    logDebug("Beginning merge attempt on {}", cs);
    Map<Branch.NameKey, BranchBatch> toSubmit = new HashMap<>();

    Multimap<Branch.NameKey, ChangeData> cbb;
    try {
      cbb = cs.changesByBranch();
    } catch (OrmException e) {
      throw new IntegrationException("Error reading changes to submit", e);
    }
    Set<Branch.NameKey> branches = cbb.keySet();
    for (Branch.NameKey branch : branches) {
      OpenRepo or = openRepo(branch.getParentKey());
      if (or != null) {
        toSubmit.put(branch, validateChangeList(or, cbb.get(branch)));
      }
    }
    // Done checks that don't involve running submit strategies.
    commits.maybeFailVerbose();
    SubmoduleOp submoduleOp = subOpFactory.create(branches, orm);
    try {
      List<SubmitStrategy> strategies = getSubmitStrategies(toSubmit, submoduleOp, dryrun);
      this.allProjects = submoduleOp.getProjectsInOrder();
      BatchUpdate.execute(
          orm.batchUpdates(allProjects),
          new SubmitStrategyListener(submitInput, strategies, commits),
          submissionId,
          dryrun);
    } catch (SubmoduleException e) {
      throw new IntegrationException(e);
    } catch (UpdateException e) {
      // BatchUpdate may have inadvertently wrapped an IntegrationException
      // thrown by some legacy SubmitStrategyOp code that intended the error
      // message to be user-visible. Copy the message from the wrapped
      // exception.
      //
      // If you happen across one of these, the correct fix is to convert the
      // inner IntegrationException to a ResourceConflictException.
      String msg;
      if (e.getCause() instanceof IntegrationException) {
        msg = e.getCause().getMessage();
      } else {
        msg = "Error submitting change" + (cs.size() != 1 ? "s" : "");
      }
      throw new IntegrationException(msg, e);
    }
  }

  public Set<Project.NameKey> getAllProjects() {
    return allProjects;
  }

  public MergeOpRepoManager getMergeOpRepoManager() {
    return orm;
  }

  private List<SubmitStrategy> getSubmitStrategies(
      Map<Branch.NameKey, BranchBatch> toSubmit, SubmoduleOp submoduleOp, boolean dryrun)
      throws IntegrationException {
    List<SubmitStrategy> strategies = new ArrayList<>();
    Set<Branch.NameKey> allBranches = submoduleOp.getBranchesInOrder();
    for (Branch.NameKey branch : allBranches) {
      OpenRepo or = orm.getRepo(branch.getParentKey());
      if (toSubmit.containsKey(branch)) {
        BranchBatch submitting = toSubmit.get(branch);
        OpenBranch ob = or.getBranch(branch);
        checkNotNull(
            submitting.submitType(),
            "null submit type for %s; expected to previously fail fast",
            submitting);
        Set<CodeReviewCommit> commitsToSubmit = commits(submitting.changes());
        ob.mergeTip = new MergeTip(ob.oldTip, commitsToSubmit);
        SubmitStrategy strategy =
            createStrategy(
                or, ob.mergeTip, branch, submitting.submitType(), ob.oldTip, submoduleOp, dryrun);
        strategies.add(strategy);
        strategy.addOps(or.getUpdate(), commitsToSubmit);
        if (submitting.submitType().equals(SubmitType.FAST_FORWARD_ONLY)
            && submoduleOp.hasSubscription(branch)) {
          submoduleOp.addOp(or.getUpdate(), branch);
        }
      } else {
        // no open change for this branch
        // add submodule triggered op into BatchUpdate
        submoduleOp.addOp(or.getUpdate(), branch);
      }
    }
    return strategies;
  }

  private Set<CodeReviewCommit> commits(List<ChangeData> cds) {
    LinkedHashSet<CodeReviewCommit> result = Sets.newLinkedHashSetWithExpectedSize(cds.size());
    for (ChangeData cd : cds) {
      CodeReviewCommit commit = commits.get(cd.getId());
      checkState(commit != null, "commit for %s not found by validateChangeList", cd.getId());
      result.add(commit);
    }
    return result;
  }

  private SubmitStrategy createStrategy(
      OpenRepo or,
      MergeTip mergeTip,
      Branch.NameKey destBranch,
      SubmitType submitType,
      CodeReviewCommit branchTip,
      SubmoduleOp submoduleOp,
      boolean dryrun)
      throws IntegrationException {
    return submitStrategyFactory.create(
        submitType,
        db,
        or.repo,
        or.rw,
        or.ins,
        or.canMergeFlag,
        getAlreadyAccepted(or, branchTip),
        destBranch,
        caller,
        mergeTip,
        commits,
        submissionId,
        submitInput.notify,
        submoduleOp,
        dryrun);
  }

  private Set<RevCommit> getAlreadyAccepted(OpenRepo or, CodeReviewCommit branchTip)
      throws IntegrationException {
    Set<RevCommit> alreadyAccepted = new HashSet<>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (Ref r : or.repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
        try {
          CodeReviewCommit aac = or.rw.parseCommit(r.getObjectId());
          if (!commits.commits.values().contains(aac)) {
            alreadyAccepted.add(aac);
          }
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    } catch (IOException e) {
      throw new IntegrationException("Failed to determine already accepted commits.", e);
    }

    logDebug("Found {} existing heads", alreadyAccepted.size());
    return alreadyAccepted;
  }

  @AutoValue
  abstract static class BranchBatch {
    @Nullable
    abstract SubmitType submitType();

    abstract List<ChangeData> changes();
  }

  private BranchBatch validateChangeList(OpenRepo or, Collection<ChangeData> submitted)
      throws IntegrationException {
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

      SubmitType st = getSubmitType(cd);
      if (st == null) {
        commits.logProblem(changeId, "No submit type for change");
        continue;
      }
      if (submitType == null) {
        submitType = st;
        choseSubmitTypeFrom = cd;
      } else if (st != submitType) {
        commits.problem(
            changeId,
            String.format(
                "Change has submit type %s, but previously chose submit type %s "
                    + "from change %s in the same batch",
                st, submitType, choseSubmitTypeFrom.getId()));
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
      if (ps == null || ps.getRevision() == null || ps.getRevision().get() == null) {
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
        commits.logProblem(
            changeId,
            "Revision "
                + idstr
                + " of patch set "
                + ps.getPatchSetId()
                + " does not match "
                + ps.getId().toRefName()
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
      commit.add(or.canMergeFlag);
      toSubmit.add(cd);
    }
    logDebug("Submitting on this run: {}", toSubmit);
    return new AutoValue_MergeOp_BranchBatch(submitType, toSubmit);
  }

  private Multimap<ObjectId, PatchSet.Id> getRevisions(OpenRepo or, Collection<ChangeData> cds)
      throws IntegrationException {
    try {
      List<String> refNames = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        Change c = cd.change();
        if (c != null) {
          refNames.add(c.currentPatchSetId().toRefName());
        }
      }
      Multimap<ObjectId, PatchSet.Id> revisions = HashMultimap.create(cds.size(), 1);
      for (Map.Entry<String, Ref> e :
          or.repo
              .getRefDatabase()
              .exactRef(refNames.toArray(new String[refNames.size()]))
              .entrySet()) {
        revisions.put(e.getValue().getObjectId(), PatchSet.Id.fromRef(e.getKey()));
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

  private OpenRepo openRepo(Project.NameKey project) throws IntegrationException {
    try {
      return orm.openRepo(project);
    } catch (NoSuchProjectException noProject) {
      logWarn("Project " + noProject.project() + " no longer exists, " + "abandoning open changes");
      abandonAllOpenChangeForDeletedProject(noProject.project());
    } catch (IOException e) {
      throw new IntegrationException("Error opening project " + project, e);
    }
    return null;
  }

  private void abandonAllOpenChangeForDeletedProject(Project.NameKey destProject) {
    try {
      for (ChangeData cd : internalChangeQuery.byProjectOpen(destProject)) {
        try (BatchUpdate bu =
            batchUpdateFactory.create(db, destProject, internalUserFactory.create(), ts)) {
          bu.setRequestId(submissionId);
          bu.addOp(
              cd.getId(),
              new BatchUpdate.Op() {
                @Override
                public boolean updateChange(ChangeContext ctx) throws OrmException {
                  Change change = ctx.getChange();
                  if (!change.getStatus().isOpen()) {
                    return false;
                  }

                  change.setStatus(Change.Status.ABANDONED);

                  ChangeMessage msg =
                      ChangeMessagesUtil.newMessage(
                          ctx.getDb(),
                          change.currentPatchSetId(),
                          internalUserFactory.create(),
                          change.getLastUpdatedOn(),
                          ChangeMessagesUtil.TAG_MERGED,
                          "Project was deleted.");
                  cmUtil.addChangeMessage(
                      ctx.getDb(), ctx.getUpdate(change.currentPatchSetId()), msg);

                  return true;
                }
              });
          try {
            bu.execute();
          } catch (UpdateException | RestApiException e) {
            logWarn("Cannot abandon changes for deleted project " + destProject, e);
          }
        }
      }
    } catch (OrmException e) {
      logWarn("Cannot abandon changes for deleted project " + destProject, e);
    }
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(submissionId + msg, args);
    }
  }

  private void logWarn(String msg, Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn(submissionId + msg, t);
    }
  }

  private void logWarn(String msg) {
    if (log.isWarnEnabled()) {
      log.warn(submissionId + msg);
    }
  }

  private void logError(String msg, Throwable t) {
    if (log.isErrorEnabled()) {
      if (t != null) {
        log.error(submissionId + msg, t);
      } else {
        log.error(submissionId + msg);
      }
    }
  }

  private void logError(String msg) {
    logError(msg, null);
  }
}
