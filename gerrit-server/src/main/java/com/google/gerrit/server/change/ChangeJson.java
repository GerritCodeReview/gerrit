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
import static com.google.gerrit.extensions.client.ListChangesOption.WEB_LINKS;
import static com.google.gerrit.server.CommonConverters.toGitPerson;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ListChangesOption;
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
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gerrit.server.api.accounts.GpgApiAdapter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.QueryResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jgit.lib.ObjectId;
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

  public static final Set<ListChangesOption> NO_OPTIONS = Collections.emptySet();

  public static final ImmutableSet<ListChangesOption> REQUIRE_LAZY_LOAD =
      ImmutableSet.of(ALL_REVISIONS, MESSAGES);

  public interface Factory {
    ChangeJson create(Set<ListChangesOption> options);
  }

  private final Provider<ReviewDb> db;
  private final LabelNormalizer labelNormalizer;
  private final Provider<CurrentUser> userProvider;
  private final AnonymousUser anonymous;
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
  private final EnumSet<ListChangesOption> options;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ConsistencyChecker> checkerProvider;
  private final ActionJson actionJson;
  private final GpgApiAdapter gpgApi;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeIndexCollection indexes;

  private boolean lazyLoad = true;
  private AccountLoader accountLoader;
  private FixInput fix;

  @AssistedInject
  ChangeJson(
      Provider<ReviewDb> db,
      LabelNormalizer ln,
      Provider<CurrentUser> user,
      AnonymousUser au,
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
      @Assisted Set<ListChangesOption> options) {
    this.db = db;
    this.labelNormalizer = ln;
    this.userProvider = user;
    this.anonymous = au;
    this.changeDataFactory = cdf;
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
    this.options =
        options.isEmpty() ? EnumSet.noneOf(ListChangesOption.class) : EnumSet.copyOf(options);
  }

  public ChangeJson lazyLoad(boolean load) {
    lazyLoad = load;
    return this;
  }

  public ChangeJson fix(FixInput fix) {
    this.fix = fix;
    return this;
  }

  public ChangeInfo format(ChangeResource rsrc) throws OrmException {
    return format(changeDataFactory.create(db.get(), rsrc.getControl()));
  }

  public ChangeInfo format(Change change) throws OrmException {
    return format(changeDataFactory.create(db.get(), change));
  }

  public ChangeInfo format(Project.NameKey project, Change.Id id)
      throws OrmException, NoSuchChangeException {
    ChangeNotes notes;
    try {
      notes = notesFactory.createChecked(db.get(), project, id);
    } catch (OrmException | NoSuchChangeException e) {
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
        | RuntimeException e) {
      if (!has(CHECK)) {
        Throwables.throwIfInstanceOf(e, OrmException.class);
        throw new OrmException(e);
      }
      return checkOnly(cd);
    }
  }

  public ChangeInfo format(RevisionResource rsrc) throws OrmException {
    ChangeData cd = changeDataFactory.create(db.get(), rsrc.getControl());
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
            | RuntimeException e) {
          if (has(CHECK)) {
            i = checkOnly(cd);
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
    ChangeControl ctl;
    try {
      ctl = cd.changeControl().forUser(userProvider.get());
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

    ConsistencyChecker.Result result = checkerProvider.get().check(ctl, fix);
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
      finish(info);
    } else {
      info = new ChangeInfo();
      info._number = result.id().get();
      info.problems = result.problems();
    }
    return info;
  }

  private ChangeInfo toChangeInfo(ChangeData cd, Optional<PatchSet.Id> limitToPsId)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException {
    ChangeInfo out = new ChangeInfo();
    CurrentUser user = userProvider.get();
    ChangeControl ctl = cd.changeControl().forUser(user);

    if (has(CHECK)) {
      out.problems = checkerProvider.get().check(ctl, fix).problems();
      // If any problems were fixed, the ChangeData needs to be reloaded.
      for (ProblemInfo p : out.problems) {
        if (p.status == ProblemInfo.Status.FIXED) {
          cd = changeDataFactory.create(cd.db(), cd.project(), cd.getId());
          break;
        }
      }
    }

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
    out.subject = in.getSubject();
    out.status = in.getStatus().asChangeStatus();
    out.owner = accountLoader.get(in.getOwner());
    out.created = in.getCreatedOn();
    out.updated = in.getLastUpdatedOn();
    out._number = in.getId().get();

    if (user.isIdentifiedUser()) {
      Collection<String> stars = cd.stars().get(user.getAccountId());
      out.starred = stars.contains(StarredChangesUtil.DEFAULT_LABEL) ? true : null;
      if (!stars.isEmpty()) {
        out.stars = stars;
      }
    }

    if (in.getStatus().isOpen() && has(REVIEWED) && user.isIdentifiedUser()) {
      Account.Id accountId = user.getAccountId();
      out.reviewed = cd.reviewedBy().contains(accountId) ? true : null;
    }

    out.labels = labelsFor(ctl, cd, has(LABELS), has(DETAILED_LABELS));
    out.submitted = getSubmittedOn(cd);

    if (out.labels != null && has(DETAILED_LABELS)) {
      // If limited to specific patch sets but not the current patch set, don't
      // list permitted labels, since users can't vote on those patch sets.
      if (!limitToPsId.isPresent() || limitToPsId.get().equals(in.currentPatchSetId())) {
        out.permittedLabels =
            cd.change().getStatus() != Change.Status.ABANDONED
                ? permittedLabels(ctl, cd)
                : ImmutableMap.of();
      }
      out.removableReviewers = removableReviewers(ctl, out.labels.values());

      out.reviewers = new HashMap<>();
      for (Map.Entry<ReviewerStateInternal, Map<Account.Id, Timestamp>> e :
          cd.reviewers().asTable().rowMap().entrySet()) {
        out.reviewers.put(e.getKey().asReviewerState(), toAccountInfo(e.getValue().keySet()));
      }
    }

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
      out.messages = messages(ctl, cd, src);
    }
    finish(out);

    if (needRevisions) {
      out.revisions = revisions(ctl, cd, src);
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
      actionJson.addChangeActions(out, ctl);
    }

    return out;
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
      ChangeControl ctl, ChangeData cd, boolean standard, boolean detailed) throws OrmException {
    if (!standard && !detailed) {
      return null;
    }

    if (ctl == null) {
      return null;
    }

    LabelTypes labelTypes = ctl.getLabelTypes();
    Map<String, LabelWithStatus> withStatus =
        cd.change().getStatus().isOpen()
            ? labelsForOpenChange(ctl, cd, labelTypes, standard, detailed)
            : labelsForClosedChange(cd, labelTypes, standard, detailed);
    return ImmutableMap.copyOf(Maps.transformValues(withStatus, LabelWithStatus::label));
  }

  private Map<String, LabelWithStatus> labelsForOpenChange(
      ChangeControl ctl, ChangeData cd, LabelTypes labelTypes, boolean standard, boolean detailed)
      throws OrmException {
    Map<String, LabelWithStatus> labels = initLabels(cd, labelTypes, standard);
    if (detailed) {
      setAllApprovals(ctl, cd, labels);
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
    // Don't use Maps.newTreeMap(Comparator) due to OpenJDK bug 100167.
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
      ChangeControl baseCtrl, ChangeData cd, Map<String, LabelWithStatus> labels)
      throws OrmException {
    Change.Status status = cd.change().getStatus();
    checkState(status.isOpen(), "should not call setAllApprovals on %s change", status);

    // Include a user in the output for this label if either:
    //  - They are an explicit reviewer.
    //  - They ever voted on this change.
    Set<Account.Id> allUsers = new HashSet<>();
    allUsers.addAll(cd.reviewers().byState(ReviewerStateInternal.REVIEWER));
    for (PatchSetApproval psa : cd.approvals().values()) {
      allUsers.add(psa.getAccountId());
    }

    Table<Account.Id, String, PatchSetApproval> current =
        HashBasedTable.create(allUsers.size(), baseCtrl.getLabelTypes().getLabelTypes().size());
    for (PatchSetApproval psa : cd.currentApprovals()) {
      current.put(psa.getAccountId(), psa.getLabel(), psa);
    }

    for (Account.Id accountId : allUsers) {
      IdentifiedUser user = userFactory.create(accountId);
      ChangeControl ctl = baseCtrl.forUser(user);
      for (Map.Entry<String, LabelWithStatus> e : labels.entrySet()) {
        LabelType lt = ctl.getLabelTypes().byLabel(e.getKey());
        if (lt == null) {
          // Ignore submit record for undefined label; likely the submit rule
          // author didn't intend for the label to show up in the table.
          continue;
        }
        Integer value;
        String tag = null;
        Timestamp date = null;
        PatchSetApproval psa = current.get(accountId, lt.getName());
        if (psa != null) {
          value = Integer.valueOf(psa.getValue());
          if (value == 0) {
            // This may be a dummy approval that was inserted when the reviewer
            // was added. Explicitly check whether the user can vote on this
            // label.
            value = labelNormalizer.canVote(ctl, lt, accountId) ? 0 : null;
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
          value = labelNormalizer.canVote(ctl, lt, accountId) ? 0 : null;
        }
        addApproval(e.getValue().label(), approvalInfo(accountId, value, tag, date));
      }
    }
  }

  private Timestamp getSubmittedOn(ChangeData cd) throws OrmException {
    Optional<PatchSetApproval> s = cd.getSubmitApproval();
    return s.isPresent() ? s.get().getGranted() : null;
  }

  private Map<String, LabelWithStatus> labelsForClosedChange(
      ChangeData cd, LabelTypes labelTypes, boolean standard, boolean detailed)
      throws OrmException {
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
    Multimap<Account.Id, PatchSetApproval> current = HashMultimap.create();
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

    Map<String, LabelWithStatus> labels;
    if (cd.change().getStatus() == Change.Status.MERGED) {
      // Since voting on merged changes is allowed all labels which apply to
      // the change must be returned. All applying labels can be retrieved from
      // the submit records, which is what initLabels does.
      // It's not possible to only compute the labels based on the approvals
      // since merged changes may not have approvals for all labels (e.g. if not
      // all labels are required for submit or if the change was auto-closed due
      // to direct push or if new labels were defined after the change was
      // merged).
      labels = initLabels(cd, labelTypes, standard);

      // Also include all labels for which approvals exists. E.g. there can be
      // approvals for labels that are ignored by a Prolog submit rule and hence
      // it wouldn't be included in the submit records.
      for (String name : labelNames) {
        if (!labels.containsKey(name)) {
          labels.put(name, LabelWithStatus.create(new LabelInfo(), null));
        }
      }
    } else {
      // For abandoned changes return only labels for which approvals exist.
      // Other labels are not needed since voting on abandoned changes is not
      // allowed.
      labels = new TreeMap<>(labelTypes.nameComparator());
      for (String name : labelNames) {
        labels.put(name, LabelWithStatus.create(new LabelInfo(), null));
      }
    }

    if (detailed) {
      labels
          .entrySet()
          .stream()
          .forEach(e -> setLabelValues(labelTypes.byLabel(e.getKey()), e.getValue()));
    }

    for (Account.Id accountId : allUsers) {
      Map<String, ApprovalInfo> byLabel = Maps.newHashMapWithExpectedSize(labels.size());

      if (detailed) {
        for (Map.Entry<String, LabelWithStatus> entry : labels.entrySet()) {
          ApprovalInfo ai = approvalInfo(accountId, 0, null, null);
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

  private ApprovalInfo approvalInfo(Account.Id id, Integer value, String tag, Timestamp date) {
    ApprovalInfo ai = getApprovalInfo(id, value, tag, date);
    accountLoader.put(ai);
    return ai;
  }

  public static ApprovalInfo getApprovalInfo(
      Account.Id id, Integer value, String tag, Timestamp date) {
    ApprovalInfo ai = new ApprovalInfo(id.get());
    ai.value = value;
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

  private Map<String, Collection<String>> permittedLabels(ChangeControl ctl, ChangeData cd)
      throws OrmException {
    if (ctl == null) {
      return null;
    }

    LabelTypes labelTypes = ctl.getLabelTypes();
    SetMultimap<String, String> permitted = LinkedHashMultimap.create();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelType type = labelTypes.byLabel(r.label);
        if (type == null) {
          continue;
        }
        PermissionRange range = ctl.getRange(Permission.forLabel(r.label));
        for (LabelValue v : type.getValues()) {
          if (range.contains(v.getValue())) {
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

  private Collection<ChangeMessageInfo> messages(
      ChangeControl ctl, ChangeData cd, Map<PatchSet.Id, PatchSet> map) throws OrmException {
    List<ChangeMessage> messages = cmUtil.byChange(db.get(), cd.notes());
    if (messages.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeMessageInfo> result = Lists.newArrayListWithCapacity(messages.size());
    for (ChangeMessage message : messages) {
      PatchSet.Id patchNum = message.getPatchSetId();
      PatchSet ps = patchNum != null ? map.get(patchNum) : null;
      if (patchNum == null || ctl.isPatchVisible(ps, db.get())) {
        ChangeMessageInfo cmi = new ChangeMessageInfo();
        cmi.id = message.getKey().get();
        cmi.author = accountLoader.get(message.getAuthor());
        cmi.date = message.getWrittenOn();
        cmi.message = message.getMessage();
        cmi.tag = message.getTag();
        cmi._revisionNumber = patchNum != null ? patchNum.get() : null;
        result.add(cmi);
      }
    }
    return result;
  }

  private Collection<AccountInfo> removableReviewers(
      ChangeControl ctl, Collection<LabelInfo> labels) {
    Set<Account.Id> fixed = Sets.newHashSetWithExpectedSize(labels.size());
    Set<Account.Id> removable = Sets.newHashSetWithExpectedSize(labels.size());
    for (LabelInfo label : labels) {
      if (label.all == null) {
        continue;
      }
      for (ApprovalInfo ai : label.all) {
        Account.Id id = new Account.Id(ai._accountId);
        if (ctl.canRemoveReviewer(id, MoreObjects.firstNonNull(ai.value, 0))) {
          removable.add(id);
        } else {
          fixed.add(id);
        }
      }
    }
    removable.removeAll(fixed);

    List<AccountInfo> result = Lists.newArrayListWithCapacity(removable.size());
    for (Account.Id id : removable) {
      result.add(accountLoader.get(id));
    }
    return result;
  }

  private Collection<AccountInfo> toAccountInfo(Collection<Account.Id> accounts) {
    return accounts
        .stream()
        .map(accountLoader::get)
        .sorted(AccountInfoComparator.ORDER_NULLS_FIRST)
        .collect(toList());
  }

  @Nullable
  private Repository openRepoIfNecessary(ChangeControl ctl) throws IOException {
    if (has(ALL_COMMITS) || has(CURRENT_COMMIT) || has(COMMIT_FOOTERS)) {
      return repoManager.openRepository(ctl.getProject().getNameKey());
    }
    return null;
  }

  private Map<String, RevisionInfo> revisions(
      ChangeControl ctl, ChangeData cd, Map<PatchSet.Id, PatchSet> map)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException {
    Map<String, RevisionInfo> res = new LinkedHashMap<>();
    try (Repository repo = openRepoIfNecessary(ctl)) {
      for (PatchSet in : map.values()) {
        if ((has(ALL_REVISIONS) || in.getId().equals(ctl.getChange().currentPatchSetId()))
            && ctl.isPatchVisible(in, db.get())) {
          res.put(in.getRevision().get(), toRevisionInfo(ctl, cd, in, repo, false));
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

  public RevisionInfo getRevisionInfo(ChangeControl ctl, PatchSet in)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    try (Repository repo = openRepoIfNecessary(ctl)) {
      RevisionInfo rev =
          toRevisionInfo(ctl, changeDataFactory.create(db.get(), ctl), in, repo, true);
      accountLoader.fill();
      return rev;
    }
  }

  private RevisionInfo toRevisionInfo(
      ChangeControl ctl, ChangeData cd, PatchSet in, @Nullable Repository repo, boolean fillCommit)
      throws PatchListNotAvailableException, GpgException, OrmException, IOException {
    Change c = ctl.getChange();
    RevisionInfo out = new RevisionInfo();
    out.isCurrent = in.getId().equals(c.currentPatchSetId());
    out._number = in.getId().get();
    out.ref = in.getRefName();
    out.created = in.getCreatedOn();
    out.uploader = accountLoader.get(in.getUploader());
    out.draft = in.isDraft() ? true : null;
    out.fetch = makeFetchMap(ctl, in);
    out.kind = changeKindCache.getChangeKind(repo, cd, in);

    boolean setCommit = has(ALL_COMMITS) || (out.isCurrent && has(CURRENT_COMMIT));
    boolean addFooters = out.isCurrent && has(COMMIT_FOOTERS);
    if (setCommit || addFooters) {
      Project.NameKey project = c.getProject();
      try (RevWalk rw = new RevWalk(repo)) {
        String rev = in.getRevision().get();
        RevCommit commit = rw.parseCommit(ObjectId.fromString(rev));
        rw.parseBody(commit);
        if (setCommit) {
          out.commit = toCommit(ctl, rw, commit, has(WEB_LINKS), fillCommit);
        }
        if (addFooters) {
          out.commitWithFooters =
              mergeUtilFactory
                  .create(projectCache.get(project))
                  .createCherryPickCommitMessage(commit, ctl, in.getId());
        }
      }
    }

    if (has(ALL_FILES) || (out.isCurrent && has(CURRENT_FILES))) {
      out.files = fileInfoJson.toFileInfoMap(c, in);
      out.files.remove(Patch.COMMIT_MSG);
      out.files.remove(Patch.MERGE_LIST);
    }

    if ((out.isCurrent || (out.draft != null && out.draft))
        && has(CURRENT_ACTIONS)
        && userProvider.get().isIdentifiedUser()) {

      actionJson.addRevisionActions(
          out, new RevisionResource(changeResourceFactory.create(ctl), in));
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
      ChangeControl ctl, RevWalk rw, RevCommit commit, boolean addLinks, boolean fillCommit)
      throws IOException {
    Project.NameKey project = ctl.getProject().getNameKey();
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
      FluentIterable<WebLinkInfo> links = webLinks.getPatchSetLinks(project, commit.name());
      info.webLinks = links.isEmpty() ? null : links.toList();
    }

    for (RevCommit parent : commit.getParents()) {
      rw.parseBody(parent);
      CommitInfo i = new CommitInfo();
      i.commit = parent.name();
      i.subject = parent.getShortMessage();
      info.parents.add(i);
    }
    return info;
  }

  private Map<String, FetchInfo> makeFetchMap(ChangeControl ctl, PatchSet in) throws OrmException {
    Map<String, FetchInfo> r = new LinkedHashMap<>();

    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      String schemeName = e.getExportName();
      DownloadScheme scheme = e.getProvider().get();
      if (!scheme.isEnabled()
          || (scheme.isAuthRequired() && !userProvider.get().isIdentifiedUser())) {
        continue;
      }

      if (!scheme.isAuthSupported() && !ctl.forUser(anonymous).isPatchVisible(in, db.get())) {
        continue;
      }

      String projectName = ctl.getProject().getNameKey().get();
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
