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

import static com.google.gerrit.common.changes.ListChangesOption.ALL_COMMITS;
import static com.google.gerrit.common.changes.ListChangesOption.ALL_FILES;
import static com.google.gerrit.common.changes.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.common.changes.ListChangesOption.DETAILED_ACCOUNTS;
import static com.google.gerrit.common.changes.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.common.changes.ListChangesOption.LABELS;
import static com.google.gerrit.common.changes.ListChangesOption.MESSAGES;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.PatchSetInfo.ParentInfo;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.actions.ActionInfo;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.ssh.SshAdvertisedAddresses;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ChangeJson {
  private static final Logger log = LoggerFactory.getLogger(ChangeJson.class);

  @Singleton
  static class Urls {
    final String git;
    final String http;
    final String ssh;

    @Inject
    Urls(@GerritServerConfig Config cfg,
        @SshAdvertisedAddresses List<String> sshAddresses) {
      this.git = ensureSlash(cfg.getString("gerrit", null, "canonicalGitUrl"));
      this.http = ensureSlash(cfg.getString("gerrit", null, "gitHttpUrl"));
      this.ssh = !sshAddresses.isEmpty()
          ? ensureSlash("ssh://" + sshAddresses.get(0))
          : null;
    }

    private static String ensureSlash(String in) {
      if (in != null && !in.endsWith("/")) {
        return in + "/";
      }
      return in;
    }
  }

  private final Provider<ReviewDb> db;
  private final LabelNormalizer labelNormalizer;
  private final Provider<CurrentUser> userProvider;
  private final AnonymousUser anonymous;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeControl.GenericFactory changeControlGenericFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final FileInfoJson fileInfoJson;
  private final AccountInfo.Loader.Factory accountLoaderFactory;
  private final Provider<String> urlProvider;
  private final Urls urls;
  private final DynamicMap<RestView<ChangeResource>> changes;
  private final Revisions revisions;

  private ChangeControl.Factory changeControlUserFactory;
  private EnumSet<ListChangesOption> options;
  private AccountInfo.Loader accountLoader;
  private ChangeControl lastControl;

  @Inject
  ChangeJson(
      Provider<ReviewDb> db,
      LabelNormalizer ln,
      Provider<CurrentUser> userProvider,
      AnonymousUser au,
      IdentifiedUser.GenericFactory uf,
      ChangeControl.GenericFactory ccf,
      PatchSetInfoFactory psi,
      FileInfoJson fileInfoJson,
      AccountInfo.Loader.Factory ailf,
      @CanonicalWebUrl Provider<String> curl,
      Urls urls,
      DynamicMap<RestView<ChangeResource>> changes,
      Revisions revisions) {
    this.db = db;
    this.labelNormalizer = ln;
    this.userProvider = userProvider;
    this.anonymous = au;
    this.userFactory = uf;
    this.changeControlGenericFactory = ccf;
    this.patchSetInfoFactory = psi;
    this.fileInfoJson = fileInfoJson;
    this.accountLoaderFactory = ailf;
    this.urlProvider = curl;
    this.urls = urls;
    this.changes = changes;
    this.revisions = revisions;

    options = EnumSet.noneOf(ListChangesOption.class);
  }

  public ChangeJson addOption(ListChangesOption o) {
    options.add(o);
    return this;
  }

  public ChangeJson addOptions(Collection<ListChangesOption> o) {
    options.addAll(o);
    return this;
  }

  public ChangeJson setChangeControlFactory(ChangeControl.Factory cf) {
    changeControlUserFactory = cf;
    return this;
  }

  public ChangeInfo format(ChangeResource rsrc) throws OrmException {
    return format(new ChangeData(rsrc.getControl()));
  }

  public ChangeInfo format(Change change) throws OrmException {
    return format(new ChangeData(change));
  }

  public ChangeInfo format(Change.Id id) throws OrmException {
    return format(new ChangeData(id));
  }

  public ChangeInfo format(ChangeData cd) throws OrmException {
    List<ChangeData> tmp = ImmutableList.of(cd);
    return formatList2(ImmutableList.of(tmp)).get(0).get(0);
  }

  public ChangeInfo format(RevisionResource rsrc) throws OrmException {
    ChangeData cd = new ChangeData(rsrc.getControl());
    cd.limitToPatchSets(ImmutableList.of(rsrc.getPatchSet().getId()));
    return format(cd);
  }

  public List<List<ChangeInfo>> formatList2(List<List<ChangeData>> in)
      throws OrmException {
    accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    List<List<ChangeInfo>> res = Lists.newArrayListWithCapacity(in.size());
    for (List<ChangeData> changes : in) {
      ChangeData.ensureChangeLoaded(db, changes);
      if (has(ALL_REVISIONS)) {
        ChangeData.ensureAllPatchSetsLoaded(db, changes);
      } else {
        ChangeData.ensureCurrentPatchSetLoaded(db, changes);
      }
      ChangeData.ensureCurrentApprovalsLoaded(db, changes);
      res.add(toChangeInfo(changes));
    }
    accountLoader.fill();
    return res;
  }

  private boolean has(ListChangesOption option) {
    return options.contains(option);
  }

  private List<ChangeInfo> toChangeInfo(List<ChangeData> changes)
      throws OrmException {
    List<ChangeInfo> info = Lists.newArrayListWithCapacity(changes.size());
    for (ChangeData cd : changes) {
      info.add(toChangeInfo(cd));
    }
    return info;
  }

  private ChangeInfo toChangeInfo(ChangeData cd) throws OrmException {
    ChangeInfo out = new ChangeInfo();
    Change in = cd.change(db);
    out.project = in.getProject().get();
    out.branch = in.getDest().getShortName();
    out.topic = in.getTopic();
    out.changeId = in.getKey().get();
    out.mergeable = in.getStatus() != Change.Status.MERGED ? in.isMergeable() : null;
    out.subject = in.getSubject();
    out.status = in.getStatus();
    out.owner = accountLoader.get(in.getOwner());
    out.created = in.getCreatedOn();
    out.updated = in.getLastUpdatedOn();
    out._number = in.getId().get();
    out._sortkey = in.getSortKey();
    out.starred = userProvider.get().getStarredChanges().contains(in.getId())
        ? true
        : null;
    out.reviewed = in.getStatus().isOpen() && isChangeReviewed(cd) ? true : null;
    out.labels = labelsFor(cd, has(LABELS), has(DETAILED_LABELS));

    Collection<PatchSet.Id> limited = cd.getLimitedPatchSets();
    if (out.labels != null && has(DETAILED_LABELS)) {
      // If limited to specific patch sets but not the current patch set, don't
      // list permitted labels, since users can't vote on those patch sets.
      if (limited == null || limited.contains(in.currentPatchSetId())) {
        out.permitted_labels = permittedLabels(cd);
      }
      out.removable_reviewers = removableReviewers(cd, out.labels.values());
    }
    if (options.contains(MESSAGES)) {
      out.messages = messages(cd);
    }
    out.finish();

    if (has(ALL_REVISIONS) || has(CURRENT_REVISION) || limited != null) {
      out.revisions = revisions(cd);
      if (out.revisions != null) {
        for (String commit : out.revisions.keySet()) {
          if (out.revisions.get(commit).isCurrent) {
            out.current_revision = commit;
            break;
          }
        }
      }
    }

    if (has(CURRENT_ACTIONS) && userProvider.get().isIdentifiedUser()) {
      out.actions = Maps.newTreeMap();
      for (UiAction.Description d : UiActions.from(
          changes,
          new ChangeResource(control(cd)),
          userProvider)) {
        out.actions.put(d.getId(), new ActionInfo(d));
      }
    }
    lastControl = null;
    return out;
  }

  private ChangeControl control(ChangeData cd) throws OrmException {
    ChangeControl ctrl = cd.changeControl();
    if (ctrl != null && ctrl.getCurrentUser() == userProvider.get()) {
      return ctrl;
    } else if (lastControl != null
        && cd.getId().equals(lastControl.getChange().getId())) {
      return lastControl;
    }

    try {
      if (changeControlUserFactory != null) {
        ctrl = changeControlUserFactory.controlFor(cd.change(db));
      } else {
        ctrl = changeControlGenericFactory.controlFor(cd.change(db),
            userProvider.get());
      }
    } catch (NoSuchChangeException e) {
      return null;
    }
    lastControl = ctrl;
    return ctrl;
  }

  private List<SubmitRecord> submitRecords(ChangeData cd) throws OrmException {
    if (cd.getSubmitRecords() != null) {
      return cd.getSubmitRecords();
    }
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return ImmutableList.of();
    }
    PatchSet ps = cd.currentPatchSet(db);
    if (ps == null) {
      return ImmutableList.of();
    }
    cd.setSubmitRecords(ctl.canSubmit(db.get(), ps, cd, true, false, true));
    return cd.getSubmitRecords();
  }

  private Map<String, LabelInfo> labelsFor(ChangeData cd, boolean standard,
      boolean detailed) throws OrmException {
    if (!standard && !detailed) {
      return null;
    }

    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return null;
    }

    LabelTypes labelTypes = ctl.getLabelTypes();
    if (cd.getChange().getStatus().isOpen()) {
      return labelsForOpenChange(cd, labelTypes, standard, detailed);
    } else {
      return labelsForClosedChange(cd, labelTypes, standard, detailed);
    }
  }

  private Map<String, LabelInfo> labelsForOpenChange(ChangeData cd,
      LabelTypes labelTypes, boolean standard, boolean detailed)
      throws OrmException {
    Map<String, LabelInfo> labels = initLabels(cd, labelTypes, standard);
    if (detailed) {
      setAllApprovals(cd, labels);
    }
    for (Map.Entry<String, LabelInfo> e : labels.entrySet()) {
      LabelType type = labelTypes.byLabel(e.getKey());
      if (type == null) {
        continue;
      }
      if (standard) {
        for (PatchSetApproval psa : cd.currentApprovals(db)) {
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

  private Map<String, LabelInfo> initLabels(ChangeData cd,
      LabelTypes labelTypes, boolean standard) throws OrmException {
    // Don't use Maps.newTreeMap(Comparator) due to OpenJDK bug 100167.
    Map<String, LabelInfo> labels =
        new TreeMap<String, LabelInfo>(labelTypes.nameComparator());
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelInfo p = labels.get(r.label);
        if (p == null || p._status.compareTo(r.status) < 0) {
          LabelInfo n = new LabelInfo();
          n._status = r.status;
          if (standard) {
            switch (r.status) {
              case OK:
                n.approved = accountLoader.get(r.appliedBy);
                break;
              case REJECT:
                n.rejected = accountLoader.get(r.appliedBy);
                break;
              default:
                break;
            }
          }

          n.optional = n._status == SubmitRecord.Label.Status.MAY ? true : null;
          labels.put(r.label, n);
        }
      }
    }
    return labels;
  }

  private void setLabelScores(LabelType type,
      LabelInfo label, short score, Account.Id accountId)
      throws OrmException {
    if (label.approved != null || label.rejected != null) {
      return;
    }

    if (type.getMin() == null || type.getMax() == null) {
      // Can't set score for unknown or misconfigured type.
      return;
    }

    if (score != 0) {
      if (score == type.getMax().getValue()) {
        label.approved = accountLoader.get(accountId);
      } else if (score == type.getMin().getValue()) {
        label.rejected = accountLoader.get(accountId);
      } else if (score > 0) {
        label.recommended = accountLoader.get(accountId);
        label.value = score;
      } else if (score < 0) {
        label.disliked = accountLoader.get(accountId);
        label.value = score;
      }
    }

    return;
  }

  private void setAllApprovals(ChangeData cd,
      Map<String, LabelInfo> labels) throws OrmException {
    ChangeControl baseCtrl = control(cd);
    if (baseCtrl == null) {
      return;
    }

    // All users ever added, even if they can't vote on one or all labels.
    Set<Account.Id> allUsers = Sets.newHashSet();
    ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals =
        cd.allApprovalsMap(db);
    for (PatchSetApproval psa : allApprovals.values()) {
      allUsers.add(psa.getAccountId());
    }

    List<PatchSetApproval> currentList = labelNormalizer.normalize(
        baseCtrl, allApprovals.get(baseCtrl.getChange().currentPatchSetId()));
    // Most recent, normalized vote on each label for the current patch set by
    // each user (may be 0).
    Table<Account.Id, String, PatchSetApproval> current = HashBasedTable.create(
        allUsers.size(), baseCtrl.getLabelTypes().getLabelTypes().size());
    for (PatchSetApproval psa : currentList) {
      current.put(psa.getAccountId(), psa.getLabel(), psa);
    }

    for (Account.Id accountId : allUsers) {
      IdentifiedUser user = userFactory.create(accountId);
      ChangeControl ctl = baseCtrl.forUser(user);
      for (Map.Entry<String, LabelInfo> e : labels.entrySet()) {
        LabelType lt = ctl.getLabelTypes().byLabel(e.getKey());
        if (lt == null) {
          // Ignore submit record for undefined label; likely the submit rule
          // author didn't intend for the label to show up in the table.
          continue;
        }
        Integer value;
        Timestamp date = null;
        PatchSetApproval psa = current.get(accountId, lt.getName());
        if (psa != null) {
          value = Integer.valueOf(psa.getValue());
          date = psa.getGranted();
        } else {
          // Either the user cannot vote on this label, or there just wasn't a
          // dummy approval for this label. Explicitly check whether the user
          // can vote on this label.
          value = labelNormalizer.canVote(ctl, lt, accountId) ? 0 : null;
        }
        e.getValue().addApproval(approvalInfo(accountId, value, date));
      }
    }
  }

  private Map<String, LabelInfo> labelsForClosedChange(ChangeData cd,
      LabelTypes labelTypes, boolean standard, boolean detailed)
      throws OrmException {
    Set<Account.Id> allUsers = Sets.newHashSet();
    for (PatchSetApproval psa : cd.allApprovals(db)) {
      allUsers.add(psa.getAccountId());
    }

    Set<String> labelNames = Sets.newHashSet();
    Multimap<Account.Id, PatchSetApproval> current = HashMultimap.create();
    for (PatchSetApproval a : cd.currentApprovals(db)) {
      LabelType type = labelTypes.byLabel(a.getLabelId());
      if (type != null && a.getValue() != 0) {
        labelNames.add(type.getName());
        current.put(a.getAccountId(), a);
      }
    }

    // We can only approximately reconstruct what the submit rule evaluator
    // would have done. These should really come from a stored submit record.
    //
    // Don't use Maps.newTreeMap(Comparator) due to OpenJDK bug 100167.
    Map<String, LabelInfo> labels =
        new TreeMap<String, LabelInfo>(labelTypes.nameComparator());
    for (String name : labelNames) {
      LabelType type = labelTypes.byLabel(name);
      LabelInfo li = new LabelInfo();
      if (detailed) {
        setLabelValues(type, li);
      }
      labels.put(type.getName(), li);
    }

    for (Account.Id accountId : allUsers) {
      Map<String, ApprovalInfo> byLabel =
          Maps.newHashMapWithExpectedSize(labels.size());

      if (detailed) {
        for (String name : labels.keySet()) {
          ApprovalInfo ai = approvalInfo(accountId, 0, null);
          byLabel.put(name, ai);
          labels.get(name).addApproval(ai);
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
        }

        LabelInfo li = labels.get(type.getName());
        if (!standard) {
          continue;
        }

        setLabelScores(type, li, val, accountId);
      }
    }
    return labels;
  }

  private ApprovalInfo approvalInfo(Account.Id id, Integer value, Timestamp date) {
    ApprovalInfo ai = new ApprovalInfo(id);
    ai.value = value;
    ai.date = date;
    accountLoader.put(ai);
    return ai;
  }

  private static boolean isOnlyZero(Collection<String> values) {
    return values.isEmpty() || (values.size() == 1 && values.contains(" 0"));
  }

  private void setLabelValues(LabelType type, LabelInfo label) {
    label.values = Maps.newLinkedHashMap();
    for (LabelValue v : type.getValues()) {
      label.values.put(v.formatValue(), v.getText());
    }
    if (isOnlyZero(label.values.keySet())) {
      label.values = null;
    }
  }

  private Map<String, Collection<String>> permittedLabels(ChangeData cd)
      throws OrmException {
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return null;
    }

    LabelTypes labelTypes = ctl.getLabelTypes();
    ListMultimap<String, String> permitted = LinkedListMultimap.create();
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
    List<String> toClear =
      Lists.newArrayListWithCapacity(permitted.keySet().size());
    for (Map.Entry<String, Collection<String>> e
        : permitted.asMap().entrySet()) {
      if (isOnlyZero(e.getValue())) {
        toClear.add(e.getKey());
      }
    }
    for (String label : toClear) {
      permitted.removeAll(label);
    }
    return permitted.asMap();
  }

  private Collection<ChangeMessageInfo> messages(ChangeData cd)
      throws OrmException {
    List<ChangeMessage> messages =
        db.get().changeMessages().byChange(cd.getId()).toList();
    if (messages.isEmpty()) {
      return Collections.emptyList();
    }

    // chronological order
    Collections.sort(messages, new Comparator<ChangeMessage>() {
      @Override
      public int compare(ChangeMessage a, ChangeMessage b) {
        return a.getWrittenOn().compareTo(b.getWrittenOn());
      }
    });

    List<ChangeMessageInfo> result =
        Lists.newArrayListWithCapacity(messages.size());
    for (ChangeMessage message : messages) {
      PatchSet.Id patchNum = message.getPatchSetId();

      ChangeMessageInfo cmi = new ChangeMessageInfo();
      cmi.id = message.getKey().get();
      cmi.author = accountLoader.get(message.getAuthor());
      cmi.date = message.getWrittenOn();
      cmi.message = message.getMessage();
      cmi._revisionNumber = patchNum != null ? patchNum.get() : null;
      result.add(cmi);
    }
    return result;
  }

  private Collection<AccountInfo> removableReviewers(ChangeData cd,
      Collection<LabelInfo> labels) throws OrmException {
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return null;
    }

    Set<Account.Id> fixed = Sets.newHashSetWithExpectedSize(labels.size());
    Set<Account.Id> removable = Sets.newHashSetWithExpectedSize(labels.size());
    for (LabelInfo label : labels) {
      if (label.all == null) {
        continue;
      }
      for (ApprovalInfo ai : label.all) {
        if (ctl.canRemoveReviewer(ai._id, Objects.firstNonNull(ai.value, 0))) {
          removable.add(ai._id);
        } else {
          fixed.add(ai._id);
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

  private boolean isChangeReviewed(ChangeData cd) throws OrmException {
    CurrentUser user = userProvider.get();
    if (user.isIdentifiedUser()) {
      PatchSet currentPatchSet = cd.currentPatchSet(db);
      if (currentPatchSet == null) {
        return false;
      }

      List<ChangeMessage> messages =
          db.get().changeMessages().byPatchSet(currentPatchSet.getId()).toList();

      if (messages.isEmpty()) {
        return false;
      }

      // Sort messages to let the most recent ones at the beginning.
      Collections.sort(messages, new Comparator<ChangeMessage>() {
        @Override
        public int compare(ChangeMessage a, ChangeMessage b) {
          return b.getWrittenOn().compareTo(a.getWrittenOn());
        }
      });

      Account.Id currentUserId = ((IdentifiedUser) user).getAccountId();
      Account.Id changeOwnerId = cd.change(db).getOwner();
      for (ChangeMessage cm : messages) {
        if (currentUserId.equals(cm.getAuthor())) {
          return true;
        } else if (changeOwnerId.equals(cm.getAuthor())) {
          return false;
        }
      }
    }
    return false;
  }

  private Map<String, RevisionInfo> revisions(ChangeData cd) throws OrmException {
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return null;
    }

    Collection<PatchSet> src;
    if (cd.getLimitedPatchSets() != null || has(ALL_REVISIONS)) {
      src = cd.patches(db);
    } else {
      src = Collections.singletonList(cd.currentPatchSet(db));
    }
    Map<String, RevisionInfo> res = Maps.newLinkedHashMap();
    for (PatchSet in : src) {
      if (ctl.isPatchVisible(in, db.get())) {
        res.put(in.getRevision().get(), toRevisionInfo(cd, in));
      }
    }
    return res;
  }

  private RevisionInfo toRevisionInfo(ChangeData cd, PatchSet in)
      throws OrmException {
    RevisionInfo out = new RevisionInfo();
    out.isCurrent = in.getId().equals(cd.change(db).currentPatchSetId());
    out._number = in.getId().get();
    out.draft = in.isDraft() ? true : null;
    out.fetch = makeFetchMap(cd, in);

    if (has(ALL_COMMITS) || (out.isCurrent && has(CURRENT_COMMIT))) {
      try {
        out.commit = toCommit(in);
      } catch (PatchSetInfoNotAvailableException e) {
        log.warn("Cannot load PatchSetInfo " + in.getId(), e);
      }
    }

    if (has(ALL_FILES) || (out.isCurrent && has(CURRENT_FILES))) {
      try {
        out.files = fileInfoJson.toFileInfoMap(cd.change(db), in);
        out.files.remove(Patch.COMMIT_MSG);
      } catch (PatchListNotAvailableException e) {
        log.warn("Cannot load PatchList " + in.getId(), e);
      }
    }

    if (out.isCurrent && has(CURRENT_ACTIONS)
        && userProvider.get().isIdentifiedUser()) {
      out.actions = Maps.newTreeMap();
      for (UiAction.Description d : UiActions.from(
          revisions,
          new RevisionResource(new ChangeResource(control(cd)), in),
          userProvider)) {
        out.actions.put(d.getId(), new ActionInfo(d));
      }
    }
    return out;
  }

  CommitInfo toCommit(PatchSet in)
      throws PatchSetInfoNotAvailableException {
    PatchSetInfo info = patchSetInfoFactory.get(db.get(), in.getId());
    CommitInfo commit = new CommitInfo();
    commit.parents = Lists.newArrayListWithCapacity(info.getParents().size());
    commit.author = toGitPerson(info.getAuthor());
    commit.committer = toGitPerson(info.getCommitter());
    commit.subject = info.getSubject();
    commit.message = info.getMessage();

    for (ParentInfo parent : info.getParents()) {
      CommitInfo i = new CommitInfo();
      i.commit = parent.id.get();
      i.subject = parent.shortMessage;
      commit.parents.add(i);
    }
    return commit;
  }

  private Map<String, FetchInfo> makeFetchMap(ChangeData cd, PatchSet in)
      throws OrmException {
    Map<String, FetchInfo> r = Maps.newLinkedHashMap();
    String refName = in.getRefName();
    ChangeControl ctl = control(cd);
    if (ctl != null && ctl.forUser(anonymous).isPatchVisible(in, db.get())) {
      if (urls.git != null) {
        r.put("git", new FetchInfo(urls.git
            + cd.change(db).getProject().get(), refName));
      }
    }
    if (urls.http != null) {
      r.put("http", new FetchInfo(urls.http
          + cd.change(db).getProject().get(), refName));
    } else {
      String http = urlProvider.get();
      if (!Strings.isNullOrEmpty(http)) {
        r.put("http", new FetchInfo(http
            + cd.change(db).getProject().get(), refName));
      }
    }
    if (urls.ssh != null) {
      r.put("ssh", new FetchInfo(
          urls.ssh + cd.change(db).getProject().get(),
          refName));
    }

    return r;
  }

  private static GitPerson toGitPerson(UserIdentity committer) {
    GitPerson p = new GitPerson();
    p.name = committer.getName();
    p.email = committer.getEmail();
    p.date = committer.getDate();
    p.tz = committer.getTimeZone();
    return p;
  }

  public static class ChangeInfo {
    final String kind = "gerritcodereview#change";
    String id;
    String project;
    String branch;
    String topic;
    public String changeId;
    public String subject;
    Change.Status status;
    Timestamp created;
    Timestamp updated;
    Boolean starred;
    Boolean reviewed;
    Boolean mergeable;

    String _sortkey;
    int _number;

    AccountInfo owner;

    Map<String, ActionInfo> actions;
    Map<String, LabelInfo> labels;
    Map<String, Collection<String>> permitted_labels;
    Collection<AccountInfo> removable_reviewers;
    Collection<ChangeMessageInfo> messages;

    String current_revision;
    Map<String, RevisionInfo> revisions;
    public Boolean _moreChanges;

    void finish() {
      id = Joiner.on('~').join(
          Url.encode(project),
          Url.encode(branch),
          Url.encode(changeId));
    }
  }

  static class RevisionInfo {
    private transient boolean isCurrent;
    Boolean draft;
    int _number;
    Map<String, FetchInfo> fetch;
    CommitInfo commit;
    Map<String, FileInfoJson.FileInfo> files;
    Map<String, ActionInfo> actions;
  }

  static class FetchInfo {
    String url;
    String ref;

    FetchInfo(String url, String ref) {
      this.url = url;
      this.ref = ref;
    }
  }

  static class GitPerson {
    String name;
    String email;
    Timestamp date;
    int tz;
  }

  static class CommitInfo {
    final String kind = "gerritcodereview#commit";
    String commit;
    List<CommitInfo> parents;
    GitPerson author;
    GitPerson committer;
    String subject;
    String message;
  }

  static class LabelInfo {
    transient SubmitRecord.Label.Status _status;

    AccountInfo approved;
    AccountInfo rejected;
    AccountInfo recommended;
    AccountInfo disliked;
    List<ApprovalInfo> all;

    Map<String, String> values;

    Short value;
    Boolean optional;

    void addApproval(ApprovalInfo ai) {
      if (all == null) {
        all = Lists.newArrayList();
      }
      all.add(ai);
    }
  }

  static class ApprovalInfo extends AccountInfo {
    Integer value;
    Timestamp date;

    ApprovalInfo(Account.Id id) {
      super(id);
    }
  }

  static class ChangeMessageInfo {
    String id;
    AccountInfo author;
    Timestamp date;
    String message;
    Integer _revisionNumber;
  }
}
