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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Submit implements RestModifyView<RevisionResource, SubmitInput>,
    UiAction<RevisionResource> {
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
  private final ChangeUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final MergeQueue mergeQueue;
  private final ChangeIndexer indexer;
  private final LabelNormalizer labelNormalizer;
  private final AccountsCollection accounts;
  private final ChangesCollection changes;

  @Inject
  Submit(@GerritPersonIdent PersonIdent serverIdent,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory userFactory,
      ChangeUpdate.Factory updateFactory,
      ApprovalsUtil approvalsUtil,
      MergeQueue mergeQueue,
      AccountsCollection accounts,
      ChangesCollection changes,
      ChangeIndexer indexer,
      LabelNormalizer labelNormalizer) {
    this.serverIdent = serverIdent;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.userFactory = userFactory;
    this.updateFactory = updateFactory;
    this.approvalsUtil = approvalsUtil;
    this.mergeQueue = mergeQueue;
    this.accounts = accounts;
    this.changes = changes;
    this.indexer = indexer;
    this.labelNormalizer = labelNormalizer;
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

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet.Id current = resource.getChange().currentPatchSetId();
    return new UiAction.Description()
      .setTitle(String.format(
          "Merge patch set %d into %s",
          resource.getPatchSet().getPatchSetId(),
          resource.getChange().getDest().getShortName()))
      .setVisible(resource.getChange().getStatus().isOpen()
          && resource.getPatchSet().getId().equals(current)
          && resource.getControl().canSubmit());
  }

  /**
   * If the merge was attempted and it failed the system usually writes a
   * comment as a ChangeMessage and sets status to NEW. Find the relevant
   * message and return it.
   */
  public ChangeMessage getConflictMessage(RevisionResource rsrc)
      throws OrmException {
    return Iterables.getFirst(Iterables.filter(
      Lists.reverse(dbProvider.get().changeMessages()
          .byChange(rsrc.getChange().getId())
          .toList()),
      new Predicate<ChangeMessage>() {
        @Override
        public boolean apply(ChangeMessage input) {
          return input.getAuthor() == null;
        }
      }), null);
  }

  public Change submit(RevisionResource rsrc, IdentifiedUser caller,
      boolean force) throws ResourceConflictException, OrmException,
      IOException {
    List<SubmitRecord> submitRecords = checkSubmitRule(rsrc, force);
    final Timestamp timestamp = TimeUtil.nowTs();
    Change change = rsrc.getChange();
    ChangeUpdate update = updateFactory.create(rsrc.getControl(), timestamp);
    update.submit(submitRecords);

    ReviewDb db = dbProvider.get();
    db.changes().beginTransaction(change.getId());
    try {
      BatchMetaDataUpdate batch = approve(rsrc, update, caller, timestamp);
      // Write update commit after all normalized label commits.
      batch.write(update, new CommitBuilder());

      change = db.changes().atomicUpdate(
        change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.setStatus(Change.Status.SUBMITTED);
              change.setLastUpdatedOn(timestamp);
              ChangeUtil.computeSortKey(change);
              return change;
            }
            return null;
          }
        });
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
    if (submit == null || submit.getAccountId() != caller.getAccountId()) {
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
    dbProvider.get().patchSetApprovals().delete(normalized.getDeleted());

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
    for (PatchSetApproval psa : normalized.getUpdated()) {
      byUser.put(psa.getAccountId(), psa.getLabel(),
          Optional.of(psa.getValue()));
    }
    for (PatchSetApproval psa : normalized.getDeleted()) {
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

  private List<SubmitRecord> checkSubmitRule(RevisionResource rsrc,
      boolean force) throws ResourceConflictException {
    List<SubmitRecord> results = rsrc.getControl().canSubmit(
        dbProvider.get(),
        rsrc.getPatchSet());
    Optional<SubmitRecord> ok = findOkRecord(results);
    if (ok.isPresent()) {
      // Rules supplied a valid solution.
      return ImmutableList.of(ok.get());
    } else if (force) {
      return results;
    } else if (results.isEmpty()) {
      throw new IllegalStateException(String.format(
          "ChangeControl.canSubmit returned empty list for %s in %s",
          rsrc.getPatchSet().getId(),
          rsrc.getChange().getProject().get()));
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
                    rsrc.getPatchSet().getId(),
                    rsrc.getChange().getProject().get()));
            }
          }
          throw new ResourceConflictException(msg.toString());

        default:
          throw new IllegalStateException(String.format(
              "Unsupported SubmitRecord %s for %s in %s",
              record,
              rsrc.getPatchSet().getId(),
              rsrc.getChange().getProject().get()));
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
