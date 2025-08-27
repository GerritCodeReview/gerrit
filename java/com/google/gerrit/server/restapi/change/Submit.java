// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.BranchUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.ImpersonationPermissionMode;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeOp;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * REST API handler for triggering submit of the specific revision of the change.
 *
 * <p>See /Documentation/rest-api-changes.html#submit-revision for more information.
 *
 * <p>Even though the endpoint is defined for url including a revision, only revision corresponding
 * to the latest patch set is allowed.
 */
@Singleton
public class Submit
    implements RestModifyView<RevisionResource, SubmitInput>, UiAction<RevisionResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEFAULT_TOOLTIP = "Submit patch set ${patchSet} into ${branch}";
  private static final String DEFAULT_TOOLTIP_ANCESTORS =
      "Submit patch set ${patchSet} and ancestors (${submitSize} changes "
          + "altogether) into ${branch}";
  private static final String DEFAULT_TOPIC_TOOLTIP =
      "Submit all ${topicSize} changes of the same topic "
          + "(${submitSize} changes including ancestors and other "
          + "changes related by topic)";
  private static final String CLICK_FAILURE_TOOLTIP = "Clicking the button would fail";
  private static final String CHANGE_UNMERGEABLE = "Problems with integrating this change";

  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final Provider<MergeOp> mergeOpProvider;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final AccountResolver accountResolver;
  private final String label;
  private final String labelWithParents;
  private final ParameterizedString titlePattern;
  private final ParameterizedString titlePatternWithAncestors;
  private final String submitTopicLabel;
  private final ParameterizedString submitTopicTooltip;
  private final boolean submitWholeTopic;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final ChangeJson.Factory json;
  private final ChangeData.Factory changeDataFactory;

  private final boolean useMergeabilityCheck;

  @Inject
  Submit(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      Provider<MergeOp> mergeOpProvider,
      Provider<MergeSuperSet> mergeSuperSet,
      AccountResolver accountResolver,
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      ChangeJson.Factory json,
      ChangeData.Factory changeDataFactory) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.mergeOpProvider = mergeOpProvider;
    this.mergeSuperSet = mergeSuperSet;
    this.accountResolver = accountResolver;
    this.label =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(cfg.getString("change", null, "submitLabel")), "Submit");
    this.labelWithParents =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(cfg.getString("change", null, "submitLabelWithParents")),
            "Submit including parents");
    this.titlePattern =
        new ParameterizedString(
            MoreObjects.firstNonNull(
                cfg.getString("change", null, "submitTooltip"), DEFAULT_TOOLTIP));
    this.titlePatternWithAncestors =
        new ParameterizedString(
            MoreObjects.firstNonNull(
                cfg.getString("change", null, "submitTooltipAncestors"),
                DEFAULT_TOOLTIP_ANCESTORS));
    submitWholeTopic = MergeSuperSet.wholeTopicEnabled(cfg);
    this.submitTopicLabel =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(cfg.getString("change", null, "submitTopicLabel")),
            "Submit whole topic");
    this.submitTopicTooltip =
        new ParameterizedString(
            MoreObjects.firstNonNull(
                cfg.getString("change", null, "submitTopicTooltip"), DEFAULT_TOPIC_TOOLTIP));
    this.queryProvider = queryProvider;
    this.psUtil = psUtil;
    this.projectCache = projectCache;
    this.json = json;
    this.changeDataFactory = changeDataFactory;
    this.useMergeabilityCheck = MergeabilityComputationBehavior.fromConfig(cfg).includeInApi();
  }

  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc, @Nullable SubmitInput input)
      throws RestApiException,
          RepositoryNotFoundException,
          IOException,
          PermissionBackendException,
          UpdateException,
          ConfigInvalidException {
    if (input == null) {
      input = new SubmitInput();
    }
    input.onBehalfOf = Strings.emptyToNull(input.onBehalfOf);
    IdentifiedUser submitter = rsrc.getUser().asIdentifiedUser();
    // It's possible that the user does not have permission to submit all changes in the superset,
    // but we check the current change for an early exit.
    rsrc.permissions().check(ChangePermission.SUBMIT);
    if (input.onBehalfOf != null) {
      submitter = onBehalfOf(rsrc, input);
    }
    changeDataFactory.create(rsrc.getChange()).checkStatePermitsWrite();

    return Response.ok(json.noOptions().format(mergeChange(rsrc, submitter, input)));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public Change mergeChange(RevisionResource rsrc, IdentifiedUser submitter, SubmitInput input)
      throws RestApiException,
          IOException,
          UpdateException,
          ConfigInvalidException,
          PermissionBackendException {
    Change change = rsrc.getChange();
    if (!change.isNew()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    } else if (!BranchUtil.branchExists(repoManager, change.getDest())) {
      throw new ResourceConflictException(
          String.format("destination branch \"%s\" not found.", change.getDest().branch()));
    } else if (!rsrc.getPatchSet().id().equals(change.currentPatchSetId())) {
      // TODO Allow submitting non-current revision by changing the current.
      throw new ResourceConflictException(
          String.format(
              "revision %s is not current revision", rsrc.getPatchSet().commitId().name()));
    }

    try (MergeOp op = mergeOpProvider.get()) {
      Change updatedChange = op.merge(change, submitter, true, input, false);
      if (updatedChange.isMerged()) {
        return updatedChange;
      }

      throw new IllegalStateException(
          String.format(
              "change %s of project %s unexpectedly had status %s after submit attempt",
              updatedChange.getId(), updatedChange.getProject(), updatedChange.getStatus()));
    }
  }

  /**
   * Returns a message describing what prevents the current change from being submitted - or null.
   * This method only considers parent changes, and changes in the same topic. The caller is
   * responsible for making sure the current change to be submitted can indeed be submitted
   * (permissions, submit rules, is not a WIP...)
   *
   * @param cd the change the user is currently looking at
   * @param cs set of changes to be submitted at once
   * @param user the user who is checking to submit
   * @return a reason why any of the changes is not submittable or null
   */
  @Nullable
  private String problemsForSubmittingChangeset(ChangeData cd, ChangeSet cs, CurrentUser user) {
    Optional<String> reason =
        MergeOp.checkCommonSubmitProblems(cd.change(), cs, false, permissionBackend, user).stream()
            .findFirst()
            .map(MergeOp.ChangeProblem::getProblem);
    if (reason.isPresent()) {
      return reason.get();
    }

    try {
      if (!useMergeabilityCheck) {
        return null;
      }
      Collection<ChangeData> unmergeable = getUnmergeableChanges(cs);
      if (unmergeable == null) {
        return CLICK_FAILURE_TOOLTIP;
      } else if (!unmergeable.isEmpty()) {
        for (ChangeData c : unmergeable) {
          if (c.change().getKey().equals(cd.change().getKey())) {
            return CHANGE_UNMERGEABLE;
          }
        }

        return "Problems with change(s): "
            + unmergeable.stream().map(c -> c.getId().toString()).collect(joining(", "));
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Error checking if change is submittable");
      throw new StorageException("Could not determine problems for the change", e);
    }
    return null;
  }

  @Nullable
  @Override
  public UiAction.Description getDescription(RevisionResource resource)
      throws IOException, PermissionBackendException {
    Change change = resource.getChange();
    if (!change.isNew() || !resource.isCurrent()) {
      return null; // submit not visible
    }
    if (!projectCache
        .get(resource.getProject())
        .map(ProjectState::statePermitsWrite)
        .orElse(false)) {
      return null; // submit not visible
    }

    ChangeData cd = resource.getChangeResource().getChangeData();
    try {
      MergeOp.checkSubmitRequirements(cd);
    } catch (ResourceConflictException e) {
      return null; // submit not visible
    }

    ChangeSet cs =
        mergeSuperSet
            .get()
            .completeChangeSet(cd.change(), resource.getUser(), /* includingTopicClosure= */ false);
    // Replace potentially stale ChangeData for the current change with the fresher one.
    cs =
        new ChangeSet(
            cs.changes().stream()
                .map(csChange -> csChange.getId().equals(cd.getId()) ? cd : csChange)
                .collect(toImmutableList()),
            cs.nonVisibleChanges());
    String submitProblems = problemsForSubmittingChangeset(cd, cs, resource.getUser());

    String topic = change.getTopic();
    int topicSize = 0;
    if (!Strings.isNullOrEmpty(topic)) {
      topicSize = queryProvider.get().noFields().byTopicOpen(topic).size();
    }
    boolean treatWithTopic = submitWholeTopic && !Strings.isNullOrEmpty(topic) && topicSize > 1;

    if (submitProblems != null) {
      return new UiAction.Description()
          .setLabel(treatWithTopic ? submitTopicLabel : (cs.size() > 1) ? labelWithParents : label)
          .setTitle(submitProblems)
          .setVisible(true)
          .setEnabled(false);
    }

    // Recheck mergeability rather than using value stored in the index, which may be stale.
    // TODO(dborowitz): This is ugly; consider providing a way to not read stored fields from the
    // index in the first place.
    // cd.setMergeable(null);
    // That was done in unmergeableChanges which was called by problemsForSubmittingChangeset, so
    // now it is safe to read from the cache, as it yields the same result.
    Boolean enabled = useMergeabilityCheck ? cd.isMergeable() : true;

    if (treatWithTopic) {
      ImmutableMap<String, String> params =
          ImmutableMap.of(
              "topicSize", String.valueOf(topicSize),
              "submitSize", String.valueOf(cs.size()));
      return new UiAction.Description()
          .setLabel(submitTopicLabel)
          .setTitle(Strings.emptyToNull(submitTopicTooltip.replace(params)))
          .setVisible(true)
          .setEnabled(Boolean.TRUE.equals(enabled));
    }
    ImmutableMap<String, String> params =
        ImmutableMap.of(
            "patchSet", String.valueOf(resource.getPatchSet().number()),
            "branch", change.getDest().shortName(),
            "commit", abbreviateName(resource.getPatchSet().commitId()),
            "submitSize", String.valueOf(cs.size()));
    ParameterizedString tp = cs.size() > 1 ? titlePatternWithAncestors : titlePattern;
    return new UiAction.Description()
        .setLabel(cs.size() > 1 ? labelWithParents : label)
        .setTitle(Strings.emptyToNull(tp.replace(params)))
        .setVisible(true)
        .setEnabled(Boolean.TRUE.equals(enabled));
  }

  @Nullable
  public Collection<ChangeData> getUnmergeableChanges(ChangeSet cs) throws IOException {
    Set<ChangeData> unmergeableChanges = new HashSet<>();
    Set<ObjectId> outDatedPatchSets = new HashSet<>();
    for (ChangeData change : cs.changes()) {
      unmergeableChanges.add(change);
      addAllOutdatedPatchSets(outDatedPatchSets, change);
    }
    ListMultimap<BranchNameKey, ChangeData> cbb = cs.changesByBranch();
    for (BranchNameKey branch : cbb.keySet()) {
      List<ChangeData> targetBranch = cbb.get(branch);
      HashMap<Change.Id, RevCommit> commits = mapToCommits(targetBranch, branch.project());
      Set<ObjectId> allParents =
          commits.values().stream()
              .flatMap(c -> Arrays.stream(c.getParents()))
              .map(RevObject::getId)
              .collect(Collectors.toSet());
      for (ChangeData change : targetBranch) {
        RevCommit commit = commits.get(change.getId());
        boolean isMergeCommit = commit.getParentCount() > 1;
        boolean isLastInChain = !allParents.contains(commit.getId());
        if (Arrays.stream(commit.getParents()).anyMatch(c -> outDatedPatchSets.contains(c.getId()))
            && !isCherryPickSubmit(change)) {
          // Found a parent that depends on an outdated patchset and the submit strategy is not
          // cherry-pick.
          continue;
        }
        // Recheck mergeability rather than using value stored in the index,
        // which may be stale.
        // TODO(dborowitz): This is ugly; consider providing a way to not read
        // stored fields from the index in the first place.
        change.setMergeable(null);
        Boolean mergeable = change.isMergeable();
        if (mergeable == null) {
          // Skip whole check, cannot determine if mergeable
          return null;
        }
        if (mergeable) {
          unmergeableChanges.remove(change);
        }
        if (isLastInChain && isMergeCommit && mergeable) {
          targetBranch.stream().forEach(unmergeableChanges::remove);
          break;
        }
      }
    }
    return unmergeableChanges;
  }

  /**
   * Add all outdated patch-sets (non-last patch-sets) to the output set {@code outdatedPatchSets}.
   */
  private static void addAllOutdatedPatchSets(Set<ObjectId> outdatedPatchSets, ChangeData cd) {
    outdatedPatchSets.addAll(
        cd.notes().getPatchSets().values().stream()
            .map(p -> p.commitId())
            .collect(Collectors.toSet()));
    outdatedPatchSets.remove(cd.currentPatchSet().commitId());
  }

  private boolean isCherryPickSubmit(ChangeData changeData) {
    SubmitTypeRecord submitTypeRecord = changeData.submitTypeRecord();
    return submitTypeRecord.isOk() && submitTypeRecord.type == SubmitType.CHERRY_PICK;
  }

  /** Map input {@code changes} to the commit SHA-1 of their latest patch-set. */
  private HashMap<Change.Id, RevCommit> mapToCommits(
      Collection<ChangeData> changes, Project.NameKey project) throws IOException {
    HashMap<Change.Id, RevCommit> commits = new HashMap<>();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      for (ChangeData change : changes) {
        RevCommit commit = walk.parseCommit(psUtil.current(change.notes()).commitId());
        commits.put(change.getId(), commit);
      }
    }
    return commits;
  }

  private IdentifiedUser onBehalfOf(RevisionResource rsrc, SubmitInput in)
      throws AuthException,
          UnprocessableEntityException,
          PermissionBackendException,
          IOException,
          ConfigInvalidException {
    PermissionBackend.ForChange perm = rsrc.permissions();
    // It's possible that the current user or on-behalf-of user does not have permission for all
    // changes in the superset, but we check the current change for an early exit.
    perm.check(ChangePermission.SUBMIT_AS);

    CurrentUser caller = rsrc.getUser();
    // TODO(kamilm): Code in MergeOp explicitly checks REAL_USER for permissions. We can simplify by
    // changing to REAL_USER here.
    IdentifiedUser submitter =
        accountResolver
            .resolve(in.onBehalfOf)
            .asUniqueUserOnBehalfOf(caller, ImpersonationPermissionMode.THIS_USER);
    try {
      permissionBackend.user(submitter).change(rsrc.getNotes()).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          String.format("on_behalf_of account %s cannot see change", submitter.getAccountId()), e);
    }
    logger.atFine().log(
        "Change %d is being submitted by %s on behalf of %s",
        rsrc.getChange().getChangeId(), rsrc.getUser().getUserName(), submitter.getUserName());
    return submitter;
  }

  /**
   * Change-level REST API endpoint that calls submit for the latest revision on a change.
   *
   * <p>See /Documentation/rest-api-changes.html#submit-change for more information.
   */
  public static class CurrentRevision implements RestModifyView<ChangeResource, SubmitInput> {
    private final Submit submit;
    private final PatchSetUtil psUtil;

    @Inject
    CurrentRevision(Submit submit, PatchSetUtil psUtil) {
      this.submit = submit;
      this.psUtil = psUtil;
    }

    @Override
    public Response<ChangeInfo> apply(ChangeResource rsrc, SubmitInput input) throws Exception {
      PatchSet ps = psUtil.current(rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      }

      return submit.apply(new RevisionResource(rsrc, ps), input);
    }
  }
}
