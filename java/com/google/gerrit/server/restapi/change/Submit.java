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

import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
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
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

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
  private static final String BLOCKED_HIDDEN_SUBMIT_TOOLTIP =
      "This change depends on other hidden changes which are not ready";
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
      ChangeJson.Factory json) {
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
  }

  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc, SubmitInput input)
      throws RestApiException, RepositoryNotFoundException, IOException, PermissionBackendException,
          UpdateException, ConfigInvalidException {
    input.onBehalfOf = Strings.emptyToNull(input.onBehalfOf);
    IdentifiedUser submitter;
    if (input.onBehalfOf != null) {
      submitter = onBehalfOf(rsrc, input);
    } else {
      rsrc.permissions().check(ChangePermission.SUBMIT);
      submitter = rsrc.getUser().asIdentifiedUser();
    }
    projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .checkStatePermitsWrite();

    return Response.ok(json.noOptions().format(mergeChange(rsrc, submitter, input)));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public Change mergeChange(RevisionResource rsrc, IdentifiedUser submitter, SubmitInput input)
      throws RestApiException, IOException, UpdateException, ConfigInvalidException,
          PermissionBackendException {
    Change change = rsrc.getChange();
    if (!change.isNew()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    } else if (!ProjectUtil.branchExists(repoManager, change.getDest())) {
      throw new ResourceConflictException(
          String.format("destination branch \"%s\" not found.", change.getDest().branch()));
    } else if (!rsrc.getPatchSet().id().equals(change.currentPatchSetId())) {
      // TODO Allow submitting non-current revision by changing the current.
      throw new ResourceConflictException(
          String.format(
              "revision %s is not current revision", rsrc.getPatchSet().commitId().name()));
    }

    try (MergeOp op = mergeOpProvider.get()) {
      Change updatedChange;

      updatedChange = op.merge(change, submitter, true, input, false);
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
  private String problemsForSubmittingChangeset(ChangeData cd, ChangeSet cs, CurrentUser user) {
    try {
      if (cs.furtherHiddenChanges()) {
        logger.atFine().log(
            "Change %d cannot be submitted by user %s because it depends on hidden changes: %s",
            cd.getId().get(), user.getLoggableName(), cs.nonVisibleChanges());
        return BLOCKED_HIDDEN_SUBMIT_TOOLTIP;
      }
      for (ChangeData c : cs.changes()) {
        Set<ChangePermission> can =
            permissionBackend
                .user(user)
                .change(c)
                .test(EnumSet.of(ChangePermission.READ, ChangePermission.SUBMIT));
        if (!can.contains(ChangePermission.READ)) {
          logger.atFine().log(
              "Change %d cannot be submitted by user %s because it depends on change %d which the user cannot read",
              cd.getId().get(), user.getLoggableName(), c.getId().get());
          return BLOCKED_HIDDEN_SUBMIT_TOOLTIP;
        }
        if (!can.contains(ChangePermission.SUBMIT)) {
          return "You don't have permission to submit change " + c.getId();
        }
        if (c.change().isWorkInProgress()) {
          return "Change " + c.getId() + " is marked work in progress";
        }
        try {
          MergeOp.checkSubmitRule(c, false);
        } catch (ResourceConflictException e) {
          return "Change " + c.getId() + " is not ready: " + e.getMessage();
        }
      }

      Collection<ChangeData> unmergeable = unmergeableChanges(cs);
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
    } catch (PermissionBackendException | IOException e) {
      logger.atSevere().withCause(e).log("Error checking if change is submittable");
      throw new StorageException("Could not determine problems for the change", e);
    }
    return null;
  }

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
      MergeOp.checkSubmitRule(cd, false);
    } catch (ResourceConflictException e) {
      return null; // submit not visible
    }

    ChangeSet cs = mergeSuperSet.get().completeChangeSet(cd.change(), resource.getUser());
    String topic = change.getTopic();
    int topicSize = 0;
    if (!Strings.isNullOrEmpty(topic)) {
      topicSize = queryProvider.get().noFields().byTopicOpen(topic).size();
    }
    boolean treatWithTopic = submitWholeTopic && !Strings.isNullOrEmpty(topic) && topicSize > 1;

    String submitProblems = problemsForSubmittingChangeset(cd, cs, resource.getUser());

    // Recheck mergeability rather than using value stored in the index, which may be stale.
    // TODO(dborowitz): This is ugly; consider providing a way to not read stored fields from the
    // index in the first place.
    // cd.setMergeable(null);
    // That was done in unmergeableChanges which was called by problemsForSubmittingChangeset, so
    // now it is safe to read from the cache, as it yields the same result.
    Boolean enabled = cd.isMergeable();

    if (submitProblems != null) {
      return new UiAction.Description()
          .setLabel(treatWithTopic ? submitTopicLabel : (cs.size() > 1) ? labelWithParents : label)
          .setTitle(submitProblems)
          .setVisible(true)
          .setEnabled(false);
    }

    if (treatWithTopic) {
      Map<String, String> params =
          ImmutableMap.of(
              "topicSize", String.valueOf(topicSize),
              "submitSize", String.valueOf(cs.size()));
      return new UiAction.Description()
          .setLabel(submitTopicLabel)
          .setTitle(Strings.emptyToNull(submitTopicTooltip.replace(params)))
          .setVisible(true)
          .setEnabled(Boolean.TRUE.equals(enabled));
    }
    Map<String, String> params =
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

  public Collection<ChangeData> unmergeableChanges(ChangeSet cs) throws IOException {
    Set<ChangeData> mergeabilityMap = new HashSet<>();
    Set<ObjectId> outDatedPatchsets = new HashSet<>();
    for (ChangeData change : cs.changes()) {
      mergeabilityMap.add(change);
      // Add all the patchsets commit ids except the current patchset.
      outDatedPatchsets.addAll(
          change.notes().getPatchSets().values().stream()
              .map(p -> p.commitId())
              .collect(Collectors.toSet()));
      outDatedPatchsets.remove(change.currentPatchSet().commitId());
    }

    ListMultimap<BranchNameKey, ChangeData> cbb = cs.changesByBranch();
    for (BranchNameKey branch : cbb.keySet()) {
      Collection<ChangeData> targetBranch = cbb.get(branch);
      HashMap<Change.Id, RevCommit> commits = findCommits(targetBranch, branch.project());

      Set<ObjectId> allParents = Sets.newHashSetWithExpectedSize(cs.size());
      for (RevCommit commit : commits.values()) {
        for (RevCommit parent : commit.getParents()) {
          allParents.add(parent.getId());
        }
      }
      for (ChangeData change : targetBranch) {

        RevCommit commit = commits.get(change.getId());
        boolean isMergeCommit = commit.getParentCount() > 1;
        boolean isLastInChain = !allParents.contains(commit.getId());
        if (Arrays.stream(commit.getParents()).anyMatch(c -> outDatedPatchsets.contains(c.getId()))
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
          mergeabilityMap.remove(change);
        }

        if (isLastInChain && isMergeCommit && mergeable) {
          for (ChangeData c : targetBranch) {
            mergeabilityMap.remove(c);
          }
          break;
        }
      }
    }
    return mergeabilityMap;
  }

  private boolean isCherryPickSubmit(ChangeData changeData) {
    SubmitTypeRecord submitTypeRecord = changeData.submitTypeRecord();
    return submitTypeRecord.isOk() && submitTypeRecord.type == SubmitType.CHERRY_PICK;
  }

  private HashMap<Change.Id, RevCommit> findCommits(
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
      throws AuthException, UnprocessableEntityException, PermissionBackendException, IOException,
          ConfigInvalidException {
    PermissionBackend.ForChange perm = rsrc.permissions();
    perm.check(ChangePermission.SUBMIT);
    perm.check(ChangePermission.SUBMIT_AS);

    CurrentUser caller = rsrc.getUser();
    IdentifiedUser submitter =
        accountResolver.resolve(in.onBehalfOf).asUniqueUserOnBehalfOf(caller);
    try {
      permissionBackend.user(submitter).change(rsrc.getNotes()).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          String.format("on_behalf_of account %s cannot see change", submitter.getAccountId()), e);
    }
    return submitter;
  }

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
