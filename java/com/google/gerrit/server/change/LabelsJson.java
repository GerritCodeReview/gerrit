// Copyright (C) 2018 The Android Open Source Project
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
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.VotingRangeInfo;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Produces label-related entities, like {@link LabelInfo}s, which is serialized to JSON afterwards.
 */
public class LabelsJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    LabelsJson create(Iterable<ListChangesOption> options);
  }

  private final ApprovalsUtil approvalsUtil;
  private final ChangeNotes.Factory notesFactory;
  private final PermissionBackend permissionBackend;
  private final boolean lazyLoad;

  @Inject
  LabelsJson(
      ApprovalsUtil approvalsUtil,
      ChangeNotes.Factory notesFactory,
      PermissionBackend permissionBackend,
      @Assisted Iterable<ListChangesOption> options) {
    this.approvalsUtil = approvalsUtil;
    this.notesFactory = notesFactory;
    this.permissionBackend = permissionBackend;
    this.lazyLoad = containsAnyOf(Sets.immutableEnumSet(options), ChangeJson.REQUIRE_LAZY_LOAD);
  }

  /**
   * Returns all {@link LabelInfo}s for a single change. Uses the provided {@link AccountLoader} to
   * lazily populate accounts. Callers have to call {@link AccountLoader#fill()} afterwards to
   * populate all accounts in the returned {@link LabelInfo}s.
   */
  Map<String, LabelInfo> labelsFor(
      AccountLoader accountLoader, ChangeData cd, boolean standard, boolean detailed)
      throws PermissionBackendException {
    if (!standard && !detailed) {
      return null;
    }

    LabelTypes labelTypes = cd.getLabelTypes();
    Map<String, LabelWithStatus> withStatus =
        cd.change().isMerged()
            ? labelsForSubmittedChange(accountLoader, cd, labelTypes, standard, detailed)
            : labelsForUnsubmittedChange(accountLoader, cd, labelTypes, standard, detailed);
    return ImmutableMap.copyOf(Maps.transformValues(withStatus, LabelWithStatus::label));
  }

  /** Returns all labels that the provided user has permission to vote on. */
  Map<String, Collection<String>> permittedLabels(Account.Id filterApprovalsBy, ChangeData cd)
      throws PermissionBackendException {
    boolean isMerged = cd.change().isMerged();
    LabelTypes labelTypes = cd.getLabelTypes();
    Map<String, LabelType> toCheck = new HashMap<>();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels != null) {
        for (SubmitRecord.Label r : rec.labels) {
          LabelType type = labelTypes.byLabel(r.label);
          if (type != null && (!isMerged || type.isAllowPostSubmit())) {
            toCheck.put(type.getName(), type);
          }
        }
      }
    }

    Map<String, Short> labels = null;
    Set<LabelPermission.WithValue> can =
        permissionBackend.absentUser(filterApprovalsBy).change(cd).testLabels(toCheck.values());
    SetMultimap<String, String> permitted = LinkedHashMultimap.create();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelType type = labelTypes.byLabel(r.label);
        if (type == null || (isMerged && !type.isAllowPostSubmit())) {
          continue;
        }

        for (LabelValue v : type.getValues()) {
          boolean ok = can.contains(new LabelPermission.WithValue(type, v));
          if (isMerged) {
            if (labels == null) {
              labels = currentLabels(filterApprovalsBy, cd);
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

  private static boolean containsAnyOf(
      ImmutableSet<ListChangesOption> set, ImmutableSet<ListChangesOption> toFind) {
    return !Sets.intersection(toFind, set).isEmpty();
  }

  private static boolean isOnlyZero(Collection<String> values) {
    return values.isEmpty() || (values.size() == 1 && values.contains(" 0"));
  }

  private static void addApproval(LabelInfo label, ApprovalInfo approval) {
    if (label.all == null) {
      label.all = new ArrayList<>();
    }
    label.all.add(approval);
  }

  private Map<String, LabelWithStatus> labelsForUnsubmittedChange(
      AccountLoader accountLoader,
      ChangeData cd,
      LabelTypes labelTypes,
      boolean standard,
      boolean detailed)
      throws PermissionBackendException {
    Map<String, LabelWithStatus> labels = initLabels(accountLoader, cd, labelTypes, standard);
    if (detailed) {
      setAllApprovals(accountLoader, cd, labels);
    }
    for (Map.Entry<String, LabelWithStatus> e : labels.entrySet()) {
      LabelType type = labelTypes.byLabel(e.getKey());
      if (type == null) {
        continue;
      }
      if (standard) {
        for (PatchSetApproval psa : cd.currentApprovals()) {
          if (type.matches(psa)) {
            short val = psa.value();
            Account.Id accountId = psa.accountId();
            setLabelScores(accountLoader, type, e.getValue(), val, accountId);
          }
        }
      }
      if (detailed) {
        setLabelValues(type, e.getValue());
      }
    }
    return labels;
  }

  private Integer parseRangeValue(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    } else if (value.startsWith(" ")) {
      value = value.trim();
    }
    return Ints.tryParse(value);
  }

  private ApprovalInfo approvalInfo(
      AccountLoader accountLoader,
      Account.Id id,
      Integer value,
      VotingRangeInfo permittedVotingRange,
      String tag,
      Timestamp date) {
    ApprovalInfo ai = new ApprovalInfo(id.get(), value, permittedVotingRange, tag, date);
    accountLoader.put(ai);
    return ai;
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

  private Map<String, Short> currentLabels(Account.Id accountId, ChangeData cd) {
    Map<String, Short> result = new HashMap<>();
    for (PatchSetApproval psa :
        approvalsUtil.byPatchSetUser(
            lazyLoad ? cd.notes() : notesFactory.createFromIndexedChange(cd.change()),
            cd.change().currentPatchSetId(),
            accountId,
            null,
            null)) {
      result.put(psa.label(), psa.value());
    }
    return result;
  }

  private Map<String, LabelWithStatus> labelsForSubmittedChange(
      AccountLoader accountLoader,
      ChangeData cd,
      LabelTypes labelTypes,
      boolean standard,
      boolean detailed)
      throws PermissionBackendException {
    Set<Account.Id> allUsers = new HashSet<>();
    if (detailed) {
      // Users expect to see all reviewers on closed changes, even if they
      // didn't vote on the latest patch set. If we don't need detailed labels,
      // we aren't including 0 votes for all users below, so we can just look at
      // the latest patch set (in the next loop).
      for (PatchSetApproval psa : cd.approvals().values()) {
        allUsers.add(psa.accountId());
      }
    }

    Set<String> labelNames = new HashSet<>();
    SetMultimap<Account.Id, PatchSetApproval> current =
        MultimapBuilder.hashKeys().hashSetValues().build();
    for (PatchSetApproval a : cd.currentApprovals()) {
      allUsers.add(a.accountId());
      LabelType type = labelTypes.byLabel(a.labelId());
      if (type != null) {
        labelNames.add(type.getName());
        // Not worth the effort to distinguish between votable/non-votable for 0
        // values on closed changes, since they can't vote anyway.
        current.put(a.accountId(), a);
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
    labels = initLabels(accountLoader, cd, labelTypes, standard);

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
        pvr = getPermittedVotingRanges(permittedLabels(accountId, cd));
        for (Map.Entry<String, LabelWithStatus> entry : labels.entrySet()) {
          ApprovalInfo ai = approvalInfo(accountLoader, accountId, 0, null, null, null);
          byLabel.put(entry.getKey(), ai);
          addApproval(entry.getValue().label(), ai);
        }
      }
      for (PatchSetApproval psa : current.get(accountId)) {
        LabelType type = labelTypes.byLabel(psa.labelId());
        if (type == null) {
          continue;
        }

        short val = psa.value();
        ApprovalInfo info = byLabel.get(type.getName());
        if (info != null) {
          info.value = Integer.valueOf(val);
          info.permittedVotingRange = pvr.getOrDefault(type.getName(), null);
          info.date = psa.granted();
          info.tag = psa.tag().orElse(null);
          if (psa.postSubmit()) {
            info.postSubmit = true;
          }
        }
        if (!standard) {
          continue;
        }

        setLabelScores(accountLoader, type, labels.get(type.getName()), val, accountId);
      }
    }
    return labels;
  }

  private Map<String, LabelWithStatus> initLabels(
      AccountLoader accountLoader, ChangeData cd, LabelTypes labelTypes, boolean standard) {
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
      AccountLoader accountLoader,
      LabelType type,
      LabelWithStatus l,
      short score,
      Account.Id accountId) {
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
      AccountLoader accountLoader, ChangeData cd, Map<String, LabelWithStatus> labels)
      throws PermissionBackendException {
    checkState(
        !cd.change().isMerged(),
        "should not call setAllApprovals on %s change",
        ChangeUtil.status(cd.change()));

    // Include a user in the output for this label if either:
    //  - They are an explicit reviewer.
    //  - They ever voted on this change.
    Set<Account.Id> allUsers = new HashSet<>();
    allUsers.addAll(cd.reviewers().byState(ReviewerStateInternal.REVIEWER));
    for (PatchSetApproval psa : cd.approvals().values()) {
      allUsers.add(psa.accountId());
    }

    Table<Account.Id, String, PatchSetApproval> current =
        HashBasedTable.create(allUsers.size(), cd.getLabelTypes().getLabelTypes().size());
    for (PatchSetApproval psa : cd.currentApprovals()) {
      current.put(psa.accountId(), psa.label(), psa);
    }

    LabelTypes labelTypes = cd.getLabelTypes();
    for (Account.Id accountId : allUsers) {
      PermissionBackend.ForChange perm = permissionBackend.absentUser(accountId).change(cd);
      Map<String, VotingRangeInfo> pvr = getPermittedVotingRanges(permittedLabels(accountId, cd));
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
          value = Integer.valueOf(psa.value());
          if (value == 0) {
            // This may be a dummy approval that was inserted when the reviewer
            // was added. Explicitly check whether the user can vote on this
            // label.
            value = perm.test(new LabelPermission(lt)) ? 0 : null;
          }
          tag = psa.tag().orElse(null);
          date = psa.granted();
          if (psa.postSubmit()) {
            logger.atWarning().log("unexpected post-submit approval on open change: %s", psa);
          }
        } else {
          // Either the user cannot vote on this label, or they were added as a
          // reviewer but have not responded yet. Explicitly check whether the
          // user can vote on this label.
          value = perm.test(new LabelPermission(lt)) ? 0 : null;
        }
        addApproval(
            e.getValue().label(),
            approvalInfo(accountLoader, accountId, value, permittedVotingRange, tag, date));
      }
    }
  }

  private List<SubmitRecord> submitRecords(ChangeData cd) {
    return cd.submitRecords(ChangeJson.SUBMIT_RULE_OPTIONS_LENIENT);
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

  @AutoValue
  abstract static class LabelWithStatus {
    private static LabelWithStatus create(LabelInfo label, SubmitRecord.Label.Status status) {
      return new AutoValue_LabelsJson_LabelWithStatus(label, status);
    }

    abstract LabelInfo label();

    @Nullable
    abstract SubmitRecord.Label.Status status();
  }
}
