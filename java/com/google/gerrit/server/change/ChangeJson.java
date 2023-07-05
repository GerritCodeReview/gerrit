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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_COMMITS;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CHANGE_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CHECK;
import static com.google.gerrit.extensions.client.ListChangesOption.COMMIT_FOOTERS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.CUSTOM_KEYED_VALUES;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_ACCOUNTS;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWED;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWER_UPDATES;
import static com.google.gerrit.extensions.client.ListChangesOption.SKIP_DIFFSTAT;
import static com.google.gerrit.extensions.client.ListChangesOption.STAR;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMIT_REQUIREMENTS;
import static com.google.gerrit.extensions.client.ListChangesOption.TRACKING_IDS;
import static com.google.gerrit.server.ChangeMessagesUtil.createChangeMessageInfo;
import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;
import static com.google.gerrit.server.util.AttentionSetUtil.removalsOnly;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.LegacySubmitRequirementInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.extensions.common.ReviewerUpdateInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.TrackingIdInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountInfoComparator;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Produces {@link ChangeInfo} (which is serialized to JSON afterwards) from {@link ChangeData}.
 *
 * <p>This is intended to be used on request scope, but may be used for converting multiple {@link
 * ChangeData} objects from different sources.
 */
public class ChangeJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      ChangeField.SUBMIT_RULE_OPTIONS_LENIENT.toBuilder().build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      ChangeField.SUBMIT_RULE_OPTIONS_STRICT.toBuilder().build();

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
      return factory.create(options, Optional.empty());
    }

    public ChangeJson create(
        Iterable<ListChangesOption> options, PluginDefinedInfosFactory pluginDefinedInfosFactory) {
      return factory.create(options, Optional.of(pluginDefinedInfosFactory));
    }

    public ChangeJson create(ListChangesOption first, ListChangesOption... rest) {
      return create(Sets.immutableEnumSet(first, rest));
    }
  }

  public interface AssistedFactory {
    ChangeJson create(
        Iterable<ListChangesOption> options,
        Optional<PluginDefinedInfosFactory> pluginDefinedInfosFactory);
  }

  @Singleton
  private static class Metrics {
    private final Timer0 toChangeInfoLatency;
    private final Timer0 toChangeInfosLatency;
    private final Timer0 formatQueryResultsLatency;

    @Inject
    Metrics(MetricMaker metricMaker) {
      toChangeInfoLatency =
          metricMaker.newTimer(
              "http/server/rest_api/change_json/to_change_info_latency",
              new Description("Latency for toChangeInfo invocations in ChangeJson")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      toChangeInfosLatency =
          metricMaker.newTimer(
              "http/server/rest_api/change_json/to_change_infos_latency",
              new Description("Latency for toChangeInfos invocations in ChangeJson")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      formatQueryResultsLatency =
          metricMaker.newTimer(
              "http/server/rest_api/change_json/format_query_results_latency",
              new Description("Latency for formatQueryResults invocations in ChangeJson")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final ChangeData.Factory changeDataFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final ImmutableSet<ListChangesOption> options;
  private final ChangeMessagesUtil cmUtil;
  private final StarredChangesUtil starredChangesUtil;
  private final Provider<ConsistencyChecker> checkerProvider;
  private final ActionJson actionJson;
  private final ChangeNotes.Factory notesFactory;
  private final LabelsJson labelsJson;
  private final RemoveReviewerControl removeReviewerControl;
  private final TrackingFooters trackingFooters;
  private final Metrics metrics;
  private final RevisionJson revisionJson;
  private final Optional<PluginDefinedInfosFactory> pluginDefinedInfosFactory;
  private final boolean includeMergeable;
  private final boolean lazyLoad;
  private final boolean cacheQueryResultsByChangeNum;

  private AccountLoader accountLoader;
  private FixInput fix;
  private ExperimentFeatures experimentFeatures;

  @Inject
  ChangeJson(
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ExperimentFeatures experimentFeatures,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ChangeData.Factory cdf,
      AccountLoader.Factory ailf,
      ChangeMessagesUtil cmUtil,
      StarredChangesUtil starredChangesUtil,
      Provider<ConsistencyChecker> checkerProvider,
      ActionJson actionJson,
      ChangeNotes.Factory notesFactory,
      LabelsJson labelsJson,
      RemoveReviewerControl removeReviewerControl,
      TrackingFooters trackingFooters,
      Metrics metrics,
      RevisionJson.Factory revisionJsonFactory,
      @GerritServerConfig Config cfg,
      @Assisted Iterable<ListChangesOption> options,
      @Assisted Optional<PluginDefinedInfosFactory> pluginDefinedInfosFactory) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.experimentFeatures = experimentFeatures;
    this.userProvider = user;
    this.changeDataFactory = cdf;
    this.permissionBackend = permissionBackend;
    this.accountLoaderFactory = ailf;
    this.cmUtil = cmUtil;
    this.starredChangesUtil = starredChangesUtil;
    this.checkerProvider = checkerProvider;
    this.actionJson = actionJson;
    this.notesFactory = notesFactory;
    this.labelsJson = labelsJson;
    this.removeReviewerControl = removeReviewerControl;
    this.trackingFooters = trackingFooters;
    this.metrics = metrics;
    this.revisionJson = revisionJsonFactory.create(options);
    this.options = Sets.immutableEnumSet(options);
    this.includeMergeable = MergeabilityComputationBehavior.fromConfig(cfg).includeInApi();
    this.lazyLoad = containsAnyOf(this.options, REQUIRE_LAZY_LOAD);
    this.pluginDefinedInfosFactory = pluginDefinedInfosFactory;
    this.cacheQueryResultsByChangeNum =
        cfg.getBoolean("index", "cacheQueryResultsByChangeNum", true);

    logger.atFine().log("options = %s", options);
  }

  public ChangeJson fix(FixInput fix) {
    this.fix = fix;
    return this;
  }

  public ChangeInfo format(ChangeResource rsrc) {
    return format(changeDataFactory.create(rsrc.getNotes()));
  }

  public ChangeInfo format(Change change) {
    return format(changeDataFactory.create(change));
  }

  public ChangeInfo format(Change change, @Nullable ObjectId metaRevId) {
    ChangeNotes notes = notesFactory.createChecked(change.getProject(), change.getId(), metaRevId);
    return format(changeDataFactory.create(notes));
  }

  public ChangeInfo format(ChangeData cd) {
    return format(cd, Optional.empty(), true, getPluginInfos(cd));
  }

  public ChangeInfo format(RevisionResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return format(cd, Optional.of(rsrc.getPatchSet().id()), true, getPluginInfos(cd));
  }

  public List<List<ChangeInfo>> format(List<QueryResult<ChangeData>> in)
      throws PermissionBackendException {
    try (Timer0.Context ignored = metrics.formatQueryResultsLatency.start()) {
      accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
      List<List<ChangeInfo>> res = new ArrayList<>(in.size());
      Map<Change.Id, ChangeInfo> cache = Maps.newHashMapWithExpectedSize(in.size());
      ImmutableListMultimap<Change.Id, PluginDefinedInfo> pluginInfosByChange =
          getPluginInfos(in.stream().flatMap(e -> e.entities().stream()).collect(toList()));
      for (QueryResult<ChangeData> r : in) {
        List<ChangeInfo> infos = toChangeInfos(r.entities(), cache, pluginInfosByChange);
        if (!infos.isEmpty() && r.more()) {
          infos.get(infos.size() - 1)._moreChanges = true;
        }
        res.add(infos);
      }
      accountLoader.fill();
      return res;
    }
  }

  public List<ChangeInfo> format(Collection<ChangeData> in) throws PermissionBackendException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    ensureLoaded(in);
    List<ChangeInfo> out = new ArrayList<>(in.size());
    ImmutableListMultimap<Change.Id, PluginDefinedInfo> pluginInfosByChange = getPluginInfos(in);
    for (ChangeData cd : in) {
      out.add(format(cd, Optional.empty(), false, pluginInfosByChange.get(cd.getId())));
    }
    accountLoader.fill();
    return out;
  }

  public ChangeInfo format(Project.NameKey project, Change.Id id) {
    return format(project, id, null);
  }

  public ChangeInfo format(Project.NameKey project, Change.Id id, @Nullable ObjectId metaRevId) {
    ChangeNotes notes;
    try {
      notes = notesFactory.createChecked(project, id, metaRevId);
    } catch (StorageException e) {
      if (!has(CHECK)) {
        throw e;
      }
      return checkOnly(changeDataFactory.create(project, id));
    }
    ChangeData cd = changeDataFactory.create(notes);
    return format(cd, Optional.empty(), true, getPluginInfos(cd));
  }

  private static Collection<LegacySubmitRequirementInfo> requirementsFor(ChangeData cd) {
    Collection<LegacySubmitRequirementInfo> reqInfos = new ArrayList<>();
    for (SubmitRecord submitRecord : cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT)) {
      if (submitRecord.requirements == null) {
        continue;
      }
      for (LegacySubmitRequirement requirement : submitRecord.requirements) {
        reqInfos.add(requirementToInfo(requirement, submitRecord.status));
      }
    }
    return reqInfos;
  }

  private Collection<SubmitRecordInfo> submitRecordsFor(ChangeData cd) {
    List<SubmitRecordInfo> submitRecordInfos = new ArrayList<>();
    for (SubmitRecord record : cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT)) {
      submitRecordInfos.add(submitRecordToInfo(record));
    }
    return submitRecordInfos;
  }

  private Collection<SubmitRequirementResultInfo> submitRequirementsFor(ChangeData cd) {
    Collection<SubmitRequirementResultInfo> reqInfos = new ArrayList<>();
    cd.submitRequirementsIncludingLegacy().entrySet().stream()
        .filter(entry -> !entry.getValue().isHidden())
        .forEach(
            entry -> reqInfos.add(SubmitRequirementsJson.toInfo(entry.getKey(), entry.getValue())));
    return reqInfos;
  }

  private static LegacySubmitRequirementInfo requirementToInfo(
      LegacySubmitRequirement req, Status status) {
    return new LegacySubmitRequirementInfo(status.name(), req.fallbackText(), req.type());
  }

  private SubmitRecordInfo submitRecordToInfo(SubmitRecord record) {
    SubmitRecordInfo info = new SubmitRecordInfo();
    if (record.status != null) {
      info.status = SubmitRecordInfo.Status.valueOf(record.status.name());
    }
    info.ruleName = record.ruleName;
    info.errorMessage = record.errorMessage;
    if (record.labels != null) {
      info.labels = new ArrayList<>();
      for (SubmitRecord.Label label : record.labels) {
        SubmitRecordInfo.Label labelInfo = new SubmitRecordInfo.Label();
        labelInfo.label = label.label;
        if (label.status != null) {
          labelInfo.status = SubmitRecordInfo.Label.Status.valueOf(label.status.name());
        }
        labelInfo.appliedBy = accountLoader.get(label.appliedBy);
        info.labels.add(labelInfo);
      }
    }
    if (record.requirements != null) {
      info.requirements = new ArrayList<>();
      for (LegacySubmitRequirement requirement : record.requirements) {
        info.requirements.add(requirementToInfo(requirement, record.status));
      }
    }
    return info;
  }

  private static void finish(ChangeInfo info, ExperimentFeatures experimentFeatures) {
    info.tripletId =
        Joiner.on('~')
            .join(Url.encode(info.project), Url.encode(info.branch), Url.encode(info.changeId));
    if (experimentFeatures.isFeatureEnabled(
        ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_RETURN_NEW_CHANGE_INFO_ID,
        Project.nameKey(info.project))) {
      info.id =
          Joiner.on('~').join(Url.encode(info.project), Url.encode(String.valueOf(info._number)));
    } else {
      info.id = info.tripletId;
    }
  }

  private static boolean containsAnyOf(
      ImmutableSet<ListChangesOption> set, ImmutableSet<ListChangesOption> toFind) {
    return !Sets.intersection(toFind, set).isEmpty();
  }

  private ChangeInfo format(
      ChangeData cd,
      Optional<PatchSet.Id> limitToPsId,
      boolean fillAccountLoader,
      List<PluginDefinedInfo> pluginInfosForChange) {
    try {
      if (fillAccountLoader) {
        accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
        ChangeInfo res = toChangeInfo(cd, limitToPsId, pluginInfosForChange);
        accountLoader.fill();
        return res;
      }
      return toChangeInfo(cd, limitToPsId, pluginInfosForChange);
    } catch (PatchListNotAvailableException
        | GpgException
        | IOException
        | PermissionBackendException
        | RuntimeException e) {
      if (!has(CHECK)) {
        Throwables.throwIfInstanceOf(e, StorageException.class);
        throw new StorageException(e);
      }
      return checkOnly(cd);
    }
  }

  private void ensureLoaded(Iterable<ChangeData> all) {
    if (lazyLoad) {
      for (ChangeData cd : all) {
        // Mark all ChangeDatas as coming from the index, but allow backfilling data from NoteDb
        cd.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
      }
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
        // Mark all ChangeDatas as coming from the index. Disallow using NoteDb
        cd.setStorageConstraint(ChangeData.StorageConstraint.INDEX_ONLY);
      }
    }
  }

  private boolean has(ListChangesOption option) {
    return options.contains(option);
  }

  private List<ChangeInfo> toChangeInfos(
      List<ChangeData> changes,
      Map<Change.Id, ChangeInfo> cache,
      ImmutableListMultimap<Change.Id, PluginDefinedInfo> pluginInfosByChange) {
    try (Timer0.Context ignored = metrics.toChangeInfosLatency.start()) {
      List<ChangeInfo> changeInfos = new ArrayList<>(changes.size());
      for (int i = 0; i < changes.size(); i++) {
        // We can only cache and re-use an entity if it's not the last in the list. The last entity
        // may later get _moreChanges set. If it was cached or re-used, that setting would propagate
        // to the original entity yielding wrong results.
        // This problem has two sides where 'last in the list' has to be respected:
        // (1) Caching
        // (2) Reusing
        boolean isCacheable = cacheQueryResultsByChangeNum && (i != changes.size() - 1);
        ChangeData cd = changes.get(i);
        ChangeInfo info = cache.get(cd.getId());
        if (info != null && isCacheable) {
          changeInfos.add(info);
          continue;
        }

        // Compute and cache if possible
        try {
          ensureLoaded(Collections.singleton(cd));
          info = format(cd, Optional.empty(), false, pluginInfosByChange.get(cd.getId()));
          changeInfos.add(info);
          if (isCacheable) {
            cache.put(Change.id(info._number), info);
          }
        } catch (RuntimeException e) {
          Optional<RequestCancelledException> requestCancelledException =
              RequestCancelledException.getFromCausalChain(e);
          if (requestCancelledException.isPresent()) {
            throw e;
          }
          logger.atWarning().withCause(e).log(
              "Omitting corrupt change %s from results", cd.getId());
        }
      }
      if (has(STAR) && userProvider.get().isIdentifiedUser()) {
        populateStarField(changeInfos);
      }
      return changeInfos;
    }
  }

  private ChangeInfo checkOnly(ChangeData cd) {
    ChangeNotes notes;
    try {
      notes = cd.notes();
    } catch (StorageException e) {
      String msg = "Error loading change";
      logger.atWarning().withCause(e).log(msg + " %s", cd.getId());
      ChangeInfo info = new ChangeInfo();
      info._number = cd.getId().get();
      ProblemInfo p = new ProblemInfo();
      p.message = msg;
      info.problems = Lists.newArrayList(p);
      return info;
    }

    ConsistencyChecker.Result result = checkerProvider.get().check(notes, fix);
    ChangeInfo info = new ChangeInfo();
    Change c = result.change();
    if (c != null) {
      info.project = c.getProject().get();
      info.branch = c.getDest().shortName();
      info.topic = c.getTopic();
      info.changeId = c.getKey().get();
      info.subject = c.getSubject();
      info.status = c.getStatus().asChangeStatus();
      info.owner = new AccountInfo(c.getOwner().get());
      info.setCreated(c.getCreatedOn());
      info.setUpdated(c.getLastUpdatedOn());
      info._number = c.getId().get();
      info.problems = result.problems();
      info.isPrivate = c.isPrivate() ? true : null;
      info.workInProgress = c.isWorkInProgress() ? true : null;
      info.hasReviewStarted = c.hasReviewStarted();
      finish(info, experimentFeatures);
    } else {
      info._number = result.id().get();
      info.problems = result.problems();
    }
    return info;
  }

  private ChangeInfo toChangeInfo(
      ChangeData cd,
      Optional<PatchSet.Id> limitToPsId,
      List<PluginDefinedInfo> pluginInfosForChange)
      throws PatchListNotAvailableException, GpgException, PermissionBackendException, IOException {
    try (Timer0.Context ignored = metrics.toChangeInfoLatency.start()) {
      return toChangeInfoImpl(cd, limitToPsId, pluginInfosForChange);
    }
  }

  private ChangeInfo toChangeInfoImpl(
      ChangeData cd, Optional<PatchSet.Id> limitToPsId, List<PluginDefinedInfo> pluginInfos)
      throws PatchListNotAvailableException, GpgException, PermissionBackendException, IOException {
    ChangeInfo out = new ChangeInfo();
    CurrentUser user = userProvider.get();

    if (has(CHECK)) {
      out.problems = checkerProvider.get().check(cd.notes(), fix).problems();
      // If any problems were fixed, the ChangeData needs to be reloaded.
      for (ProblemInfo p : out.problems) {
        if (p.status == ProblemInfo.Status.FIXED) {
          cd = changeDataFactory.create(cd.project(), cd.getId());
          break;
        }
      }
    }

    Change in = cd.change();
    out.project = in.getProject().get();
    out.branch = in.getDest().shortName();
    out.topic = in.getTopic();
    if (!cd.attentionSet().isEmpty()) {
      out.removedFromAttentionSet =
          removalsOnly(cd.attentionSet()).stream()
              .collect(
                  toImmutableMap(
                      a -> a.account().get(),
                      a -> AttentionSetUtil.createAttentionSetInfo(a, accountLoader)));
      out.attentionSet =
          // This filtering should match GetAttentionSet.
          additionsOnly(cd.attentionSet()).stream()
              .collect(
                  toImmutableMap(
                      a -> a.account().get(),
                      a -> AttentionSetUtil.createAttentionSetInfo(a, accountLoader)));
    }
    if (has(CUSTOM_KEYED_VALUES)) {
      out.customKeyedValues = cd.customKeyedValues();
    }
    out.hashtags = cd.hashtags();
    out.changeId = in.getKey().get();
    if (in.isNew()) {
      SubmitTypeRecord str = cd.submitTypeRecord();
      if (str.isOk()) {
        out.submitType = str.type;
      }
      if (includeMergeable) {
        out.mergeable = cd.isMergeable();
      }
      if (has(SUBMITTABLE)) {
        out.submittable = submittable(cd);
      }
    }
    if (!has(SKIP_DIFFSTAT)) {
      Optional<ChangedLines> changedLines = cd.changedLines();
      if (changedLines.isPresent()) {
        out.insertions = changedLines.get().insertions;
        out.deletions = changedLines.get().deletions;
      }
    }
    out.isPrivate = in.isPrivate() ? true : null;
    out.workInProgress = in.isWorkInProgress() ? true : null;
    out.hasReviewStarted = in.hasReviewStarted();
    out.subject = in.getSubject();
    out.status = in.getStatus().asChangeStatus();
    out.owner = accountLoader.get(in.getOwner());
    out.setCreated(in.getCreatedOn());
    out.setUpdated(in.getLastUpdatedOn());
    out._number = in.getId().get();
    out.totalCommentCount = cd.totalCommentCount();
    out.unresolvedCommentCount = cd.unresolvedCommentCount();

    if (cd.getRefStates() != null) {
      String metaName = RefNames.changeMetaRef(cd.getId());
      Optional<RefState> metaState =
          cd.getRefStates().values().stream().filter(r -> r.ref().equals(metaName)).findAny();

      // metaState should always be there, but it doesn't hurt to be extra careful.
      metaState.ifPresent(rs -> out.metaRevId = rs.id().getName());
    }

    if (user.isIdentifiedUser()) {
      if (cd.isStarred(user.getAccountId())) {
        out.starred = true;
      }
    }

    if (in.isNew() && has(REVIEWED) && user.isIdentifiedUser()) {
      out.reviewed = cd.isReviewedBy(user.getAccountId()) ? true : null;
    }

    out.labels = labelsJson.labelsFor(accountLoader, cd, has(LABELS), has(DETAILED_LABELS));
    out.requirements = requirementsFor(cd);
    out.submitRecords = submitRecordsFor(cd);
    if (has(SUBMIT_REQUIREMENTS)) {
      out.submitRequirements = submitRequirementsFor(cd);
    }

    if (out.labels != null && has(DETAILED_LABELS)) {
      // If limited to specific patch sets but not the current patch set, don't
      // list permitted labels, since users can't vote on those patch sets.
      if (user.isIdentifiedUser()
          && (!limitToPsId.isPresent() || limitToPsId.get().equals(in.currentPatchSetId()))) {
        out.permittedLabels =
            !cd.change().isAbandoned()
                ? labelsJson.permittedLabels(user.getAccountId(), cd)
                : ImmutableMap.of();
        out.removableLabels = labelsJson.removableLabels(accountLoader, user, cd);
      }
    }

    if (has(LABELS) || has(DETAILED_LABELS)) {
      out.reviewers = reviewerMap(cd.reviewers(), cd.reviewersByEmail(), false);
      out.pendingReviewers = reviewerMap(cd.pendingReviewers(), cd.pendingReviewersByEmail(), true);
      out.removableReviewers = removableReviewers(cd, out);
    }

    setSubmitter(cd, out);

    if (!pluginInfos.isEmpty()) {
      out.plugins = pluginInfos;
    }
    out.revertOf = cd.change().getRevertOf() != null ? cd.change().getRevertOf().get() : null;
    out.submissionId = cd.change().getSubmissionId();
    out.cherryPickOfChange =
        cd.change().getCherryPickOf() != null
            ? cd.change().getCherryPickOf().changeId().get()
            : null;
    out.cherryPickOfPatchSet =
        cd.change().getCherryPickOf() != null ? cd.change().getCherryPickOf().get() : null;

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
    finish(out, experimentFeatures);

    // This block must come after the ChangeInfo is mostly populated, since
    // it will be passed to ActionVisitors as-is.
    if (needRevisions) {
      out.revisions = revisionJson.getRevisions(accountLoader, cd, src, limitToPsId, out);
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
      actionJson.addChangeActions(out, cd);
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

  private Collection<ReviewerUpdateInfo> reviewerUpdates(ChangeData cd) {
    List<ReviewerStatusUpdate> reviewerUpdates = cd.reviewerUpdates();
    List<ReviewerUpdateInfo> result = new ArrayList<>(reviewerUpdates.size());
    for (ReviewerStatusUpdate c : reviewerUpdates) {
      ReviewerUpdateInfo change =
          new ReviewerUpdateInfo(
              c.date(),
              accountLoader.get(c.updatedBy()),
              accountLoader.get(c.reviewer()),
              c.state().asReviewerState());
      result.add(change);
    }
    return result;
  }

  private boolean submittable(ChangeData cd) {
    return cd.submitRequirementsIncludingLegacy().values().stream()
        .allMatch(SubmitRequirementResult::fulfilled);
  }

  private void setSubmitter(ChangeData cd, ChangeInfo out) {
    Optional<PatchSetApproval> s = cd.getSubmitApproval();
    if (!s.isPresent()) {
      return;
    }
    out.setSubmitted(s.get().granted(), accountLoader.get(s.get().accountId()));
  }

  private ImmutableList<ChangeMessageInfo> messages(ChangeData cd) {
    List<ChangeMessage> messages = cmUtil.byChange(cd.notes());
    if (messages.isEmpty()) {
      return ImmutableList.of();
    }

    List<ChangeMessageInfo> result = Lists.newArrayListWithCapacity(messages.size());
    for (ChangeMessage message : messages) {
      result.add(createChangeMessageInfo(message, accountLoader));
    }
    return ImmutableList.copyOf(result);
  }

  private Collection<AccountInfo> removableReviewers(ChangeData cd, ChangeInfo out)
      throws PermissionBackendException {
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
    Set<Account.Id> removable = new HashSet<>();

    // Add all reviewers, which will later be removed if they are in the "fixed" set.
    removable.addAll(
        out.reviewers.getOrDefault(ReviewerState.REVIEWER, Collections.emptySet()).stream()
            .filter(a -> a._accountId != null)
            .map(a -> Account.id(a._accountId))
            .collect(Collectors.toSet()));

    // Check if the user has the permission to remove a reviewer. This means we can bypass the
    // testRemoveReviewer check for a specific reviewer in the loop saving potentially many
    // permission checks.
    boolean canRemoveAnyReviewer =
        permissionBackend
            .user(userProvider.get())
            .change(cd)
            .test(ChangePermission.REMOVE_REVIEWER);
    for (LabelInfo label : labels) {
      if (label.all == null) {
        continue;
      }
      for (ApprovalInfo ai : label.all) {
        Account.Id id = Account.id(ai._accountId);

        if (!canRemoveAnyReviewer
            && !removeReviewerControl.testRemoveReviewer(
                cd, userProvider.get(), id, MoreObjects.firstNonNull(ai.value, 0))) {
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
          Account.Id id = Account.id(ai._accountId);
          if (canRemoveAnyReviewer
              || removeReviewerControl.testRemoveReviewer(cd, userProvider.get(), id, 0)) {
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
        .map(a -> new AccountInfo(a.name(), a.email()))
        .sorted(AccountInfoComparator.ORDER_NULLS_FIRST)
        .collect(toList());
  }

  private Map<PatchSet.Id, PatchSet> loadPatchSets(
      ChangeData cd, Optional<PatchSet.Id> limitToPsId) {
    Collection<PatchSet> src;
    if (has(ALL_REVISIONS) || has(MESSAGES)) {
      src = cd.patchSets();
    } else {
      PatchSet ps;
      if (limitToPsId.isPresent()) {
        ps = cd.patchSet(limitToPsId.get());
        if (ps == null) {
          throw new StorageException("missing patch set " + limitToPsId.get());
        }
      } else {
        ps = cd.currentPatchSet();
        if (ps == null) {
          throw new StorageException("missing current patch set for change " + cd.getId());
        }
      }
      src = Collections.singletonList(ps);
    }
    // Sort by patch set ID in increasing order to have a stable output.
    ImmutableSortedMap.Builder<PatchSet.Id, PatchSet> map = ImmutableSortedMap.naturalOrder();
    for (PatchSet patchSet : src) {
      map.put(patchSet.id(), patchSet);
    }
    return map.build();
  }

  /** Populate the 'starred' field. */
  private void populateStarField(List<ChangeInfo> changeInfos) {
    // We populate the 'starred' field for all change infos together so that we open the All-Users
    // repository only once
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      List<Change.Id> changeIds =
          changeInfos.stream().map(c -> Change.id(c._number)).collect(Collectors.toList());
      Set<Change.Id> starredChanges =
          starredChangesUtil.areStarred(
              allUsersRepo, changeIds, userProvider.get().asIdentifiedUser().getAccountId());
      if (starredChanges.isEmpty()) {
        return;
      }
      changeInfos.stream().forEach(c -> c.starred = starredChanges.contains(Change.id(c._number)));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to open All-Users repo.");
    }
  }

  private List<PluginDefinedInfo> getPluginInfos(ChangeData cd) {
    return getPluginInfos(Collections.singleton(cd)).get(cd.getId());
  }

  private ImmutableListMultimap<Change.Id, PluginDefinedInfo> getPluginInfos(
      Collection<ChangeData> cds) {
    if (pluginDefinedInfosFactory.isPresent()) {
      return pluginDefinedInfosFactory.get().createPluginDefinedInfos(cds);
    }
    return ImmutableListMultimap.of();
  }
}
