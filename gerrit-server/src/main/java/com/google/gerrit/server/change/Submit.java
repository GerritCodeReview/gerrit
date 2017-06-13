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

package com.google.gerrit.server.change;

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.change.Submit.Output;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Submit extends RetryingRestModifyView<RevisionResource, SubmitInput, Output>
    implements UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Submit.class);

  private static final String DEFAULT_TOOLTIP = "Submit patch set ${patchSet} into ${branch}";
  private static final String DEFAULT_TOOLTIP_ANCESTORS =
      "Submit patch set ${patchSet} and ancestors (${submitSize} changes "
          + "altogether) into ${branch}";
  private static final String DEFAULT_TOPIC_TOOLTIP =
      "Submit all ${topicSize} changes of the same topic "
          + "(${submitSize} changes including ancestors and other "
          + "changes related by topic)";
  private static final String BLOCKED_SUBMIT_TOOLTIP =
      "This change depends on other changes which are not ready";
  private static final String BLOCKED_HIDDEN_SUBMIT_TOOLTIP =
      "This change depends on other hidden changes which are not ready";
  private static final String BLOCKED_WORK_IN_PROGRESS = "This change is marked work in progress";
  private static final String CLICK_FAILURE_TOOLTIP = "Clicking the button would fail";
  private static final String CHANGE_UNMERGEABLE = "Problems with integrating this change";
  private static final String CHANGES_NOT_MERGEABLE = "Problems with change(s): ";

  public static class Output {
    transient Change change;

    private Output(Change c) {
      change = c;
    }
  }

  /**
   * Subclass of {@link SubmitInput} with special bits that may be flipped for testing purposes
   * only.
   */
  @VisibleForTesting
  public static class TestSubmitInput extends SubmitInput {
    public final boolean failAfterRefUpdates;

    public TestSubmitInput(SubmitInput base, boolean failAfterRefUpdates) {
      this.onBehalfOf = base.onBehalfOf;
      this.notify = base.notify;
      this.failAfterRefUpdates = failAfterRefUpdates;
    }
  }

  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final MergeOp.Factory mergeOpFactory;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final Accounts accounts;
  private final AccountsCollection accountsCollection;
  private final String label;
  private final String labelWithParents;
  private final ParameterizedString titlePattern;
  private final ParameterizedString titlePatternWithAncestors;
  private final String submitTopicLabel;
  private final ParameterizedString submitTopicTooltip;
  private final boolean submitWholeTopic;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;

  @Inject
  Submit(
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      RetryHelper retryHelper,
      ChangeData.Factory changeDataFactory,
      ChangeMessagesUtil cmUtil,
      ChangeNotes.Factory changeNotesFactory,
      MergeOp.Factory mergeOpFactory,
      Provider<MergeSuperSet> mergeSuperSet,
      Accounts accounts,
      AccountsCollection accountsCollection,
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.changeDataFactory = changeDataFactory;
    this.cmUtil = cmUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.mergeOpFactory = mergeOpFactory;
    this.mergeSuperSet = mergeSuperSet;
    this.accounts = accounts;
    this.accountsCollection = accountsCollection;
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
    submitWholeTopic = wholeTopicEnabled(cfg);
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
  }

  @Override
  protected Output applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, SubmitInput input)
      throws RestApiException, RepositoryNotFoundException, IOException, OrmException,
          PermissionBackendException {
    input.onBehalfOf = Strings.emptyToNull(input.onBehalfOf);
    IdentifiedUser submitter;
    if (input.onBehalfOf != null) {
      submitter = onBehalfOf(rsrc, input);
    } else {
      rsrc.permissions().check(ChangePermission.SUBMIT);
      submitter = rsrc.getUser().asIdentifiedUser();
    }

    return new Output(mergeChange(updateFactory, rsrc, submitter, input));
  }

  public Change mergeChange(
      BatchUpdate.Factory updateFactory,
      RevisionResource rsrc,
      IdentifiedUser submitter,
      SubmitInput input)
      throws OrmException, RestApiException, IOException {
    Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    } else if (!ProjectUtil.branchExists(repoManager, change.getDest())) {
      throw new ResourceConflictException(
          String.format("destination branch \"%s\" not found.", change.getDest().get()));
    } else if (!rsrc.getPatchSet().getId().equals(change.currentPatchSetId())) {
      // TODO Allow submitting non-current revision by changing the current.
      throw new ResourceConflictException(
          String.format(
              "revision %s is not current revision", rsrc.getPatchSet().getRevision().get()));
    }

    try (MergeOp op = mergeOpFactory.create(updateFactory)) {
      ReviewDb db = dbProvider.get();
      op.merge(db, change, submitter, true, input, false);
      try {
        change =
            changeNotesFactory.createChecked(db, change.getProject(), change.getId()).getChange();
      } catch (NoSuchChangeException e) {
        throw new ResourceConflictException("change is deleted");
      }
    }

    switch (change.getStatus()) {
      case MERGED:
        return change;
      case NEW:
        ChangeMessage msg = getConflictMessage(rsrc);
        if (msg != null) {
          throw new ResourceConflictException(msg.getMessage());
        }
        //$FALL-THROUGH$
      case ABANDONED:
      case DRAFT:
      default:
        throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }
  }

  /**
   * @param cd the change the user is currently looking at
   * @param cs set of changes to be submitted at once
   * @param user the user who is checking to submit
   * @return a reason why any of the changes is not submittable or null
   */
  private String problemsForSubmittingChangeset(ChangeData cd, ChangeSet cs, CurrentUser user) {
    try {
      if (cs.furtherHiddenChanges()) {
        return BLOCKED_HIDDEN_SUBMIT_TOOLTIP;
      }
      for (ChangeData c : cs.changes()) {
        Set<ChangePermission> can =
            permissionBackend
                .user(user)
                .database(dbProvider)
                .change(c)
                .test(EnumSet.of(ChangePermission.READ, ChangePermission.SUBMIT));
        if (!can.contains(ChangePermission.READ)) {
          return BLOCKED_HIDDEN_SUBMIT_TOOLTIP;
        }
        if (!can.contains(ChangePermission.SUBMIT)) {
          return BLOCKED_SUBMIT_TOOLTIP;
        }
        if (c.change().isWorkInProgress()) {
          return BLOCKED_WORK_IN_PROGRESS;
        }
        MergeOp.checkSubmitRule(accounts, c);
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
        return CHANGES_NOT_MERGEABLE
            + unmergeable.stream().map(c -> c.getId().toString()).collect(joining(", "));
      }
    } catch (ResourceConflictException e) {
      return BLOCKED_SUBMIT_TOOLTIP;
    } catch (PermissionBackendException | OrmException | IOException e) {
      log.error("Error checking if change is submittable", e);
      throw new OrmRuntimeException("Could not determine problems for the change", e);
    }
    return null;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    Change change = resource.getChange();
    String topic = change.getTopic();
    ReviewDb db = dbProvider.get();
    ChangeData cd = changeDataFactory.create(db, resource.getControl());
    boolean visible;
    try {
      visible =
          change.getStatus().isOpen()
              && resource.isCurrent()
              && !resource.getPatchSet().isDraft()
              && resource.permissions().test(ChangePermission.SUBMIT);
      MergeOp.checkSubmitRule(accounts, cd);
    } catch (ResourceConflictException e) {
      visible = false;
    } catch (PermissionBackendException e) {
      log.error("Error checking if change is submittable", e);
      throw new OrmRuntimeException("Could not check submit permission", e);
    } catch (OrmException e) {
      log.error("Error checking if change is submittable", e);
      throw new OrmRuntimeException("Could not determine problems for the change", e);
    }

    if (!visible) {
      return new UiAction.Description().setLabel("").setTitle("").setVisible(false);
    }

    ChangeSet cs;
    try {
      cs = mergeSuperSet.get().completeChangeSet(db, cd.change(), resource.getControl().getUser());
    } catch (OrmException | IOException e) {
      throw new OrmRuntimeException(
          "Could not determine complete set of changes to be submitted", e);
    }

    int topicSize = 0;
    if (!Strings.isNullOrEmpty(topic)) {
      topicSize = getChangesByTopic(topic).size();
    }
    boolean treatWithTopic = submitWholeTopic && !Strings.isNullOrEmpty(topic) && topicSize > 1;

    String submitProblems = problemsForSubmittingChangeset(cd, cs, resource.getUser());

    Boolean enabled;
    try {
      // Recheck mergeability rather than using value stored in the index,
      // which may be stale.
      // TODO(dborowitz): This is ugly; consider providing a way to not read
      // stored fields from the index in the first place.
      // cd.setMergeable(null);
      // That was done in unmergeableChanges which was called by
      // problemsForSubmittingChangeset, so now it is safe to read from
      // the cache, as it yields the same result.
      enabled = cd.isMergeable(accounts);
    } catch (OrmException e) {
      throw new OrmRuntimeException("Could not determine mergeability", e);
    }

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
    RevId revId = resource.getPatchSet().getRevision();
    Map<String, String> params =
        ImmutableMap.of(
            "patchSet", String.valueOf(resource.getPatchSet().getPatchSetId()),
            "branch", change.getDest().getShortName(),
            "commit", ObjectId.fromString(revId.get()).abbreviate(7).name(),
            "submitSize", String.valueOf(cs.size()));
    ParameterizedString tp = cs.size() > 1 ? titlePatternWithAncestors : titlePattern;
    return new UiAction.Description()
        .setLabel(cs.size() > 1 ? labelWithParents : label)
        .setTitle(Strings.emptyToNull(tp.replace(params)))
        .setVisible(true)
        .setEnabled(Boolean.TRUE.equals(enabled));
  }

  /**
   * If the merge was attempted and it failed the system usually writes a comment as a ChangeMessage
   * and sets status to NEW. Find the relevant message and return it.
   */
  public ChangeMessage getConflictMessage(RevisionResource rsrc) throws OrmException {
    return FluentIterable.from(
            cmUtil.byPatchSet(dbProvider.get(), rsrc.getNotes(), rsrc.getPatchSet().getId()))
        .filter(cm -> cm.getAuthor() == null)
        .last()
        .orNull();
  }

  public Collection<ChangeData> unmergeableChanges(ChangeSet cs) throws OrmException, IOException {
    Set<ChangeData> mergeabilityMap = new HashSet<>();
    for (ChangeData change : cs.changes()) {
      mergeabilityMap.add(change);
    }

    ListMultimap<Branch.NameKey, ChangeData> cbb = cs.changesByBranch();
    for (Branch.NameKey branch : cbb.keySet()) {
      Collection<ChangeData> targetBranch = cbb.get(branch);
      HashMap<Change.Id, RevCommit> commits = findCommits(targetBranch, branch.getParentKey());

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

        // Recheck mergeability rather than using value stored in the index,
        // which may be stale.
        // TODO(dborowitz): This is ugly; consider providing a way to not read
        // stored fields from the index in the first place.
        change.setMergeable(null);
        Boolean mergeable = change.isMergeable(accounts);
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

  private HashMap<Change.Id, RevCommit> findCommits(
      Collection<ChangeData> changes, Project.NameKey project) throws IOException, OrmException {
    HashMap<Change.Id, RevCommit> commits = new HashMap<>();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      for (ChangeData change : changes) {
        RevCommit commit =
            walk.parseCommit(
                ObjectId.fromString(
                    psUtil.current(dbProvider.get(), change.notes()).getRevision().get()));
        commits.put(change.getId(), commit);
      }
    }
    return commits;
  }

  private IdentifiedUser onBehalfOf(RevisionResource rsrc, SubmitInput in)
      throws AuthException, UnprocessableEntityException, OrmException, PermissionBackendException {
    PermissionBackend.ForChange perm = rsrc.permissions().database(dbProvider);
    perm.check(ChangePermission.SUBMIT);
    perm.check(ChangePermission.SUBMIT_AS);

    CurrentUser caller = rsrc.getUser();
    IdentifiedUser submitter = accountsCollection.parseOnBehalfOf(caller, in.onBehalfOf);
    try {
      perm.user(submitter).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          String.format("on_behalf_of account %s cannot see change", submitter.getAccountId()));
    }
    return submitter;
  }

  public static boolean wholeTopicEnabled(Config config) {
    return config.getBoolean("change", null, "submitWholeTopic", false);
  }

  private List<ChangeData> getChangesByTopic(String topic) {
    try {
      return queryProvider.get().byTopicOpen(topic);
    } catch (OrmException e) {
      throw new OrmRuntimeException(e);
    }
  }

  public static class CurrentRevision
      extends RetryingRestModifyView<ChangeResource, SubmitInput, ChangeInfo> {
    private final Provider<ReviewDb> dbProvider;
    private final Submit submit;
    private final ChangeJson.Factory json;
    private final PatchSetUtil psUtil;

    @Inject
    CurrentRevision(
        Provider<ReviewDb> dbProvider,
        RetryHelper retryHelper,
        Submit submit,
        ChangeJson.Factory json,
        PatchSetUtil psUtil) {
      super(retryHelper);
      this.dbProvider = dbProvider;
      this.submit = submit;
      this.json = json;
      this.psUtil = psUtil;
    }

    @Override
    protected ChangeInfo applyImpl(
        BatchUpdate.Factory updateFactory, ChangeResource rsrc, SubmitInput input)
        throws RestApiException, RepositoryNotFoundException, IOException, OrmException,
            PermissionBackendException {
      PatchSet ps = psUtil.current(dbProvider.get(), rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }

      Output out = submit.applyImpl(updateFactory, new RevisionResource(rsrc, ps), input);
      return json.noOptions().format(out.change);
    }
  }
}
