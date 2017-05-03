// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.Change.INITIAL_PATCH_SET_ID;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.SendEmailExecutor;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.InsertChangeOp;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeInserter implements InsertChangeOp {
  public interface Factory {
    ChangeInserter create(Change.Id cid, ObjectId commitId, String refName);
  }

  private static final Logger log = LoggerFactory.getLogger(ChangeInserter.class);

  private final PermissionBackend permissionBackend;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final ExecutorService sendEmailExecutor;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final NotesMigration migration;

  private final Change.Id changeId;
  private final PatchSet.Id psId;
  private final ObjectId commitId;
  private final String refName;

  // Fields exposed as setters.
  private Change.Status status;
  private String topic;
  private String message;
  private String patchSetDescription;
  private boolean isPrivate;
  private boolean workInProgress;
  private List<String> groups = Collections.emptyList();
  private boolean validate = true;
  private NotifyHandling notify = NotifyHandling.ALL;
  private ListMultimap<RecipientType, Account.Id> accountsToNotify = ImmutableListMultimap.of();
  private Set<Account.Id> reviewers;
  private Set<Account.Id> extraCC;
  private Map<String, Short> approvals;
  private RequestScopePropagator requestScopePropagator;
  private boolean fireRevisionCreated;
  private boolean sendMail;
  private boolean updateRef;

  // Fields set during the insertion process.
  private ReceiveCommand cmd;
  private Change change;
  private ChangeMessage changeMessage;
  private PatchSetInfo patchSetInfo;
  private PatchSet patchSet;
  private String pushCert;

  @Inject
  ChangeInserter(
      PermissionBackend permissionBackend,
      ProjectControl.GenericFactory projectControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeControl.GenericFactory changeControlFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CreateChangeSender.Factory createChangeSenderFactory,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      CommitValidators.Factory commitValidatorsFactory,
      CommentAdded commentAdded,
      RevisionCreated revisionCreated,
      NotesMigration migration,
      @Assisted Change.Id changeId,
      @Assisted ObjectId commitId,
      @Assisted String refName) {
    this.permissionBackend = permissionBackend;
    this.projectControlFactory = projectControlFactory;
    this.userFactory = userFactory;
    this.changeControlFactory = changeControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.sendEmailExecutor = sendEmailExecutor;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.migration = migration;

    this.changeId = changeId;
    this.psId = new PatchSet.Id(changeId, INITIAL_PATCH_SET_ID);
    this.commitId = commitId.copy();
    this.refName = refName;
    this.reviewers = Collections.emptySet();
    this.extraCC = Collections.emptySet();
    this.approvals = Collections.emptyMap();
    this.fireRevisionCreated = true;
    this.sendMail = true;
    this.updateRef = true;
  }

  @Override
  public Change createChange(Context ctx) throws IOException {
    change =
        new Change(
            getChangeKey(ctx.getRevWalk(), commitId),
            changeId,
            ctx.getAccountId(),
            new Branch.NameKey(ctx.getProject(), refName),
            ctx.getWhen());
    change.setStatus(MoreObjects.firstNonNull(status, Change.Status.NEW));
    change.setTopic(topic);
    change.setPrivate(isPrivate);
    change.setWorkInProgress(workInProgress);
    change.setReviewStarted(!workInProgress);
    return change;
  }

  private static Change.Key getChangeKey(RevWalk rw, ObjectId id) throws IOException {
    RevCommit commit = rw.parseCommit(id);
    rw.parseBody(commit);
    List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
    if (!idList.isEmpty()) {
      return new Change.Key(idList.get(idList.size() - 1).trim());
    }
    ObjectId changeId =
        ChangeIdUtil.computeChangeId(
            commit.getTree(),
            commit,
            commit.getAuthorIdent(),
            commit.getCommitterIdent(),
            commit.getShortMessage());
    StringBuilder changeIdStr = new StringBuilder();
    changeIdStr.append("I").append(ObjectId.toString(changeId));
    return new Change.Key(changeIdStr.toString());
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public ObjectId getCommitId() {
    return commitId;
  }

  public Change getChange() {
    checkState(change != null, "getChange() only valid after creating change");
    return change;
  }

  public ChangeInserter setTopic(String topic) {
    checkState(change == null, "setTopic(String) only valid before creating change");
    this.topic = topic;
    return this;
  }

  public ChangeInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  public ChangeInserter setPatchSetDescription(String patchSetDescription) {
    this.patchSetDescription = patchSetDescription;
    return this;
  }

  public ChangeInserter setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  public ChangeInserter setNotify(NotifyHandling notify) {
    this.notify = notify;
    return this;
  }

  public ChangeInserter setAccountsToNotify(
      ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.accountsToNotify = checkNotNull(accountsToNotify);
    return this;
  }

  public ChangeInserter setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
    return this;
  }

  public ChangeInserter setExtraCC(Set<Account.Id> extraCC) {
    this.extraCC = extraCC;
    return this;
  }

  public ChangeInserter setPrivate(boolean isPrivate) {
    checkState(change == null, "setPrivate(boolean) only valid before creating change");
    this.isPrivate = isPrivate;
    return this;
  }

  public ChangeInserter setDraft(boolean draft) {
    checkState(change == null, "setDraft(boolean) only valid before creating change");
    return setStatus(draft ? Change.Status.DRAFT : Change.Status.NEW);
  }

  public ChangeInserter setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
    return this;
  }

  public ChangeInserter setStatus(Change.Status status) {
    checkState(change == null, "setStatus(Change.Status) only valid before creating change");
    this.status = status;
    return this;
  }

  public ChangeInserter setGroups(List<String> groups) {
    checkNotNull(groups, "groups may not be empty");
    checkState(patchSet == null, "setGroups(Iterable<String>) only valid before creating change");
    this.groups = groups;
    return this;
  }

  public ChangeInserter setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  public ChangeInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
    return this;
  }

  public ChangeInserter setRequestScopePropagator(RequestScopePropagator r) {
    this.requestScopePropagator = r;
    return this;
  }

  public void setPushCertificate(String cert) {
    pushCert = cert;
  }

  public PatchSet getPatchSet() {
    checkState(patchSet != null, "getPatchSet() only valid after creating change");
    return patchSet;
  }

  public ChangeInserter setApprovals(Map<String, Short> approvals) {
    this.approvals = approvals;
    return this;
  }

  /**
   * Set whether to include the new patch set ref update in this update.
   *
   * <p>If false, the caller is responsible for creating the patch set ref <strong>before</strong>
   * executing the containing {@code BatchUpdate}.
   *
   * <p>Should not be used in new code, as it doesn't result in a single atomic batch ref update for
   * code and NoteDb meta refs.
   *
   * @param updateRef whether to update the ref during {@code updateRepo}.
   */
  @Deprecated
  public ChangeInserter setUpdateRef(boolean updateRef) {
    this.updateRef = updateRef;
    return this;
  }

  public ChangeMessage getChangeMessage() {
    if (message == null) {
      return null;
    }
    checkState(changeMessage != null, "getChangeMessage() only valid after inserting change");
    return changeMessage;
  }

  public ReceiveCommand getCommand() {
    return cmd;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws ResourceConflictException, IOException {
    cmd = new ReceiveCommand(ObjectId.zeroId(), commitId, psId.toRefName());
    validate(ctx);
    if (!updateRef) {
      return;
    }
    ctx.addRefUpdate(cmd);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, OrmException, IOException {
    change = ctx.getChange(); // Use defensive copy created by ChangeControl.
    ReviewDb db = ctx.getDb();
    ChangeControl ctl = ctx.getControl();
    patchSetInfo =
        patchSetInfoFactory.get(ctx.getRevWalk(), ctx.getRevWalk().parseCommit(commitId), psId);
    ctx.getChange().setCurrentPatchSet(patchSetInfo);

    ChangeUpdate update = ctx.getUpdate(psId);
    update.setChangeId(change.getKey().get());
    update.setSubjectForCommit("Create change");
    update.setBranch(change.getDest().get());
    update.setTopic(change.getTopic());
    update.setPsDescription(patchSetDescription);
    update.setPrivate(isPrivate);
    update.setWorkInProgress(workInProgress);

    boolean draft = status == Change.Status.DRAFT;
    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      newGroups = GroupCollector.getDefaultGroups(commitId);
    }
    patchSet =
        psUtil.insert(
            ctx.getDb(),
            ctx.getRevWalk(),
            update,
            psId,
            commitId,
            draft,
            newGroups,
            pushCert,
            patchSetDescription);

    /* TODO: fixStatus is used here because the tests
     * (byStatusClosed() in AbstractQueryChangesTest)
     * insert changes that are already merged,
     * and setStatus may not be used to set the Status to merged
     *
     * is it possible to make the tests use the merge code path,
     * instead of setting the status directly?
     */
    update.fixStatus(change.getStatus());

    Set<Account.Id> reviewersToAdd = new HashSet<>(reviewers);
    if (migration.readChanges()) {
      approvalsUtil.addCcs(
          ctx.getNotes(), update, filterOnChangeVisibility(db, ctx.getNotes(), extraCC));
    } else {
      reviewersToAdd.addAll(extraCC);
    }

    LabelTypes labelTypes = ctl.getProjectControl().getLabelTypes();
    approvalsUtil.addReviewers(
        db,
        update,
        labelTypes,
        change,
        patchSet,
        patchSetInfo,
        filterOnChangeVisibility(db, ctx.getNotes(), reviewersToAdd),
        Collections.<Account.Id>emptySet());
    approvalsUtil.addApprovalsForNewPatchSet(
        db, update, labelTypes, patchSet, ctx.getControl(), approvals);
    // Check if approvals are changing in with this update. If so, add current user to reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as
    // reviewer which is needed in several other code paths.
    if (!approvals.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }
    if (message != null) {
      changeMessage =
          ChangeMessagesUtil.newMessage(
              patchSet.getId(),
              ctx.getUser(),
              patchSet.getCreatedOn(),
              message,
              ChangeMessagesUtil.uploadedPatchSetTag(workInProgress));
      cmUtil.addChangeMessage(db, update, changeMessage);
    }
    return true;
  }

  private Set<Account.Id> filterOnChangeVisibility(
      final ReviewDb db, ChangeNotes notes, Set<Account.Id> accounts) {
    return accounts
        .stream()
        .filter(
            accountId -> {
              try {
                IdentifiedUser user = userFactory.create(accountId);
                return changeControlFactory.controlFor(notes, user).isVisible(db);
              } catch (OrmException e) {
                log.warn(
                    String.format(
                        "Failed to check if account %d can see change %d",
                        accountId.get(), notes.getChangeId().get()),
                    e);
                return false;
              }
            })
        .collect(toSet());
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    if (sendMail && (notify != NotifyHandling.NONE || !accountsToNotify.isEmpty())) {
      Runnable sender =
          new Runnable() {
            @Override
            public void run() {
              try {
                CreateChangeSender cm =
                    createChangeSenderFactory.create(change.getProject(), change.getId());
                cm.setFrom(change.getOwner());
                cm.setPatchSet(patchSet, patchSetInfo);
                cm.setNotify(notify);
                cm.setAccountsToNotify(accountsToNotify);
                cm.addReviewers(reviewers);
                cm.addExtraCC(extraCC);
                cm.send();
              } catch (Exception e) {
                log.error("Cannot send email for new change " + change.getId(), e);
              }
            }

            @Override
            public String toString() {
              return "send-email newchange";
            }
          };
      if (requestScopePropagator != null) {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            sendEmailExecutor.submit(requestScopePropagator.wrap(sender));
      } else {
        sender.run();
      }
    }

    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    if (fireRevisionCreated) {
      revisionCreated.fire(change, patchSet, ctx.getAccount(), ctx.getWhen(), notify);
      if (approvals != null && !approvals.isEmpty()) {
        ChangeControl changeControl =
            changeControlFactory.controlFor(ctx.getDb(), change, ctx.getUser());
        List<LabelType> labels = changeControl.getLabelTypes().getLabelTypes();
        Map<String, Short> allApprovals = new HashMap<>();
        Map<String, Short> oldApprovals = new HashMap<>();
        for (LabelType lt : labels) {
          allApprovals.put(lt.getName(), (short) 0);
          oldApprovals.put(lt.getName(), null);
        }
        for (Map.Entry<String, Short> entry : approvals.entrySet()) {
          if (entry.getValue() != 0) {
            allApprovals.put(entry.getKey(), entry.getValue());
            oldApprovals.put(entry.getKey(), (short) 0);
          }
        }
        commentAdded.fire(
            change, patchSet, ctx.getAccount(), null, allApprovals, oldApprovals, ctx.getWhen());
      }
    }
  }

  private void validate(RepoContext ctx) throws IOException, ResourceConflictException {
    if (!validate) {
      return;
    }

    PermissionBackend.ForRef perm =
        permissionBackend.user(ctx.getUser()).project(ctx.getProject()).ref(refName);
    try {
      RefControl refControl =
          projectControlFactory.controlFor(ctx.getProject(), ctx.getUser()).controlForRef(refName);
      try (CommitReceivedEvent event =
          new CommitReceivedEvent(
              cmd,
              refControl.getProjectControl().getProject(),
              change.getDest().get(),
              ctx.getRevWalk().getObjectReader(),
              commitId,
              ctx.getIdentifiedUser())) {
        commitValidatorsFactory
            .forGerritCommits(perm, refControl, new NoSshInfo(), ctx.getRevWalk())
            .validate(event);
      }
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    } catch (NoSuchProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
