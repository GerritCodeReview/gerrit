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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_COMMITS;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CHANGE_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CHECK;
import static com.google.gerrit.extensions.client.ListChangesOption.COMMIT_FOOTERS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_ACCOUNTS;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.DOWNLOAD_COMMANDS;
import static com.google.gerrit.extensions.client.ListChangesOption.LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.extensions.client.ListChangesOption.PUSH_CERTIFICATES;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWED;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWER_UPDATES;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.extensions.client.ListChangesOption.TRACKING_IDS;
import static com.google.gerrit.extensions.client.ListChangesOption.WEB_LINKS;
import static com.google.gerrit.server.CommonConverters.toGitPerson;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.extensions.common.PushCertificateInfo;
import com.google.gerrit.extensions.common.ReviewerUpdateInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.common.TrackingIdInfo;
import com.google.gerrit.extensions.common.VotingRangeInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gerrit.server.api.accounts.GpgApiAdapter;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gerrit.server.query.change.PluginDefinedAttributesFactory;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeJson {
  private static final Logger log = LoggerFactory.getLogger(ChangeJson.class);

  // Submit rule options in this class should always use fastEvalLabels for
  // efficiency reasons. Callers that care about submittability after taking
  // vote squashing into account should be looking at the submit action.
  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      ChangeField.SUBMIT_RULE_OPTIONS_LENIENT.toBuilder().fastEvalLabels(true).build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      ChangeField.SUBMIT_RULE_OPTIONS_STRICT.toBuilder().fastEvalLabels(true).build();

  public static final ImmutableSet<ListChangesOption> REQUIRE_LAZY_LOAD =
      ImmutableSet.of(
          ALL_COMMITS,
          ALL_REVISIONS,
          CHANGE_ACTIONS,
          CHECK,
          COMMIT_FOOTERS,
          CURRENT_ACTIONS,
          CURRENT_COMMIT,
          MESSAGES);

  @Singleton
  public static class Factory {
    private final AssistedFactory factory;

    @Inject
    Factory(AssistedFactory factory) {
      this.factory = factory;
    }

    public ChangeJson noOptions() {
      return create(ImmutableSet.of());
    }

    public ChangeJson create(Iterable<ListChangesOption> options) {
      return factory.create(options);
    }

    public ChangeJson create(ListChangesOption first, ListChangesOption... rest) {
      return create(Sets.immutableEnumSet(first, rest));
    }
  }

  public interface AssistedFactory {
    ChangeJson create(Iterable<ListChangesOption> options);
  }

  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> userProvider;
  private final AnonymousUser anonymous;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeData.Factory changeDataFactory;
  private final FileInfoJson fileInfoJson;
  private final AccountLoader.Factory accountLoaderFactory;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final WebLinks webLinks;
  private final ImmutableSet<ListChangesOption> options;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ConsistencyChecker> checkerProvider;
  private final ActionJson actionJson;
  private final GpgApiAdapter gpgApi;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeIndexCollection indexes;
  private final ApprovalsUtil approvalsUtil;
  private final RemoveReviewerControl removeReviewerControl;
  private final TrackingFooters trackingFooters;
  private boolean lazyLoad = true;
  private AccountLoader accountLoader;
  private FixInput fix;
  private PluginDefinedAttributesFactory pluginDefinedAttributesFactory;

  @Inject
  ChangeJson(
      Provider<ReviewDb> db,
      Provider<CurrentUser> user,
      AnonymousUser au,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      IdentifiedUser.GenericFactory uf,
      ChangeData.Factory cdf,
      FileInfoJson fileInfoJson,
      AccountLoader.Factory ailf,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      WebLinks webLinks,
      ChangeMessagesUtil cmUtil,
      Provider<ConsistencyChecker> checkerProvider,
      ActionJson actionJson,
      GpgApiAdapter gpgApi,
      ChangeNotes.Factory notesFactory,
      ChangeResource.Factory changeResourceFactory,
      ChangeKindCache changeKindCache,
      ChangeIndexCollection indexes,
      ApprovalsUtil approvalsUtil,
      RemoveReviewerControl removeReviewerControl,
      TrackingFooters trackingFooters,
      @Assisted Iterable<ListChangesOption> options) {
    this.db = db;
    this.userProvider = user;
    this.anonymous = au;
    this.changeDataFactory = cdf;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.userFactory = uf;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.fileInfoJson = fileInfoJson;
    this.accountLoaderFactory = ailf;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.webLinks = webLinks;
    this.cmUtil = cmUtil;
    this.checkerProvider = checkerProvider;
    this.actionJson = actionJson;
    this.gpgApi = gpgApi;
    this.notesFactory = notesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.changeKindCache = changeKindCache;
    this.indexes = indexes;
    this.approvalsUtil = approvalsUtil;
    this.removeReviewerControl = removeReviewerControl;
    this.options = Sets.immutableEnumSet(options);
    this.trackingFooters = trackingFooters;
  }

  public ChangeJson lazyLoad(boolean load) {
    lazyLoad = load;
    return this;
  }

  public ChangeJson fix(FixInput fix) {
    this.fix = fix;
    return this;
  }

  public void setPluginDefinedAttributesFactory(PluginDefinedAttributesFactory pluginsFactory) {
    this.pluginDefinedAttributesFactory = pluginsFactory;
  }

  public ChangeInfo format(ChangeResource rsrc) throws OrmException {
    return format(changeDataFactory.create(db.get(), rsrc.getNotes()));
  }

  public ChangeInfo format(Change change) throws OrmException {
    return format(changeDataFactory.create(db.get(), change));
  }

  public ChangeInfo format(Project.NameKey project, Change.Id id) throws OrmException {
    ChangeNotes notes;
    try {
      notes = notesFactory.createChecked(db.get(), project, id);
    } catch (OrmException e) {
      if (!has(CHECK)) {
        throw e;
      }
      return checkOnly(changeDataFactory.create(db.get(), project, id));
    }
    return format(changeDataFactory.create(db.get(), notes));
  }

  public ChangeInfo format(ChangeData cd) throws OrmException {
    return format(cd, Optional.empty(), true);
  }

  private ChangeInfo format(
      ChangeData cd, Optional<PatchSet.Id> limitToPsId, boolean fillAccountLoader)
      throws OrmException {
    try {
      if (fillAccountLoader) {
        accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
        ChangeInfo res = toChangeInfo(cd, limitToPsId);
        accountLoader.fill();
        return res;
      }
      return toChangeInfo(cd, limitToPsId);
    } catch (PatchListNotAvailableException
        | GpgException
        | OrmException
        | IOException
        | PermissionBackendException
        | NoSuchProjectException
        | RuntimeException e) {
      if (!has(CHECK)) {
        Throwables.throwIfInstanceOf(e, OrmException.class);
        throw new OrmException(e);
      }
      return checkOnly(cd);
    }
  }

  public ChangeInfo format(RevisionResource rsrc) throws OrmException {
    ChangeData cd = changeDataFactory.create(db.get(), rsrc.getNotes());
    return format(cd, Optional.of(rsrc.getPatchSet().getId()), true);
  }

  public List<List<ChangeInfo>> formatQueryResults(List<QueryResult<ChangeData>> in)
      throws OrmException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    ensureLoaded(FluentIterable.from(in).transformAndConcat(QueryResult::entities));

    List<List<ChangeInfo>> res = Lists.newArrayListWithCapacity(in.size());
    Map<Change.Id, ChangeInfo> out = new HashMap<>();
    for (QueryResult<ChangeData> r : in) {
      List<ChangeInfo> infos = toChangeInfo(out, r.entities());
      if (!infos.isEmpty() && r.more()) {
        infos.get(infos.size() - 1)._moreChanges = true;
      }
      res.add(infos);
    }
    accountLoader.fill();
    return res;
  }

  public List<ChangeInfo> formatChangeDatas(Collection<ChangeData> in) throws OrmException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    ensureLoaded(in);
    List<ChangeInfo> out = new ArrayList<>(in.size());
    for (ChangeData cd : in) {
      out.add(format(cd));
    }
    accountLoader.fill();
    return out;
  }

  private void ensureLoaded(Iterable<ChangeData> all) throws OrmException {
    if (lazyLoad) {
      ChangeData.ensureChangeLoaded(all);
      if (has(ALL_REVISIONS)) {
        ChangeData.ensureAllPatchSetsLoaded(all);
      } else if (has(CURRENT_REVISION) || has(MESSAGES)) {
        ChangeData.ensureCurrentPatchSetLoaded(all);
      }
      if (has(REVIEWED) && userProvider.get().isIdentifiedUser()) {
        ChangeData.ensureReviewedByLoadedForOpenChanges(all);
      }
      ChangeData.ensureCurrentApprovalsLoaded(all);
    } else {
      for (ChangeData cd : all) {
        cd.setLazyLoad(false);
      }
    }
  }

  private boolean has(ListChangesOption option) {
    return options.contains(option);
  }

  private List<ChangeInfo> toChangeInfo(Map<Change.Id, ChangeInfo> out, List<ChangeData> changes) {
    List<ChangeInfo> info = Lists.newArrayListWithCapacity(changes.size());
    for (ChangeData cd : changes) {
      ChangeInfo i = out.get(cd.getId());
      if (i == null) {
        try {
          i = toChangeInfo(cd, Optional.empty());
        } catch (PatchListNotAvailableException
            | GpgException
            | OrmException
            | IOException
            | PermissionBackendException
            | NoSuchProjectException
            | RuntimeException e) {
          if (has(CHECK)) {
            i = checkOnly(cd);
          } else if (e instanceof NoSuchChangeException) {
            log.info(
                "NoSuchChangeException: Omitting corrupt change "
                    + cd.getId()
                    + " from results. Seems to be stale in the index.");
            continue;
          } else {
            log.warn("Omitting corrupt change " + cd.getId() + " from results", e);
            continue;
          }
        }
        out.put(cd.getId(), i);
      }
      info.add(i);
    }
    return info;
  }

  private ChangeInfo checkOnly(ChangeData cd) {
    ChangeNotes notes;
    try {
      notes = cd.notes();
    } catch (OrmException e) {
      String msg = "Error loading change";
      log.warn(msg + " " + cd.getId(), e);
      ChangeInfo info = new ChangeInfo();
      info._number = cd.getId().get();
      ProblemInfo p = new ProblemInfo();
      p.message = msg;
      info.problems = Lists.newArrayList(p);
      return info;
    }

    ConsistencyChecker.Result result = checkerProvider.get().check(notes, fix);
    ChangeInfo info;
    Change c = result.change();
    if (c != null) {
      info = new ChangeInfo();
      info.project = c.getProject().get();
      info.branch = c.getDest().getShortName();
      info.topic = c.getTopic();
      info.changeId = c.getKey().get();
      info.subject = c.getSubject();
      info.status = c.getStatus().asChangeStatus();
      info.owner = new AccountInfo(c.getOwner().get());
      info.created = c.getCreatedOn();
      info.updated = c.getLastUpdatedOn();
      info._number = c.getId().get();
      info.problems = result.problems();
      info.isPrivate = c.isPrivate() ? true : null;
      info.workInProgress = c.isWorkInProgress() ? true : null;
      info.hasReviewStarted = c.hasReviewStarted();
      finish(info);
    } else {
      info = new ChangeInfo();
      info._number = result.id().get();
      info.problems = result.problems();
    }
    return info;
  }

  private ChangeInfo toChangeInfo(ChangeData cd, Optional<PatchSet.Id> limitToPsId)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException,
          PermissionBackendException, NoSuchProjectException {
    ChangeInfo out = new ChangeInfo();
    CurrentUser user = userProvider.get();

    if (has(CHECK)) {
      out.problems = checkerProvider.get().check(cd.notes(), fix).problems();
      // If any problems were fixed, the ChangeData needs to be reloaded.
      for (ProblemInfo p : out.problems) {
        if (p.status == ProblemInfo.Status.FIXED) {
          cd = changeDataFactory.create(cd.db(), cd.project(), cd.getId());
          break;
        }
      }
    }

    PermissionBackend.ForChange perm = permissionBackendForChange(user, cd);
    Change in = cd.change();
    out.project = in.getProject().get();
    out.branch = in.getDest().getShortName();
    out.topic = in.getTopic();
    if (indexes.getSearchIndex().getSchema().hasField(ChangeField.ASSIGNEE)) {
      if (in.getAssignee() != null) {
        out.assignee = accountLoader.get(in.getAssignee());
      }
    }
    out.hashtags = cd.hashtags();
    out.changeId = in.getKey().get();
    if (in.getStatus().isOpen()) {
      SubmitTypeRecord str = cd.submitTypeRecord();
      if (str.isOk()) {
        out.submitType = str.type;
      }
      out.mergeable = cd.isMergeable();
      if (has(SUBMITTABLE)) {
        out.submittable = submittable(cd);
      }
    }
    Optional<ChangedLines> changedLines = cd.changedLines();
    if (changedLines.isPresent()) {
      out.insertions = changedLines.get().insertions;
      out.deletions = changedLines.get().deletions;
    }
    out.isPrivate = in.isPrivate() ? true : null;
    out.workInProgress = in.isWorkInProgress() ? true : null;
    out.hasReviewStarted = in.hasReviewStarted();
    out.subject = in.getSubject();
    out.status = in.getStatus().asChangeStatus();
    out.owner = accountLoader.get(in.getOwner());
    out.created = in.getCreatedOn();
    out.updated = in.getLastUpdatedOn();
    out._number = in.getId().get();
    out.unresolvedCommentCount = cd.unresolvedCommentCount();

    if (user.isIdentifiedUser()) {
      Collection<String> stars = cd.stars(user.getAccountId());
      out.starred = stars.contains(StarredChangesUtil.DEFAULT_LABEL) ? true : null;
      if (!stars.isEmpty()) {
        out.stars = stars;
      }
    }

    if (in.getStatus().isOpen() && has(REVIEWED) && user.isIdentifiedUser()) {
      out.reviewed = cd.isReviewedBy(user.getAccountId()) ? true : null;
    }

    out.labels = labelsFor(perm, cd, has(LABELS), has(DETAILED_LABELS));

    if (out.labels != null && has(DETAILED_LABELS)) {
      // If limited to specific patch sets but not the current patch set, don't
      // list permitted labels, since users can't vote on those patch sets.
      if (user.isIdentifiedUser()
          && (!limitToPsId.isPresent() || limitToPsId.get().equals(in.currentPatchSetId()))) {
        out.permittedLabels =
            cd.change().getStatus() != Change.Status.ABANDONED
                ? permittedLabels(perm, cd)
                : ImmutableMap.of();
      }

      out.reviewers = reviewerMap(cd.reviewers(), cd.reviewersByEmail(), false);
      out.pendingReviewers = reviewerMap(cd.pendingReviewers(), cd.pendingReviewersByEmail(), true);
      out.removableReviewers = removableReviewers(cd, out);
    }

    setSubmitter(cd, out);
    out.plugins =
        pluginDefinedAttributesFactory != null ? pluginDefinedAttributesFactory.create(cd) : null;
    out.revertOf = cd.change().getRevertOf() != null ? cd.change().getRevertOf().get() : null;

    if (has(REVIEWER_UPDATES)) {
      out.reviewerUpdates = reviewerUpdates(cd);
    }

    boolean needMessages = has(MESSAGES);
    boolean needRevisions = has(ALL_REVISIONS) || has(CURRENT_REVISION) || limitToPsId.isPresent();
    Map<PatchSet.Id, PatchSet> src;
    if (needMessages || needRevisions) {
      src = loadPatchSets(cd, limitToPsId);
    } else {
      src = null;
    }

    if (needMessages) {
      out.messages = messages(cd);
    }
    finish(out);

    // This block must come after the ChangeInfo is mostly populated, since
    // it will be passed to ActionVisitors as-is.
    if (needRevisions) {
      out.revisions = revisions(cd, src, limitToPsId, out);
      if (out.revisions != null) {
        for (Map.Entry<String, RevisionInfo> entry : out.revisions.entrySet()) {
          if (entry.getValue().isCurrent) {
            out.currentRevision = entry.getKey();
            break;
          }
        }
      }
    }

    if (has(CURRENT_ACTIONS) || has(CHANGE_ACTIONS)) {
      actionJson.addChangeActions(out, cd.notes());
    }

    if (has(TRACKING_IDS)) {
      ListMultimap<String, String> set = trackingFooters.extract(cd.commitFooters());
      out.trackingIds =
          set.entries().stream()
              .map(e -> new TrackingIdInfo(e.getKey(), e.getValue()))
              .collect(toList());
    }

    return out;
  }

  private Map<ReviewerState, Collection<AccountInfo>> reviewerMap(
      ReviewerSet reviewers, ReviewerByEmailSet reviewersByEmail, boolean includeRemoved) {
    Map<ReviewerState, Collection<AccountInfo>> reviewerMap = new HashMap<>();
    for (ReviewerStateInternal state : ReviewerStateInternal.values()) {
      if (!includeRemoved && state == ReviewerStateInternal.REMOVED) {
        continue;
      }
      Collection<AccountInfo> reviewersByState = toAccountInfo(reviewers.byState(state));
      reviewersByState.addAll(toAccountInfoByEmail(reviewersByEmail.byState(state)));
      if (!reviewersByState.isEmpty()) {
        reviewerMap.put(state.asReviewerState(), reviewersByState);
      }
    }
    return reviewerMap;
  }

  private Collection<ReviewerUpdateInfo> reviewerUpdates(ChangeData cd) throws OrmException {
    List<ReviewerStatusUpdate> reviewerUpdates = cd.reviewerUpdates();
    List<ReviewerUpdateInfo> result = new ArrayList<>(reviewerUpdates.size());
    for (ReviewerStatusUpdate c : reviewerUpdates) {
      ReviewerUpdateInfo change = new ReviewerUpdateInfo();
      change.updated = c.date();
      change.state = c.state().asReviewerState();
      change.updatedBy = accountLoader.get(c.updatedBy());
      change.reviewer = accountLoader.get(c.reviewer());
      result.add(change);
    }
    return result;
  }

  private boolean submittable(ChangeData cd) throws OrmException {
    return SubmitRecord.findOkRecord(cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT)).isPresent();
  }

  private List<SubmitRecord> submitRecords(ChangeData cd) throws OrmException {
    return cd.submitRecords(SUBMIT_RULE_OPTIONS_LENIENT);
  }

  private Map<String, LabelInfo> labelsFor(
      PermissionBackend.ForChange perm, ChangeData cd, boolean standard, boolean detailed)
      throws OrmException, PermissionBackendException {
    if (!standard && !detailed) {
      return null;
    }

    LabelTypes labelTypes = cd.getLabelTypes();
    Map<String, LabelWithStatus> withStatus =
        cd.change().getStatus() == Change.Status.MERGED
            ? labelsForSubmittedChange(perm, cd, labelTypes, standard, detailed)
            : labelsForUnsubmittedChange(perm, cd, labelTypes, standard, detailed);
    return ImmutableMap.copyOf(Maps.transformValues(withStatus, LabelWithStatus::label));
  }

  private Map<String, LabelWithStatus> labelsForUnsubmittedChange(
      PermissionBackend.ForChange perm,
      ChangeData cd,
      LabelTypes labelTypes,
      boolean standard,
      boolean detailed)
      throws OrmException, PermissionBackendException {
    Map<String, LabelWithStatus> labels = initLabels(cd, labelTypes, standard);
    if (detailed) {
      setAllApprovals(perm, cd, labels);
    }
    for (Map.Entry<String, LabelWithStatus> e : labels.entrySet()) {
      LabelType type = labelTypes.byLabel(e.getKey());
      if (type == null) {
        continue;
      }
      if (standard) {
        for (PatchSetApproval psa : cd.currentApprovals()) {
          if (type.matches(psa)) {
            short val = psa.getValue();
            Account.Id accountId = psa.getAccountId();
            setLabelScores(type, e.getValue(), val, accountId);
          }
        }
      }
      if (detailed) {
        setLabelValues(type, e.getValue());
      }
    }
    return labels;
  }

  private Map<String, LabelWithStatus> initLabels(
      ChangeData cd, LabelTypes labelTypes, boolean standard) throws OrmException {
    Map<String, LabelWithStatus> labels = new TreeMap<>(labelTypes.nameComparator());
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelWithStatus p = labels.get(r.label);
        if (p == null || p.status().compareTo(r.status) < 0) {
          LabelInfo n = new LabelInfo();
          if (standard) {
            switch (r.status) {
              case OK:
                n.approved = accountLoader.get(r.appliedBy);
                break;
              case REJECT:
                n.rejected = accountLoader.get(r.appliedBy);
                n.blocking = true;
                break;
              case IMPOSSIBLE:
              case MAY:
              case NEED:
              default:
                break;
            }
          }

          n.optional = r.status == SubmitRecord.Label.Status.MAY ? true : null;
          labels.put(r.label, LabelWithStatus.create(n, r.status));
        }
      }
    }
    return labels;
  }

  private void setLabelScores(
      LabelType type, LabelWithStatus l, short score, Account.Id accountId) {
    if (l.label().approved != null || l.label().rejected != null) {
      return;
    }

    if (type.getMin() == null || type.getMax() == null) {
      // Can't set score for unknown or misconfigured type.
      return;
    }

    if (score != 0) {
      if (score == type.getMin().getValue()) {
        l.label().rejected = accountLoader.get(accountId);
      } else if (score == type.getMax().getValue()) {
        l.label().approved = accountLoader.get(accountId);
      } else if (score < 0) {
        l.label().disliked = accountLoader.get(accountId);
        l.label().value = score;
      } else if (score > 0 && l.label().disliked == null) {
        l.label().recommended = accountLoader.get(accountId);
        l.label().value = score;
      }
    }
  }

  private void setAllApprovals(
      PermissionBackend.ForChange basePerm, ChangeData cd, Map<String, LabelWithStatus> labels)
      throws OrmException, PermissionBackendException {
    Change.Status status = cd.change().getStatus();
    checkState(
        status != Change.Status.MERGED, "should not call setAllApprovals on %s change", status);

    // Include a user in the output for this label if either:
    //  - They are an explicit reviewer.
    //  - They ever voted on this change.
    Set<Account.Id> allUsers = new HashSet<>();
    allUsers.addAll(cd.reviewers().byState(ReviewerStateInternal.REVIEWER));
    for (PatchSetApproval psa : cd.approvals().values()) {
      allUsers.add(psa.getAccountId());
    }

    Table<Account.Id, String, PatchSetApproval> current =
        HashBasedTable.create(allUsers.size(), cd.getLabelTypes().getLabelTypes().size());
    for (PatchSetApproval psa : cd.currentApprovals()) {
      current.put(psa.getAccountId(), psa.getLabel(), psa);
    }

    LabelTypes labelTypes = cd.getLabelTypes();
    for (Account.Id accountId : allUsers) {
      PermissionBackend.ForChange perm = basePerm.user(userFactory.create(accountId));
      Map<String, VotingRangeInfo> pvr = getPermittedVotingRanges(permittedLabels(perm, cd));
      for (Map.Entry<String, LabelWithStatus> e : labels.entrySet()) {
        LabelType lt = labelTypes.byLabel(e.getKey());
        if (lt == null) {
          // Ignore submit record for undefined label; likely the submit rule
          // author didn't intend for the label to show up in the table.
          continue;
        }
        Integer value;
        VotingRangeInfo permittedVotingRange = pvr.getOrDefault(lt.getName(), null);
        String tag = null;
        Timestamp date = null;
        PatchSetApproval psa = current.get(accountId, lt.getName());
        if (psa != null) {
          value = Integer.valueOf(psa.getValue());
          if (value == 0) {
            // This may be a dummy approval that was inserted when the reviewer
            // was added. Explicitly check whether the user can vote on this
            // label.
            value = perm.test(new LabelPermission(lt)) ? 0 : null;
          }
          tag = psa.getTag();
          date = psa.getGranted();
          if (psa.isPostSubmit()) {
            log.warn("unexpected post-submit approval on open change: {}", psa);
          }
        } else {
          // Either the user cannot vote on this label, or they were added as a
          // reviewer but have not responded yet. Explicitly check whether the
          // user can vote on this label.
          value = perm.test(new LabelPermission(lt)) ? 0 : null;
        }
        addApproval(
            e.getValue().label(), approvalInfo(accountId, value, permittedVotingRange, tag, date));
      }
    }
  }

  private Map<String, VotingRangeInfo> getPermittedVotingRanges(
      Map<String, Collection<String>> permittedLabels) {
    Map<String, VotingRangeInfo> permittedVotingRanges =
        Maps.newHashMapWithExpectedSize(permittedLabels.size());
    for (String label : permittedLabels.keySet()) {
      List<Integer> permittedVotingRange =
          permittedLabels.get(label).stream()
              .map(this::parseRangeValue)
              .filter(java.util.Objects::nonNull)
              .sorted()
              .collect(toList());

      if (permittedVotingRange.isEmpty()) {
        permittedVotingRanges.put(label, null);
      } else {
        int minPermittedValue = permittedVotingRange.get(0);
        int maxPermittedValue = Iterables.getLast(permittedVotingRange);
        permittedVotingRanges.put(label, new VotingRangeInfo(minPermittedValue, maxPermittedValue));
      }
    }
    return permittedVotingRanges;
  }

  private Integer parseRangeValue(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    } else if (value.startsWith(" ")) {
      value = value.trim();
    }
    return Ints.tryParse(value);
  }

  private void setSubmitter(ChangeData cd, ChangeInfo out) throws OrmException {
    Optional<PatchSetApproval> s = cd.getSubmitApproval();
    if (!s.isPresent()) {
      return;
    }
    out.submitted = s.get().getGranted();
    out.submitter = accountLoader.get(s.get().getAccountId());
  }

  private Map<String, LabelWithStatus> labelsForSubmittedChange(
      PermissionBackend.ForChange basePerm,
      ChangeData cd,
      LabelTypes labelTypes,
      boolean standard,
      boolean detailed)
      throws OrmException, PermissionBackendException {
    Set<Account.Id> allUsers = new HashSet<>();
    if (detailed) {
      // Users expect to see all reviewers on closed changes, even if they
      // didn't vote on the latest patch set. If we don't need detailed labels,
      // we aren't including 0 votes for all users below, so we can just look at
      // the latest patch set (in the next loop).
      for (PatchSetApproval psa : cd.approvals().values()) {
        allUsers.add(psa.getAccountId());
      }
    }

    Set<String> labelNames = new HashSet<>();
    SetMultimap<Account.Id, PatchSetApproval> current =
        MultimapBuilder.hashKeys().hashSetValues().build();
    for (PatchSetApproval a : cd.currentApprovals()) {
      allUsers.add(a.getAccountId());
      LabelType type = labelTypes.byLabel(a.getLabelId());
      if (type != null) {
        labelNames.add(type.getName());
        // Not worth the effort to distinguish between votable/non-votable for 0
        // values on closed changes, since they can't vote anyway.
        current.put(a.getAccountId(), a);
      }
    }

    // Since voting on merged changes is allowed all labels which apply to
    // the change must be returned. All applying labels can be retrieved from
    // the submit records, which is what initLabels does.
    // It's not possible to only compute the labels based on the approvals
    // since merged changes may not have approvals for all labels (e.g. if not
    // all labels are required for submit or if the change was auto-closed due
    // to direct push or if new labels were defined after the change was
    // merged).
    Map<String, LabelWithStatus> labels;
    labels = initLabels(cd, labelTypes, standard);

    // Also include all labels for which approvals exists. E.g. there can be
    // approvals for labels that are ignored by a Prolog submit rule and hence
    // it wouldn't be included in the submit records.
    for (String name : labelNames) {
      if (!labels.containsKey(name)) {
        labels.put(name, LabelWithStatus.create(new LabelInfo(), null));
      }
    }

    if (detailed) {
      labels.entrySet().stream()
          .filter(e -> labelTypes.byLabel(e.getKey()) != null)
          .forEach(e -> setLabelValues(labelTypes.byLabel(e.getKey()), e.getValue()));
    }

    for (Account.Id accountId : allUsers) {
      Map<String, ApprovalInfo> byLabel = Maps.newHashMapWithExpectedSize(labels.size());
      Map<String, VotingRangeInfo> pvr = Collections.emptyMap();
      if (detailed) {
        PermissionBackend.ForChange perm = basePerm.user(userFactory.create(accountId));
        pvr = getPermittedVotingRanges(permittedLabels(perm, cd));
        for (Map.Entry<String, LabelWithStatus> entry : labels.entrySet()) {
          ApprovalInfo ai = approvalInfo(accountId, 0, null, null, null);
          byLabel.put(entry.getKey(), ai);
          addApproval(entry.getValue().label(), ai);
        }
      }
      for (PatchSetApproval psa : current.get(accountId)) {
        LabelType type = labelTypes.byLabel(psa.getLabelId());
        if (type == null) {
          continue;
        }

        short val = psa.getValue();
        ApprovalInfo info = byLabel.get(type.getName());
        if (info != null) {
          info.value = Integer.valueOf(val);
          info.permittedVotingRange = pvr.getOrDefault(type.getName(), null);
          info.date = psa.getGranted();
          info.tag = psa.getTag();
          if (psa.isPostSubmit()) {
            info.postSubmit = true;
          }
        }
        if (!standard) {
          continue;
        }

        setLabelScores(type, labels.get(type.getName()), val, accountId);
      }
    }
    return labels;
  }

  private ApprovalInfo approvalInfo(
      Account.Id id,
      Integer value,
      VotingRangeInfo permittedVotingRange,
      String tag,
      Timestamp date) {
    ApprovalInfo ai = getApprovalInfo(id, value, permittedVotingRange, tag, date);
    accountLoader.put(ai);
    return ai;
  }

  public static ApprovalInfo getApprovalInfo(
      Account.Id id,
      Integer value,
      @Nullable VotingRangeInfo permittedVotingRange,
      @Nullable String tag,
      Timestamp date) {
    ApprovalInfo ai = new ApprovalInfo(id.get());
    ai.value = value;
    ai.permittedVotingRange = permittedVotingRange;
    ai.date = date;
    ai.tag = tag;
    return ai;
  }

  private static boolean isOnlyZero(Collection<String> values) {
    return values.isEmpty() || (values.size() == 1 && values.contains(" 0"));
  }

  private void setLabelValues(LabelType type, LabelWithStatus l) {
    l.label().defaultValue = type.getDefaultValue();
    l.label().values = new LinkedHashMap<>();
    for (LabelValue v : type.getValues()) {
      l.label().values.put(v.formatValue(), v.getText());
    }
    if (isOnlyZero(l.label().values.keySet())) {
      l.label().values = null;
    }
  }

  private Map<String, Collection<String>> permittedLabels(
      PermissionBackend.ForChange perm, ChangeData cd)
      throws OrmException, PermissionBackendException {
    boolean isMerged = cd.change().getStatus() == Change.Status.MERGED;
    LabelTypes labelTypes = cd.getLabelTypes();
    Map<String, LabelType> toCheck = new HashMap<>();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels != null) {
        for (SubmitRecord.Label r : rec.labels) {
          LabelType type = labelTypes.byLabel(r.label);
          if (type != null && (!isMerged || type.allowPostSubmit())) {
            toCheck.put(type.getName(), type);
          }
        }
      }
    }

    Map<String, Short> labels = null;
    Set<LabelPermission.WithValue> can = perm.testLabels(toCheck.values());
    SetMultimap<String, String> permitted = LinkedHashMultimap.create();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelType type = labelTypes.byLabel(r.label);
        if (type == null || (isMerged && !type.allowPostSubmit())) {
          continue;
        }

        for (LabelValue v : type.getValues()) {
          boolean ok = can.contains(new LabelPermission.WithValue(type, v));
          if (isMerged) {
            if (labels == null) {
              labels = currentLabels(perm, cd);
            }
            short prev = labels.getOrDefault(type.getName(), (short) 0);
            ok &= v.getValue() >= prev;
          }
          if (ok) {
            permitted.put(r.label, v.formatValue());
          }
        }
      }
    }

    List<String> toClear = Lists.newArrayListWithCapacity(permitted.keySet().size());
    for (Map.Entry<String, Collection<String>> e : permitted.asMap().entrySet()) {
      if (isOnlyZero(e.getValue())) {
        toClear.add(e.getKey());
      }
    }
    for (String label : toClear) {
      permitted.removeAll(label);
    }
    return permitted.asMap();
  }

  private Map<String, Short> currentLabels(PermissionBackend.ForChange perm, ChangeData cd)
      throws OrmException {
    IdentifiedUser user = perm.user().asIdentifiedUser();
    Map<String, Short> result = new HashMap<>();
    for (PatchSetApproval psa :
        approvalsUtil.byPatchSetUser(
            db.get(),
            lazyLoad ? cd.notes() : notesFactory.createFromIndexedChange(cd.change()),
            user,
            cd.change().currentPatchSetId(),
            user.getAccountId(),
            null,
            null)) {
      result.put(psa.getLabel(), psa.getValue());
    }
    return result;
  }

  private Collection<ChangeMessageInfo> messages(ChangeData cd) throws OrmException {
    List<ChangeMessage> messages = cmUtil.byChange(db.get(), cd.notes());
    if (messages.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeMessageInfo> result = Lists.newArrayListWithCapacity(messages.size());
    for (ChangeMessage message : messages) {
      PatchSet.Id patchNum = message.getPatchSetId();
      ChangeMessageInfo cmi = new ChangeMessageInfo();
      cmi.id = message.getKey().get();
      cmi.author = accountLoader.get(message.getAuthor());
      cmi.date = message.getWrittenOn();
      cmi.message = message.getMessage();
      cmi.tag = message.getTag();
      cmi._revisionNumber = patchNum != null ? patchNum.get() : null;
      Account.Id realAuthor = message.getRealAuthor();
      if (realAuthor != null) {
        cmi.realAuthor = accountLoader.get(realAuthor);
      }
      result.add(cmi);
    }
    return result;
  }

  private Collection<AccountInfo> removableReviewers(ChangeData cd, ChangeInfo out)
      throws PermissionBackendException, NoSuchProjectException, OrmException, IOException {
    // Although this is called removableReviewers, this method also determines
    // which CCs are removable.
    //
    // For reviewers, we need to look at each approval, because the reviewer
    // should only be considered removable if *all* of their approvals can be
    // removed. First, add all reviewers with *any* removable approval to the
    // "removable" set. Along the way, if we encounter a non-removable approval,
    // add the reviewer to the "fixed" set. Before we return, remove all members
    // of "fixed" from "removable", because not all of their approvals can be
    // removed.
    Collection<LabelInfo> labels = out.labels.values();
    Set<Account.Id> fixed = Sets.newHashSetWithExpectedSize(labels.size());
    Set<Account.Id> removable = Sets.newHashSetWithExpectedSize(labels.size());
    for (LabelInfo label : labels) {
      if (label.all == null) {
        continue;
      }
      for (ApprovalInfo ai : label.all) {
        Account.Id id = new Account.Id(ai._accountId);

        if (removeReviewerControl.testRemoveReviewer(
            cd, userProvider.get(), id, MoreObjects.firstNonNull(ai.value, 0))) {
          removable.add(id);
        } else {
          fixed.add(id);
        }
      }
    }

    // CCs are simpler than reviewers. They are removable if the ChangeControl
    // would permit a non-negative approval by that account to be removed, in
    // which case add them to removable. We don't need to add unremovable CCs to
    // "fixed" because we only visit each CC once here.
    Collection<AccountInfo> ccs = out.reviewers.get(ReviewerState.CC);
    if (ccs != null) {
      for (AccountInfo ai : ccs) {
        if (ai._accountId != null) {
          Account.Id id = new Account.Id(ai._accountId);
          if (removeReviewerControl.testRemoveReviewer(cd, userProvider.get(), id, 0)) {
            removable.add(id);
          }
        }
      }
    }

    // Subtract any reviewers with non-removable approvals from the "removable"
    // set. This also subtracts any CCs that for some reason also hold
    // unremovable approvals.
    removable.removeAll(fixed);

    List<AccountInfo> result = Lists.newArrayListWithCapacity(removable.size());
    for (Account.Id id : removable) {
      result.add(accountLoader.get(id));
    }
    // Reviewers added by email are always removable
    for (Collection<AccountInfo> infos : out.reviewers.values()) {
      for (AccountInfo info : infos) {
        if (info._accountId == null) {
          result.add(info);
        }
      }
    }
    return result;
  }

  private Collection<AccountInfo> toAccountInfo(Collection<Account.Id> accounts) {
    return accounts.stream()
        .map(accountLoader::get)
        .sorted(AccountInfoComparator.ORDER_NULLS_FIRST)
        .collect(toList());
  }

  private Collection<AccountInfo> toAccountInfoByEmail(Collection<Address> addresses) {
    return addresses.stream()
        .map(a -> new AccountInfo(a.getName(), a.getEmail()))
        .sorted(AccountInfoComparator.ORDER_NULLS_FIRST)
        .collect(toList());
  }

  @Nullable
  private Repository openRepoIfNecessary(Project.NameKey project) throws IOException {
    if (has(ALL_COMMITS) || has(CURRENT_COMMIT) || has(COMMIT_FOOTERS)) {
      return repoManager.openRepository(project);
    }
    return null;
  }

  @Nullable
  private RevWalk newRevWalk(@Nullable Repository repo) {
    return repo != null ? new RevWalk(repo) : null;
  }

  private Map<String, RevisionInfo> revisions(
      ChangeData cd,
      Map<PatchSet.Id, PatchSet> map,
      Optional<PatchSet.Id> limitToPsId,
      ChangeInfo changeInfo)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException,
          PermissionBackendException {
    Map<String, RevisionInfo> res = new LinkedHashMap<>();
    Boolean isWorldReadable = null;
    try (Repository repo = openRepoIfNecessary(cd.project());
        RevWalk rw = newRevWalk(repo)) {
      for (PatchSet in : map.values()) {
        PatchSet.Id id = in.getId();
        boolean want = false;
        if (has(ALL_REVISIONS)) {
          want = true;
        } else if (limitToPsId.isPresent()) {
          want = id.equals(limitToPsId.get());
        } else {
          want = id.equals(cd.change().currentPatchSetId());
        }
        if (want) {
          if (isWorldReadable == null) {
            isWorldReadable = isWorldReadable(cd);
          }
          res.put(
              in.getRevision().get(),
              toRevisionInfo(cd, in, repo, rw, false, changeInfo, isWorldReadable));
        }
      }
      return res;
    }
  }

  private Map<PatchSet.Id, PatchSet> loadPatchSets(ChangeData cd, Optional<PatchSet.Id> limitToPsId)
      throws OrmException {
    Collection<PatchSet> src;
    if (has(ALL_REVISIONS) || has(MESSAGES)) {
      src = cd.patchSets();
    } else {
      PatchSet ps;
      if (limitToPsId.isPresent()) {
        ps = cd.patchSet(limitToPsId.get());
        if (ps == null) {
          throw new OrmException("missing patch set " + limitToPsId.get());
        }
      } else {
        ps = cd.currentPatchSet();
        if (ps == null) {
          throw new OrmException("missing current patch set for change " + cd.getId());
        }
      }
      src = Collections.singletonList(ps);
    }
    Map<PatchSet.Id, PatchSet> map = Maps.newHashMapWithExpectedSize(src.size());
    for (PatchSet patchSet : src) {
      map.put(patchSet.getId(), patchSet);
    }
    return map;
  }

  public RevisionInfo getRevisionInfo(ChangeData cd, PatchSet in)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException,
          PermissionBackendException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    try (Repository repo = openRepoIfNecessary(cd.project());
        RevWalk rw = newRevWalk(repo)) {
      RevisionInfo rev = toRevisionInfo(cd, in, repo, rw, true, null, isWorldReadable(cd));
      accountLoader.fill();
      return rev;
    }
  }

  private RevisionInfo toRevisionInfo(
      ChangeData cd,
      PatchSet in,
      @Nullable Repository repo,
      @Nullable RevWalk rw,
      boolean fillCommit,
      @Nullable ChangeInfo changeInfo,
      boolean isWorldReadable)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException {
    Change c = cd.change();
    RevisionInfo out = new RevisionInfo();
    out.isCurrent = in.getId().equals(c.currentPatchSetId());
    out._number = in.getId().get();
    out.ref = in.getRefName();
    out.created = in.getCreatedOn();
    out.uploader = accountLoader.get(in.getUploader());
    out.fetch = makeFetchMap(cd, in, isWorldReadable);
    out.kind = changeKindCache.getChangeKind(rw, repo != null ? repo.getConfig() : null, cd, in);
    out.description = in.getDescription();

    boolean setCommit = has(ALL_COMMITS) || (out.isCurrent && has(CURRENT_COMMIT));
    boolean addFooters = out.isCurrent && has(COMMIT_FOOTERS);
    if (setCommit || addFooters) {
      checkState(rw != null);
      checkState(repo != null);
      Project.NameKey project = c.getProject();
      String rev = in.getRevision().get();
      RevCommit commit = rw.parseCommit(ObjectId.fromString(rev));
      rw.parseBody(commit);
      if (setCommit) {
        out.commit = toCommit(project, rw, commit, has(WEB_LINKS), fillCommit);
      }
      if (addFooters) {
        Ref ref = repo.exactRef(cd.change().getDest().get());
        RevCommit mergeTip = null;
        if (ref != null) {
          mergeTip = rw.parseCommit(ref.getObjectId());
          rw.parseBody(mergeTip);
        }
        out.commitWithFooters =
            mergeUtilFactory
                .create(projectCache.get(project))
                .createCommitMessageOnSubmit(
                    commit, mergeTip, cd.notes(), userProvider.get(), in.getId());
      }
    }

    if (has(ALL_FILES) || (out.isCurrent && has(CURRENT_FILES))) {
      out.files = fileInfoJson.toFileInfoMap(c, in);
      out.files.remove(Patch.COMMIT_MSG);
      out.files.remove(Patch.MERGE_LIST);
    }

    if (out.isCurrent && has(CURRENT_ACTIONS) && userProvider.get().isIdentifiedUser()) {

      actionJson.addRevisionActions(
          changeInfo,
          out,
          new RevisionResource(changeResourceFactory.create(cd.notes(), userProvider.get()), in));
    }

    if (gpgApi.isEnabled() && has(PUSH_CERTIFICATES)) {
      if (in.getPushCertificate() != null) {
        out.pushCertificate =
            gpgApi.checkPushCertificate(
                in.getPushCertificate(), userFactory.create(in.getUploader()));
      } else {
        out.pushCertificate = new PushCertificateInfo();
      }
    }

    return out;
  }

  CommitInfo toCommit(
      Project.NameKey project, RevWalk rw, RevCommit commit, boolean addLinks, boolean fillCommit)
      throws IOException {
    CommitInfo info = new CommitInfo();
    if (fillCommit) {
      info.commit = commit.name();
    }
    info.parents = new ArrayList<>(commit.getParentCount());
    info.author = toGitPerson(commit.getAuthorIdent());
    info.committer = toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();

    if (addLinks) {
      List<WebLinkInfo> links = webLinks.getPatchSetLinks(project, commit.name());
      info.webLinks = links.isEmpty() ? null : links;
    }

    for (RevCommit parent : commit.getParents()) {
      rw.parseBody(parent);
      CommitInfo i = new CommitInfo();
      i.commit = parent.name();
      i.subject = parent.getShortMessage();
      if (addLinks) {
        List<WebLinkInfo> parentLinks = webLinks.getParentLinks(project, parent.name());
        i.webLinks = parentLinks.isEmpty() ? null : parentLinks;
      }
      info.parents.add(i);
    }
    return info;
  }

  private Map<String, FetchInfo> makeFetchMap(ChangeData cd, PatchSet in, boolean isWorldReadable) {
    Map<String, FetchInfo> r = new LinkedHashMap<>();
    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      String schemeName = e.getExportName();
      DownloadScheme scheme = e.getProvider().get();
      if (!scheme.isEnabled()
          || (scheme.isAuthRequired() && !userProvider.get().isIdentifiedUser())) {
        continue;
      }
      if (!scheme.isAuthSupported() && !isWorldReadable) {
        continue;
      }

      String projectName = cd.project().get();
      String url = scheme.getUrl(projectName);
      String refName = in.getRefName();
      FetchInfo fetchInfo = new FetchInfo(url, refName);
      r.put(schemeName, fetchInfo);

      if (has(DOWNLOAD_COMMANDS)) {
        populateFetchMap(scheme, downloadCommands, projectName, refName, fetchInfo);
      }
    }

    return r;
  }

  public static void populateFetchMap(
      DownloadScheme scheme,
      DynamicMap<DownloadCommand> commands,
      String projectName,
      String refName,
      FetchInfo fetchInfo) {
    for (DynamicMap.Entry<DownloadCommand> e2 : commands) {
      String commandName = e2.getExportName();
      DownloadCommand command = e2.getProvider().get();
      String c = command.getCommand(scheme, projectName, refName);
      if (c != null) {
        addCommand(fetchInfo, commandName, c);
      }
    }
  }

  private static void addCommand(FetchInfo fetchInfo, String commandName, String c) {
    if (fetchInfo.commands == null) {
      fetchInfo.commands = new TreeMap<>();
    }
    fetchInfo.commands.put(commandName, c);
  }

  static void finish(ChangeInfo info) {
    info.id =
        Joiner.on('~')
            .join(Url.encode(info.project), Url.encode(info.branch), Url.encode(info.changeId));
  }

  private static void addApproval(LabelInfo label, ApprovalInfo approval) {
    if (label.all == null) {
      label.all = new ArrayList<>();
    }
    label.all.add(approval);
  }

  /**
   * @return {@link com.google.gerrit.server.permissions.PermissionBackend.ForChange} constructed
   *     from either an index-backed or a database-backed {@link ChangeData} depending on {@code
   *     lazyload}.
   */
  private PermissionBackend.ForChange permissionBackendForChange(CurrentUser user, ChangeData cd)
      throws OrmException {
    PermissionBackend.WithUser withUser = permissionBackend.user(user).database(db);
    return lazyLoad
        ? withUser.change(cd)
        : withUser.indexedChange(cd, notesFactory.createFromIndexedChange(cd.change()));
  }

  private boolean isWorldReadable(ChangeData cd) throws OrmException, PermissionBackendException {
    try {
      permissionBackendForChange(anonymous, cd).check(ChangePermission.READ);
      return true;
    } catch (AuthException ae) {
      return false;
    }
  }

  @AutoValue
  abstract static class LabelWithStatus {
    private static LabelWithStatus create(LabelInfo label, SubmitRecord.Label.Status status) {
      return new AutoValue_ChangeJson_LabelWithStatus(label, status);
    }

    abstract LabelInfo label();

    @Nullable
    abstract SubmitRecord.Label.Status status();
  }
}
