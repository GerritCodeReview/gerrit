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

package com.google.gerrit.server.submit;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenBranch;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
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
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SubmitRuleOptions SUBMIT_RULE_OPTIONS = SubmitRuleOptions.builder().build();
  private static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_ALLOW_CLOSED =
      SUBMIT_RULE_OPTIONS.toBuilder().allowClosed(true).build();

  public static class CommitStatus {
    private final ImmutableMap<Change.Id, ChangeData> changes;
    private final ImmutableSetMultimap<BranchNameKey, Change.Id> byBranch;
    private final Map<Change.Id, CodeReviewCommit> commits;
    private final ListMultimap<Change.Id, String> problems;
    private final boolean allowClosed;

    private CommitStatus(ChangeSet cs, boolean allowClosed) {
      checkArgument(
          !cs.furtherHiddenChanges(), "CommitStatus must not be called with hidden changes");
      changes = cs.changesById();
      ImmutableSetMultimap.Builder<BranchNameKey, Change.Id> bb = ImmutableSetMultimap.builder();
      for (ChangeData cd : cs.changes()) {
        bb.put(cd.change().getDest(), cd.getId());
      }
      byBranch = bb.build();
      commits = new HashMap<>();
      problems = MultimapBuilder.treeKeys(comparing(Change.Id::get)).arrayListValues(1).build();
      this.allowClosed = allowClosed;
    }

    public ImmutableSet<Change.Id> getChangeIds() {
      return changes.keySet();
    }

    public ImmutableSet<Change.Id> getChangeIds(BranchNameKey branch) {
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
      logger.atSevere().withCause(t).log("%s %s", msg, id);
      problems.put(id, msg);
    }

    public void logProblem(Change.Id id, String msg) {
      logger.atSevere().log("%s %s", msg, id);
      problems.put(id, msg);
    }

    public boolean isOk() {
      return problems.isEmpty();
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
      ChangeData cd = requireNonNull(changes.get(id), () -> String.format("ChangeData for %s", id));
      return requireNonNull(
          cd.getSubmitRecords(submitRuleOptions(allowClosed)),
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
  private final Provider<InternalChangeQuery> queryProvider;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final Provider<MergeOpRepoManager> ormProvider;
  private final NotifyResolver notifyResolver;
  private final RetryHelper retryHelper;
  private final ChangeData.Factory changeDataFactory;

  // Changes that were updated by this MergeOp.
  private final Map<Change.Id, Change> updatedChanges;

  private Timestamp ts;
  private SubmissionId submissionId;
  private IdentifiedUser caller;

  private MergeOpRepoManager orm;
  private CommitStatus commitStatus;
  private SubmitInput submitInput;
  private NotifyResolver.Result notify;
  private Set<Project.NameKey> allProjects;
  private boolean dryrun;
  private TopicMetrics topicMetrics;

  @Inject
  MergeOp(
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      InternalUser.Factory internalUserFactory,
      MergeSuperSet mergeSuperSet,
      MergeValidators.Factory mergeValidatorsFactory,
      Provider<InternalChangeQuery> queryProvider,
      SubmitStrategyFactory submitStrategyFactory,
      SubmoduleOp.Factory subOpFactory,
      Provider<MergeOpRepoManager> ormProvider,
      NotifyResolver notifyResolver,
      TopicMetrics topicMetrics,
      RetryHelper retryHelper,
      ChangeData.Factory changeDataFactory) {
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.internalUserFactory = internalUserFactory;
    this.mergeSuperSet = mergeSuperSet;
    this.mergeValidatorsFactory = mergeValidatorsFactory;
    this.queryProvider = queryProvider;
    this.submitStrategyFactory = submitStrategyFactory;
    this.subOpFactory = subOpFactory;
    this.ormProvider = ormProvider;
    this.notifyResolver = notifyResolver;
    this.retryHelper = retryHelper;
    this.topicMetrics = topicMetrics;
    this.changeDataFactory = changeDataFactory;
    this.updatedChanges = new HashMap<>();
  }

  @Override
  public void close() {
    if (orm != null) {
      orm.close();
    }
  }

  public static void checkSubmitRule(ChangeData cd, boolean allowClosed)
      throws ResourceConflictException {
    PatchSet patchSet = cd.currentPatchSet();
    if (patchSet == null) {
      throw new ResourceConflictException("missing current patch set for change " + cd.getId());
    }
    List<SubmitRecord> results = getSubmitRecords(cd, allowClosed);
    if (SubmitRecord.allRecordsOK(results)) {
      // Rules supplied a valid solution.
      return;
    } else if (results.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "SubmitRuleEvaluator.evaluate for change %s returned empty list for %s in %s",
              cd.getId(), patchSet.id(), cd.change().getProject().get()));
    }

    for (SubmitRecord record : results) {
      switch (record.status) {
        case OK:
          break;

        case CLOSED:
          throw new ResourceConflictException("change is closed");

        case RULE_ERROR:
          throw new ResourceConflictException("submit rule error: " + record.errorMessage);

        case NOT_READY:
          throw new ResourceConflictException(describeNotReady(cd, record));

        case FORCED:
        default:
          throw new IllegalStateException(
              String.format(
                  "Unexpected SubmitRecord status %s for %s in %s",
                  record.status, patchSet.id().getId(), cd.change().getProject().get()));
      }
    }
    throw new IllegalStateException();
  }

  private static SubmitRuleOptions submitRuleOptions(boolean allowClosed) {
    return allowClosed ? SUBMIT_RULE_OPTIONS_ALLOW_CLOSED : SUBMIT_RULE_OPTIONS;
  }

  private static List<SubmitRecord> getSubmitRecords(ChangeData cd, boolean allowClosed) {
    return cd.submitRecords(submitRuleOptions(allowClosed));
  }

  private static String describeNotReady(ChangeData cd, SubmitRecord record) {
    List<String> blockingConditions = new ArrayList<>();
    if (record.labels != null) {
      blockingConditions.add(describeLabels(cd, record.labels));
    }
    if (record.requirements != null) {
      record.requirements.stream()
          .map(SubmitRequirement::fallbackText)
          .forEach(blockingConditions::add);
    }
    return Joiner.on("; ").join(blockingConditions);
  }

  private static String describeLabels(ChangeData cd, List<SubmitRecord.Label> labels) {
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

  private void checkSubmitRulesAndState(ChangeSet cs, boolean allowMerged)
      throws ResourceConflictException {
    checkArgument(
        !cs.furtherHiddenChanges(), "checkSubmitRulesAndState called for topic with hidden change");
    for (ChangeData cd : cs.changes()) {
      try {
        if (!cd.change().isNew()) {
          if (!(cd.change().isMerged() && allowMerged)) {
            commitStatus.problem(
                cd.getId(), "Change " + cd.getId() + " is " + ChangeUtil.status(cd.change()));
          }
        } else if (cd.change().isWorkInProgress()) {
          commitStatus.problem(cd.getId(), "Change " + cd.getId() + " is work in progress");
        } else {
          checkSubmitRule(cd, allowMerged);
        }
      } catch (ResourceConflictException e) {
        commitStatus.problem(cd.getId(), e.getMessage());
      } catch (StorageException e) {
        String msg = "Error checking submit rules for change";
        logger.atWarning().withCause(e).log("%s %s", msg, cd.getId());
        commitStatus.problem(cd.getId(), msg);
      }
    }
    commitStatus.maybeFailVerbose();
  }

  private void bypassSubmitRules(ChangeSet cs, boolean allowClosed) {
    checkArgument(
        !cs.furtherHiddenChanges(), "cannot bypass submit rules for topic with hidden change");
    for (ChangeData cd : cs.changes()) {
      List<SubmitRecord> records = new ArrayList<>(getSubmitRecords(cd, allowClosed));
      SubmitRecord forced = new SubmitRecord();
      forced.status = SubmitRecord.Status.FORCED;
      records.add(forced);
      cd.setSubmitRecords(submitRuleOptions(allowClosed), records);
    }
  }

  /**
   * Merges the given change.
   *
   * <p>Depending on the server configuration, more changes may be affected, e.g. by submission of a
   * topic or via superproject subscriptions. All affected changes are integrated using the projects
   * integration strategy.
   *
   * @param change the change to be merged.
   * @param caller the identity of the caller
   * @param checkSubmitRules whether the prolog submit rules should be evaluated
   * @param submitInput parameters regarding the merge
   * @throws RestApiException if an error occurred.
   * @throws PermissionBackendException if permissions can't be checked
   * @throws IOException an error occurred reading from NoteDb.
   * @return the merged change
   */
  public Change merge(
      Change change,
      IdentifiedUser caller,
      boolean checkSubmitRules,
      SubmitInput submitInput,
      boolean dryrun)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException,
          PermissionBackendException {
    this.submitInput = submitInput;
    this.notify =
        notifyResolver.resolve(
            firstNonNull(submitInput.notify, NotifyHandling.ALL), submitInput.notifyDetails);
    this.dryrun = dryrun;
    this.caller = caller;
    this.ts = TimeUtil.nowTs();
    this.submissionId = new SubmissionId(change);

    try (TraceContext traceContext =
        TraceContext.open()
            .addTag(RequestId.Type.SUBMISSION_ID, new RequestId(submissionId.toString()))) {
      openRepoManager();

      logger.atFine().log("Beginning integration of %s", change);
      try {
        ChangeSet indexBackedChangeSet =
            mergeSuperSet.setMergeOpRepoManager(orm).completeChangeSet(change, caller);
        if (!indexBackedChangeSet.ids().contains(change.getId())) {
          // indexBackedChangeSet contains only open changes, if the change is missing in this set
          // it might be that the change was concurrently submitted in the meantime.
          change = changeDataFactory.create(change).reloadChange();
          if (!change.isNew()) {
            throw new ResourceConflictException("change is " + ChangeUtil.status(change));
          }
          throw new IllegalStateException(
              String.format("change %s missing from %s", change.getId(), indexBackedChangeSet));
        }

        if (indexBackedChangeSet.furtherHiddenChanges()) {
          throw new AuthException(
              "A change to be submitted with " + change.getId() + " is not visible");
        }
        logger.atFine().log("Calculated to merge %s", indexBackedChangeSet);

        // Reload ChangeSet so that we don't rely on (potentially) stale index data for merging
        ChangeSet cs = reloadChanges(indexBackedChangeSet);

        // Count cross-project submissions outside of the retry loop. The chance of a single project
        // failing increases with the number of projects, so the failure count would be inflated if
        // this metric were incremented inside of integrateIntoHistory.
        int projects = cs.projects().size();
        if (projects > 1) {
          topicMetrics.topicSubmissions.increment();
        }

        RetryTracker retryTracker = new RetryTracker();
        retryHelper
            .changeUpdate(
                "integrateIntoHistory",
                updateFactory -> {
                  long attempt = retryTracker.lastAttemptNumber + 1;
                  boolean isRetry = attempt > 1;
                  if (isRetry) {
                    logger.atFine().log("Retrying, attempt #%d; skipping merged changes", attempt);
                    this.ts = TimeUtil.nowTs();
                    openRepoManager();
                  }
                  this.commitStatus = new CommitStatus(cs, isRetry);
                  if (checkSubmitRules) {
                    logger.atFine().log("Checking submit rules and state");
                    checkSubmitRulesAndState(cs, isRetry);
                  } else {
                    logger.atFine().log("Bypassing submit rules");
                    bypassSubmitRules(cs, isRetry);
                  }
                  integrateIntoHistory(cs);
                  return null;
                })
            .listener(retryTracker)
            // Up to the entire submit operation is retried, including possibly many projects.
            // Multiply the timeout by the number of projects we're actually attempting to
            // submit.
            .defaultTimeoutMultiplier(cs.projects().size())
            .call();

        if (projects > 1) {
          topicMetrics.topicSubmissionsCompleted.increment();
        }

        // It's expected that callers invoke this method only for open changes and that the provided
        // change either gets updated to merged or that this method fails with an exception. For
        // safety, fall-back to return the provided change if there was no update for this change
        // (e.g. caller provided a change that was already merged).
        return updatedChanges.containsKey(change.getId())
            ? updatedChanges.get(change.getId())
            : change;
      } catch (IOException e) {
        // Anything before the merge attempt is an error
        throw new StorageException(e);
      }
    }
  }

  private void openRepoManager() {
    if (orm != null) {
      orm.close();
    }
    orm = ormProvider.get();
    orm.setContext(ts, caller, notify);
  }

  private ChangeSet reloadChanges(ChangeSet changeSet) {
    List<ChangeData> visible = new ArrayList<>(changeSet.changes().size());
    List<ChangeData> nonVisible = new ArrayList<>(changeSet.nonVisibleChanges().size());
    changeSet.changes().forEach(c -> visible.add(changeDataFactory.create(c.project(), c.getId())));
    changeSet
        .nonVisibleChanges()
        .forEach(c -> nonVisible.add(changeDataFactory.create(c.project(), c.getId())));
    return new ChangeSet(visible, nonVisible);
  }

  private class RetryTracker implements RetryListener {
    long lastAttemptNumber;

    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      lastAttemptNumber = attempt.getAttemptNumber();
    }
  }

  @Singleton
  private static class TopicMetrics {
    final Counter0 topicSubmissions;
    final Counter0 topicSubmissionsCompleted;

    @Inject
    TopicMetrics(MetricMaker metrics) {
      topicSubmissions =
          metrics.newCounter(
              "topic/cross_project_submit",
              new Description("Attempts at cross project topic submission").setRate());
      topicSubmissionsCompleted =
          metrics.newCounter(
              "topic/cross_project_submit_completed",
              new Description("Cross project topic submissions that concluded successfully")
                  .setRate());
    }
  }

  private void integrateIntoHistory(ChangeSet cs) throws RestApiException, UpdateException {
    checkArgument(!cs.furtherHiddenChanges(), "cannot integrate hidden changes into history");
    logger.atFine().log("Beginning merge attempt on %s", cs);
    Map<BranchNameKey, BranchBatch> toSubmit = new HashMap<>();

    ListMultimap<BranchNameKey, ChangeData> cbb;
    try {
      cbb = cs.changesByBranch();
    } catch (StorageException e) {
      throw new StorageException("Error reading changes to submit", e);
    }
    Set<BranchNameKey> branches = cbb.keySet();

    for (BranchNameKey branch : branches) {
      OpenRepo or = openRepo(branch.project());
      if (or != null) {
        toSubmit.put(branch, validateChangeList(or, cbb.get(branch)));
      }
    }

    // Done checks that don't involve running submit strategies.
    commitStatus.maybeFailVerbose();

    try {
      SubmoduleOp submoduleOp = subOpFactory.create(branches, orm);
      List<SubmitStrategy> strategies = getSubmitStrategies(toSubmit, submoduleOp, dryrun);
      this.allProjects = submoduleOp.getProjectsInOrder();
      try {
        BatchUpdate.execute(
            orm.batchUpdates(allProjects),
            new SubmitStrategyListener(submitInput, strategies, commitStatus),
            dryrun);
      } finally {
        // If the BatchUpdate fails it can be that merging some of the changes was actually
        // successful. This is why we must to collect the updated changes also when an exception was
        // thrown.
        strategies.forEach(s -> updatedChanges.putAll(s.getUpdatedChanges()));
      }
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(e.getMessage());
    } catch (IOException e) {
      throw new StorageException(e);
    } catch (SubmoduleConflictException e) {
      throw new IntegrationConflictException(e.getMessage(), e);
    } catch (UpdateException e) {
      if (e.getCause() instanceof LockFailureException) {
        // Lock failures are a special case: RetryHelper depends on this specific causal chain in
        // order to trigger a retry. The downside of throwing here is we will not get the nicer
        // error message constructed below, in the case where this is the final attempt and the
        // operation is not retried further. This is not a huge downside, and is hopefully so rare
        // as to be unnoticeable, assuming RetryHelper is retrying sufficiently.
        throw e;
      }

      // BatchUpdate may have inadvertently wrapped an IntegrationConflictException
      // thrown by some legacy SubmitStrategyOp code that intended the error
      // message to be user-visible. Copy the message from the wrapped
      // exception.
      //
      // If you happen across one of these, the correct fix is to convert the
      // inner IntegrationConflictException to a ResourceConflictException.
      if (e.getCause() instanceof IntegrationConflictException) {
        throw (IntegrationConflictException) e.getCause();
      }
      throw new StorageException(genericMergeError(cs), e);
    }
  }

  public Set<Project.NameKey> getAllProjects() {
    return allProjects;
  }

  public MergeOpRepoManager getMergeOpRepoManager() {
    return orm;
  }

  private List<SubmitStrategy> getSubmitStrategies(
      Map<BranchNameKey, BranchBatch> toSubmit, SubmoduleOp submoduleOp, boolean dryrun)
      throws IntegrationConflictException, NoSuchProjectException, IOException {
    List<SubmitStrategy> strategies = new ArrayList<>();
    Set<BranchNameKey> allBranches = submoduleOp.getBranchesInOrder();
    Set<CodeReviewCommit> allCommits =
        toSubmit.values().stream().map(BranchBatch::commits).flatMap(Set::stream).collect(toSet());
    for (BranchNameKey branch : allBranches) {
      OpenRepo or = orm.getRepo(branch.project());
      if (toSubmit.containsKey(branch)) {
        BranchBatch submitting = toSubmit.get(branch);
        logger.atFine().log("adding ops for branch batch %s", submitting);
        OpenBranch ob = or.getBranch(branch);
        requireNonNull(
            submitting.submitType(),
            String.format("null submit type for %s; expected to previously fail fast", submitting));
        Set<CodeReviewCommit> commitsToSubmit = submitting.commits();
        ob.mergeTip = new MergeTip(ob.oldTip, commitsToSubmit);
        SubmitStrategy strategy =
            submitStrategyFactory.create(
                submitting.submitType(),
                or.rw,
                or.canMergeFlag,
                getAlreadyAccepted(or, ob.oldTip),
                allCommits,
                branch,
                caller,
                ob.mergeTip,
                commitStatus,
                submissionId,
                submitInput,
                submoduleOp,
                dryrun);
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

  private Set<RevCommit> getAlreadyAccepted(OpenRepo or, CodeReviewCommit branchTip) {
    Set<RevCommit> alreadyAccepted = new HashSet<>();

    if (branchTip != null) {
      alreadyAccepted.add(branchTip);
    }

    try {
      for (Ref r : or.repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
        try {
          CodeReviewCommit aac = or.rw.parseCommit(r.getObjectId());
          if (!commitStatus.commits.values().contains(aac)) {
            alreadyAccepted.add(aac);
          }
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    } catch (IOException e) {
      throw new StorageException("Failed to determine already accepted commits.", e);
    }

    logger.atFine().log("Found %d existing heads: %s", alreadyAccepted.size(), alreadyAccepted);
    return alreadyAccepted;
  }

  @AutoValue
  abstract static class BranchBatch {
    @Nullable
    abstract SubmitType submitType();

    abstract Set<CodeReviewCommit> commits();
  }

  private BranchBatch validateChangeList(OpenRepo or, Collection<ChangeData> submitted) {
    logger.atFine().log("Validating %d changes", submitted.size());
    Set<CodeReviewCommit> toSubmit = new LinkedHashSet<>(submitted.size());
    SetMultimap<ObjectId, PatchSet.Id> revisions = getRevisions(or, submitted);

    SubmitType submitType = null;
    ChangeData choseSubmitTypeFrom = null;
    for (ChangeData cd : submitted) {
      Change.Id changeId = cd.getId();
      ChangeNotes notes;
      Change chg;
      SubmitType st;
      try {
        notes = cd.notes();
        chg = cd.change();
        st = getSubmitType(cd);
      } catch (StorageException e) {
        commitStatus.logProblem(changeId, e);
        continue;
      }

      if (st == null) {
        commitStatus.logProblem(changeId, "No submit type for change");
        continue;
      }
      if (submitType == null) {
        submitType = st;
        choseSubmitTypeFrom = cd;
      } else if (st != submitType) {
        commitStatus.problem(
            changeId,
            String.format(
                "Change has submit type %s, but previously chose submit type %s "
                    + "from change %s in the same batch",
                st, submitType, choseSubmitTypeFrom.getId()));
        continue;
      }
      if (chg.currentPatchSetId() == null) {
        String msg = "Missing current patch set on change";
        logger.atSevere().log("%s %s", msg, changeId);
        commitStatus.problem(changeId, msg);
        continue;
      }

      PatchSet ps;
      BranchNameKey destBranch = chg.getDest();
      try {
        ps = cd.currentPatchSet();
      } catch (StorageException e) {
        commitStatus.logProblem(changeId, e);
        continue;
      }
      if (ps == null) {
        commitStatus.logProblem(changeId, "Missing patch set on change");
        continue;
      }

      ObjectId id = ps.commitId();
      if (!revisions.containsEntry(id, ps.id())) {
        if (revisions.containsValue(ps.id())) {
          // TODO This is actually an error, the patch set ref exists but points to a revision that
          // is different from the revision that we have stored for the patch set in the change
          // meta data.
          commitStatus.logProblem(
              changeId,
              "Revision "
                  + id.name()
                  + " of patch set "
                  + ps.number()
                  + " does not match the revision of the patch set ref "
                  + ps.id().toRefName());
          continue;
        }

        // The patch set ref is not found but we want to merge the change. We can't safely do that
        // if the patch set ref is missing. In a cluster setups with multiple primary nodes this can
        // indicate a replication lag (e.g. the change meta data was already replicated, but the
        // replication of the patch set ref is still pending).
        commitStatus.logProblem(
            changeId,
            "Patch set ref "
                + ps.id().toRefName()
                + " not found. Expected patch set ref of "
                + ps.number()
                + " to point to revision "
                + id.name());
        continue;
      }

      CodeReviewCommit commit;
      try {
        commit = or.rw.parseCommit(id);
      } catch (IOException e) {
        commitStatus.logProblem(changeId, e);
        continue;
      }

      commit.setNotes(notes);
      commit.setPatchsetId(ps.id());
      commitStatus.put(commit);

      MergeValidators mergeValidators = mergeValidatorsFactory.create();
      try {
        mergeValidators.validatePreMerge(or.repo, commit, or.project, destBranch, ps.id(), caller);
      } catch (MergeValidationException mve) {
        commitStatus.problem(changeId, mve.getMessage());
        continue;
      }
      commit.add(or.canMergeFlag);
      toSubmit.add(commit);
    }
    logger.atFine().log("Submitting on this run: %s", toSubmit);
    return new AutoValue_MergeOp_BranchBatch(submitType, toSubmit);
  }

  private SetMultimap<ObjectId, PatchSet.Id> getRevisions(OpenRepo or, Collection<ChangeData> cds) {
    try {
      List<String> refNames = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        Change c = cd.change();
        if (c != null) {
          refNames.add(c.currentPatchSetId().toRefName());
        }
      }
      SetMultimap<ObjectId, PatchSet.Id> revisions =
          MultimapBuilder.hashKeys(cds.size()).hashSetValues(1).build();
      for (Map.Entry<String, Ref> e :
          or.repo
              .getRefDatabase()
              .exactRef(refNames.toArray(new String[refNames.size()]))
              .entrySet()) {
        revisions.put(e.getValue().getObjectId(), PatchSet.Id.fromRef(e.getKey()));
      }
      return revisions;
    } catch (IOException | StorageException e) {
      throw new StorageException("Failed to validate changes", e);
    }
  }

  private SubmitType getSubmitType(ChangeData cd) {
    SubmitTypeRecord str = cd.submitTypeRecord();
    return str.isOk() ? str.type : null;
  }

  private OpenRepo openRepo(Project.NameKey project) {
    try {
      return orm.getRepo(project);
    } catch (NoSuchProjectException e) {
      logger.atWarning().log("Project %s no longer exists, abandoning open changes.", project);
      abandonAllOpenChangeForDeletedProject(project);
    } catch (IOException e) {
      throw new StorageException("Error opening project " + project, e);
    }
    return null;
  }

  private void abandonAllOpenChangeForDeletedProject(Project.NameKey destProject) {
    try {
      for (ChangeData cd : queryProvider.get().byProjectOpen(destProject)) {
        try (BatchUpdate bu =
            batchUpdateFactory.create(destProject, internalUserFactory.create(), ts)) {
          bu.addOp(
              cd.getId(),
              new BatchUpdateOp() {
                @Override
                public boolean updateChange(ChangeContext ctx) {
                  Change change = ctx.getChange();
                  if (!change.isNew()) {
                    return false;
                  }

                  change.setStatus(Change.Status.ABANDONED);

                  ChangeMessage msg =
                      ChangeMessagesUtil.newMessage(
                          change.currentPatchSetId(),
                          internalUserFactory.create(),
                          change.getLastUpdatedOn(),
                          "Project was deleted.",
                          ChangeMessagesUtil.TAG_MERGED);
                  cmUtil.addChangeMessage(ctx.getUpdate(change.currentPatchSetId()), msg);

                  return true;
                }
              });
          try {
            bu.execute();
          } catch (UpdateException | RestApiException e) {
            logger.atWarning().withCause(e).log(
                "Cannot abandon changes for deleted project %s", destProject);
          }
        }
      }
    } catch (StorageException e) {
      logger.atWarning().withCause(e).log(
          "Cannot abandon changes for deleted project %s", destProject);
    }
  }

  private String genericMergeError(ChangeSet cs) {
    int c = cs.size();
    if (c == 1) {
      return "Error submitting change";
    }
    int p = cs.projects().size();
    if (p == 1) {
      // Fused updates: it's correct to say that none of the n changes were submitted.
      return "Error submitting " + c + " changes";
    }
    // Multiple projects involved, but we don't know at this point what failed. At least give the
    // user a heads up that some changes may be unsubmitted, even if the change screen they land on
    // after the error message says that this particular change was submitted.
    return "Error submitting some of the "
        + c
        + " changes to one or more of the "
        + p
        + " projects involved; some projects may have submitted successfully, but others may have"
        + " failed";
  }
}
