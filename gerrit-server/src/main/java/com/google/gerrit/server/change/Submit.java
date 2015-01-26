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

import static com.google.gerrit.common.data.SubmitRecord.Status.OK;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
public class Submit implements RestModifyView<RevisionResource, SubmitInput>,
    UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Submit.class);

  private static final String DEFAULT_TOOLTIP =
      "Submit patch set ${patchSet} into ${branch}";
  private static final String DEFAULT_TOPIC_TOOLTIP =
      "Submit all ${topicSize} changes of the same topic";
  private static final String DEFAULT_BLOCKED_TOPIC_TOOLTIP =
      "Other changes in this topic are not ready";
  private static final String DEFAULT_BLOCKED_HIDDEN_TOPIC_TOOLTIP =
      "Other hidden changes in this topic are not ready";

  public enum Status {
    SUBMITTED, MERGED
  }

  public static class Output {
    public Status status;
    transient Change change;

    private Output(Status s, Change c) {
      status = s;
      change = c;
    }
  }

  private final PersonIdent serverIdent;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final MergeQueue mergeQueue;
  private final ChangeIndexer indexer;
  private final LabelNormalizer labelNormalizer;
  private final AccountsCollection accounts;
  private final ChangesCollection changes;
  private final String label;
  private final ParameterizedString titlePattern;
  private final String submitTopicLabel;
  private final ParameterizedString submitTopicTooltip;
  private final boolean submitWholeTopic;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  Submit(@GerritPersonIdent PersonIdent serverIdent,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory userFactory,
      ChangeData.Factory changeDataFactory,
      ChangeUpdate.Factory updateFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      MergeQueue mergeQueue,
      AccountsCollection accounts,
      ChangesCollection changes,
      ChangeIndexer indexer,
      LabelNormalizer labelNormalizer,
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider) {
    this.serverIdent = serverIdent;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.userFactory = userFactory;
    this.changeDataFactory = changeDataFactory;
    this.updateFactory = updateFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.mergeQueue = mergeQueue;
    this.accounts = accounts;
    this.changes = changes;
    this.indexer = indexer;
    this.labelNormalizer = labelNormalizer;
    this.label = MoreObjects.firstNonNull(
        Strings.emptyToNull(cfg.getString("change", null, "submitLabel")),
        "Submit");
    this.titlePattern = new ParameterizedString(MoreObjects.firstNonNull(
        cfg.getString("change", null, "submitTooltip"),
        DEFAULT_TOOLTIP));
    submitWholeTopic = cfg.getBoolean("change", null, "submitWholeTopic" , false);
    this.submitTopicLabel = MoreObjects.firstNonNull(
        Strings.emptyToNull(cfg.getString("change", null, "submitTopicLabel")),
        "Submit whole topic");
    this.submitTopicTooltip = new ParameterizedString(MoreObjects.firstNonNull(
        cfg.getString("change", null, "submitTopicTooltip"),
        DEFAULT_TOPIC_TOOLTIP));
    this.queryProvider = queryProvider;
  }

  @Override
  public Output apply(RevisionResource rsrc, SubmitInput input)
      throws AuthException, ResourceConflictException,
      RepositoryNotFoundException, IOException, OrmException,
      UnprocessableEntityException {
    input.onBehalfOf = Strings.emptyToNull(input.onBehalfOf);
    if (input.onBehalfOf != null) {
      rsrc = onBehalfOf(rsrc, input);
    }
    ChangeControl control = rsrc.getControl();
    IdentifiedUser caller = (IdentifiedUser) control.getCurrentUser();
    Change change = rsrc.getChange();
    if (input.onBehalfOf == null && !control.canSubmit()) {
      throw new AuthException("submit not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    } else if (!ProjectUtil.branchExists(repoManager, change.getDest())) {
      throw new ResourceConflictException(String.format(
          "destination branch \"%s\" not found.",
          change.getDest().get()));
    } else if (!rsrc.getPatchSet().getId().equals(change.currentPatchSetId())) {
      // TODO Allow submitting non-current revision by changing the current.
      throw new ResourceConflictException(String.format(
          "revision %s is not current revision",
          rsrc.getPatchSet().getRevision().get()));
    }

    change = submit(rsrc, caller, false);
    if (change == null) {
      throw new ResourceConflictException("change is "
          + status(dbProvider.get().changes().get(rsrc.getChange().getId())));
    }

    if (input.waitForMerge) {
      mergeQueue.merge(change.getDest());
      change = dbProvider.get().changes().get(change.getId());
    } else {
      mergeQueue.schedule(change.getDest());
    }

    if (change == null) {
      throw new ResourceConflictException("change is deleted");
    }
    switch (change.getStatus()) {
      case SUBMITTED:
        return new Output(Status.SUBMITTED, change);
      case MERGED:
        return new Output(Status.MERGED, change);
      case NEW:
        ChangeMessage msg = getConflictMessage(rsrc);
        if (msg != null) {
          throw new ResourceConflictException(msg.getMessage());
        }
      default:
        throw new ResourceConflictException("change is " + status(change));
    }
  }

  /**
   * @param changes list of changes to be submitted at once
   * @param identifiedUser the user who is checking to submit
   * @return a reason why any of the changes is not submittable or null
   */
  private String areChangesSubmittable(List<ChangeData> changes,
      IdentifiedUser identifiedUser) {
    for (ChangeData c : changes) {
      try {
        ChangeControl changeControl = c.changeControl().forUser(
            identifiedUser);
        if (!changeControl.isVisible(dbProvider.get())) {
          return DEFAULT_BLOCKED_HIDDEN_TOPIC_TOOLTIP;
        }
        if (!changeControl.canSubmit()) {
          return DEFAULT_BLOCKED_TOPIC_TOOLTIP;
        }
        checkSubmitRule(c, c.currentPatchSet(), false);
      } catch (OrmException e) {
        log.error(String.valueOf(e));
        throw new OrmRuntimeException(e);
      } catch (ResourceConflictException e) {
        return DEFAULT_BLOCKED_TOPIC_TOOLTIP;
      }
    }
    return null;
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet.Id current = resource.getChange().currentPatchSetId();
    String topic = resource.getChange().getTopic();
    boolean visible = !resource.getPatchSet().isDraft()
        && resource.getChange().getStatus().isOpen()
        && resource.getPatchSet().getId().equals(current)
        && resource.getControl().canSubmit();
    if (!visible) {
      return new UiAction.Description()
        .setLabel("")
        .setTitle("")
        .setVisible(false);
    }
    if (submitWholeTopic && !Strings.isNullOrEmpty(topic)) {
      List<ChangeData> changesByTopic = null;
      try {
        changesByTopic = queryProvider.get().byTopicOpen(topic);
      } catch (OrmException e) {
        throw new OrmRuntimeException(e);
      }
      Map<String, String> params = ImmutableMap.of(
          "topicSize", String.valueOf(changesByTopic.size()));
      String topicProblems = areChangesSubmittable(changesByTopic,
          resource.getUser());
      if (!Strings.isNullOrEmpty(topicProblems)) {
        return new UiAction.Description()
          .setLabel(topicProblems)
          .setTitle(Strings.emptyToNull(
              submitTopicTooltip.replace(params)))
          .setVisible(true)
          .setEnabled(false);
      } else {
        return new UiAction.Description()
          .setLabel(submitTopicLabel)
          .setTitle(Strings.emptyToNull(
              submitTopicTooltip.replace(params)))
          .setVisible(true)
          .setEnabled(true);
      }
    } else {
      RevId revId = resource.getPatchSet().getRevision();
      Map<String, String> params = ImmutableMap.of(
          "patchSet", String.valueOf(resource.getPatchSet().getPatchSetId()),
          "branch", resource.getChange().getDest().getShortName(),
          "commit", ObjectId.fromString(revId.get()).abbreviate(7).name());
      return new UiAction.Description()
        .setLabel(label)
        .setTitle(Strings.emptyToNull(titlePattern.replace(params)))
        .setVisible(true);
    }
  }

  /**
   * If the merge was attempted and it failed the system usually writes a
   * comment as a ChangeMessage and sets status to NEW. Find the relevant
   * message and return it.
   */
  public ChangeMessage getConflictMessage(RevisionResource rsrc)
      throws OrmException {
    return FluentIterable.from(cmUtil.byPatchSet(dbProvider.get(), rsrc.getNotes(),
        rsrc.getPatchSet().getId()))
        .filter(new Predicate<ChangeMessage>() {
          @Override
          public boolean apply(ChangeMessage input) {
            return input.getAuthor() == null;
          }
        })
        .last()
        .orNull();
  }

  private Change submitToDatabase(ReviewDb db, Change.Id changeId,
      final Timestamp timestamp) throws OrmException {
    return db.changes().atomicUpdate(changeId,
      new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus().isOpen()) {
            change.setStatus(Change.Status.SUBMITTED);
            change.setLastUpdatedOn(timestamp);
            return change;
          }
          return null;
        }
      });
  }

  private Change submitThisChange(RevisionResource rsrc, IdentifiedUser caller,
      boolean force) throws ResourceConflictException, OrmException,
      IOException {
    ReviewDb db = dbProvider.get();
    ChangeData cd = changeDataFactory.create(db, rsrc.getControl());
    List<SubmitRecord> submitRecords = checkSubmitRule(cd,
        rsrc.getPatchSet(), force);

    final Timestamp timestamp = TimeUtil.nowTs();
    Change change = rsrc.getChange();
    ChangeUpdate update = updateFactory.create(rsrc.getControl(), timestamp);
    update.submit(submitRecords);

    db.changes().beginTransaction(change.getId());
    try {
      BatchMetaDataUpdate batch = approve(rsrc, update, caller, timestamp);
      // Write update commit after all normalized label commits.
      batch.write(update, new CommitBuilder());
      change = submitToDatabase(db, change.getId(), timestamp);
      if (change == null) {
        return null;
      }
      db.commit();
    } finally {
      db.rollback();
    }
    indexer.index(db, change);
    return change;
  }

  private Change submitWholeTopic(RevisionResource rsrc, IdentifiedUser caller,
      boolean force, String topic) throws ResourceConflictException, OrmException,
      IOException {
    Preconditions.checkNotNull(topic);
    final Timestamp timestamp = TimeUtil.nowTs();

    ReviewDb db = dbProvider.get();
    ChangeData cd = changeDataFactory.create(db, rsrc.getControl());

    List<SubmitRecord> submitRecords = checkSubmitRule(cd,
        rsrc.getPatchSet(), force);

    Change change = rsrc.getChange();
    ChangeUpdate update = updateFactory.create(rsrc.getControl(), timestamp);
    update.submit(submitRecords);

    db.changes().beginTransaction(change.getId());
    List<ChangeData> changesByTopic = queryProvider.get().byTopicOpen(topic);
    try {
      BatchMetaDataUpdate batch = approve(rsrc, update, caller, timestamp);
      // Write update commit after all normalized label commits.
      batch.write(update, new CommitBuilder());

      for (ChangeData c : changesByTopic) {
        if (submitToDatabase(db, c.getId(), timestamp) == null) {
          return null;
        }
      }
      db.commit();
    } finally {
      db.rollback();
    }
    List<Change.Id> ids = new ArrayList<>(changesByTopic.size());
    for (ChangeData c : changesByTopic) {
      ids.add(c.getId());
    }
    indexer.indexAsync(ids).checkedGet();
    return change;
  }

  public Change submit(RevisionResource rsrc, IdentifiedUser caller,
      boolean force) throws ResourceConflictException, OrmException,
      IOException {
    String topic = rsrc.getChange().getTopic();
    if (submitWholeTopic && !Strings.isNullOrEmpty(topic)) {
      return submitWholeTopic(rsrc, caller, force, topic);
    } else {
      return submitThisChange(rsrc, caller, force);
    }
  }

  private BatchMetaDataUpdate approve(RevisionResource rsrc,
      ChangeUpdate update, IdentifiedUser caller, Timestamp timestamp)
      throws OrmException {
    PatchSet.Id psId = rsrc.getPatchSet().getId();
    Map<PatchSetApproval.Key, PatchSetApproval> byKey = Maps.newHashMap();
    for (PatchSetApproval psa :
        approvalsUtil.byPatchSet(dbProvider.get(), rsrc.getControl(), psId)) {
      if (!byKey.containsKey(psa.getKey())) {
        byKey.put(psa.getKey(), psa);
      }
    }

    PatchSetApproval submit = ApprovalsUtil.getSubmitter(psId, byKey.values());
    if (submit == null
        || !submit.getAccountId().equals(caller.getAccountId())) {
      submit = new PatchSetApproval(
          new PatchSetApproval.Key(
              rsrc.getPatchSet().getId(),
              caller.getAccountId(),
              LabelId.SUBMIT),
          (short) 1, TimeUtil.nowTs());
      byKey.put(submit.getKey(), submit);
    }
    submit.setValue((short) 1);
    submit.setGranted(timestamp);

    // Flatten out existing approvals for this patch set based upon the current
    // permissions. Once the change is closed the approvals are not updated at
    // presentation view time, except for zero votes used to indicate a reviewer
    // was added. So we need to make sure votes are accurate now. This way if
    // permissions get modified in the future, historical records stay accurate.
    LabelNormalizer.Result normalized =
        labelNormalizer.normalize(rsrc.getControl(), byKey.values());

    // TODO(dborowitz): Don't use a label in notedb; just check when status
    // change happened.
    update.putApproval(submit.getLabel(), submit.getValue());

    dbProvider.get().patchSetApprovals().upsert(normalized.getNormalized());
    dbProvider.get().patchSetApprovals().delete(normalized.deleted());

    try {
      return saveToBatch(rsrc, update, normalized, timestamp);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private BatchMetaDataUpdate saveToBatch(RevisionResource rsrc,
      ChangeUpdate callerUpdate, LabelNormalizer.Result normalized,
      Timestamp timestamp) throws IOException {
    Table<Account.Id, String, Optional<Short>> byUser = HashBasedTable.create();
    for (PatchSetApproval psa : normalized.updated()) {
      byUser.put(psa.getAccountId(), psa.getLabel(),
          Optional.of(psa.getValue()));
    }
    for (PatchSetApproval psa : normalized.deleted()) {
      byUser.put(psa.getAccountId(), psa.getLabel(), Optional.<Short> absent());
    }

    ChangeControl ctl = rsrc.getControl();
    BatchMetaDataUpdate batch = callerUpdate.openUpdate();
    for (Account.Id accountId : byUser.rowKeySet()) {
      if (!accountId.equals(callerUpdate.getUser().getAccountId())) {
        ChangeUpdate update = updateFactory.create(
            ctl.forUser(userFactory.create(dbProvider, accountId)), timestamp);
        update.setSubject("Finalize approvals at submit");
        putApprovals(update, byUser.row(accountId));

        CommitBuilder commit = new CommitBuilder();
        commit.setCommitter(new PersonIdent(serverIdent, timestamp));
        batch.write(update, commit);
      }
    }

    putApprovals(callerUpdate,
        byUser.row(callerUpdate.getUser().getAccountId()));
    return batch;
  }

  private static void putApprovals(ChangeUpdate update,
      Map<String, Optional<Short>> approvals) {
    for (Map.Entry<String, Optional<Short>> e : approvals.entrySet()) {
      if (e.getValue().isPresent()) {
        update.putApproval(e.getKey(), e.getValue().get());
      } else {
        update.removeApproval(e.getKey());
      }
    }
  }

  private List<SubmitRecord> checkSubmitRule(ChangeData cd,
      PatchSet patchSet, boolean force)
          throws ResourceConflictException, OrmException {
    List<SubmitRecord> results = new SubmitRuleEvaluator(cd)
        .setPatchSet(patchSet)
        .canSubmit();
    Optional<SubmitRecord> ok = findOkRecord(results);
    if (ok.isPresent()) {
      // Rules supplied a valid solution.
      return ImmutableList.of(ok.get());
    } else if (force) {
      return results;
    } else if (results.isEmpty()) {
      throw new IllegalStateException(String.format(
          "ChangeControl.canSubmit returned empty list for %s in %s",
          patchSet.getId(),
          cd.change().getProject().get()));
    }

    for (SubmitRecord record : results) {
      switch (record.status) {
        case CLOSED:
          throw new ResourceConflictException("change is closed");

        case RULE_ERROR:
          throw new ResourceConflictException(String.format(
              "rule error: %s",
              record.errorMessage));

        case NOT_READY:
          StringBuilder msg = new StringBuilder();
          for (SubmitRecord.Label lbl : record.labels) {
            switch (lbl.status) {
              case OK:
              case MAY:
                continue;

              case REJECT:
                if (msg.length() > 0) msg.append("; ");
                msg.append("blocked by ").append(lbl.label);
                continue;

              case NEED:
                if (msg.length() > 0) msg.append("; ");
                msg.append("needs ").append(lbl.label);
                continue;

              case IMPOSSIBLE:
                if (msg.length() > 0) msg.append("; ");
                msg.append("needs ").append(lbl.label)
                   .append(" (check project access)");
                continue;

              default:
                throw new IllegalStateException(String.format(
                    "Unsupported SubmitRecord.Label %s for %s in %s",
                    lbl.toString(),
                    patchSet.getId(),
                    cd.change().getProject().get()));
            }
          }
          throw new ResourceConflictException(msg.toString());

        default:
          throw new IllegalStateException(String.format(
              "Unsupported SubmitRecord %s for %s in %s",
              record,
              patchSet.getId().getId(),
              cd.change().getProject().get()));
      }
    }
    throw new IllegalStateException();
  }

  private static Optional<SubmitRecord> findOkRecord(Collection<SubmitRecord> in) {
    return Iterables.tryFind(in, new Predicate<SubmitRecord>() {
      @Override
      public boolean apply(SubmitRecord input) {
        return input.status == OK;
      }
    });
  }

  static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }

  private RevisionResource onBehalfOf(RevisionResource rsrc, SubmitInput in)
      throws AuthException, UnprocessableEntityException, OrmException {
    ChangeControl caller = rsrc.getControl();
    if (!caller.canSubmit()) {
      throw new AuthException("submit not permitted");
    }
    if (!caller.canSubmitAs()) {
      throw new AuthException("submit on behalf of not permitted");
    }
    IdentifiedUser targetUser = accounts.parseId(in.onBehalfOf);
    if (targetUser == null) {
      throw new UnprocessableEntityException(String.format(
          "Account Not Found: %s", in.onBehalfOf));
    }
    ChangeControl target = caller.forUser(targetUser);
    if (!target.getRefControl().isVisible()) {
      throw new UnprocessableEntityException(String.format(
          "on_behalf_of account %s cannot see destination ref",
          targetUser.getAccountId()));
    }
    return new RevisionResource(changes.parse(target), rsrc.getPatchSet());
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, SubmitInput> {
    private final Provider<ReviewDb> dbProvider;
    private final Submit submit;
    private final ChangeJson json;

    @Inject
    CurrentRevision(Provider<ReviewDb> dbProvider,
        Submit submit,
        ChangeJson json) {
      this.dbProvider = dbProvider;
      this.submit = submit;
      this.json = json;
    }

    @Override
    public ChangeInfo apply(ChangeResource rsrc, SubmitInput input)
        throws AuthException, ResourceConflictException,
        RepositoryNotFoundException, IOException, OrmException,
        UnprocessableEntityException {
      PatchSet ps = dbProvider.get().patchSets()
        .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      Output out = submit.apply(new RevisionResource(rsrc, ps), input);
      return json.format(out.change);
    }
  }
}
