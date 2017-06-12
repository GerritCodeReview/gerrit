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
import static java.util.stream.Collectors.toSet;

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
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
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.SendEmailExecutor;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeInserter extends BatchUpdate.InsertChangeOp {
  public interface Factory {
    ChangeInserter create(Change.Id cid, RevCommit rc, String refName);
  }

  private static final Logger log = LoggerFactory.getLogger(ChangeInserter.class);

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

  private final Change.Id changeId;
  private final PatchSet.Id psId;
  private final RevCommit commit;
  private final String refName;

  // Fields exposed as setters.
  private Change.Status status;
  private String topic;
  private String message;
  private List<String> groups = Collections.emptyList();
  private CommitValidators.Policy validatePolicy = CommitValidators.Policy.GERRIT;
  private NotifyHandling notify = NotifyHandling.ALL;
  private Set<Account.Id> reviewers;
  private Set<Account.Id> extraCC;
  private Map<String, Short> approvals;
  private RequestScopePropagator requestScopePropagator;
  private ReceiveCommand updateRefCommand;
  private boolean fireRevisionCreated;
  private boolean sendMail;
  private boolean updateRef;

  // Fields set during the insertion process.
  private Change change;
  private ChangeMessage changeMessage;
  private PatchSetInfo patchSetInfo;
  private PatchSet patchSet;
  private String pushCert;

  @Inject
  ChangeInserter(
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
      @Assisted Change.Id changeId,
      @Assisted RevCommit commit,
      @Assisted String refName) {
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

    this.changeId = changeId;
    this.psId = new PatchSet.Id(changeId, INITIAL_PATCH_SET_ID);
    this.commit = commit;
    this.refName = refName;
    this.reviewers = Collections.emptySet();
    this.extraCC = Collections.emptySet();
    this.approvals = Collections.emptyMap();
    this.updateRefCommand = null;
    this.fireRevisionCreated = true;
    this.sendMail = true;
    this.updateRef = true;
  }

  @Override
  public Change createChange(Context ctx) {
    change =
        new Change(
            getChangeKey(commit),
            changeId,
            ctx.getAccountId(),
            new Branch.NameKey(ctx.getProject(), refName),
            ctx.getWhen());
    change.setStatus(MoreObjects.firstNonNull(status, Change.Status.NEW));
    change.setTopic(topic);
    return change;
  }

  private static Change.Key getChangeKey(RevCommit commit) {
    List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
    if (!idList.isEmpty()) {
      return new Change.Key(idList.get(idList.size() - 1).trim());
    }
    ObjectId id =
        ChangeIdUtil.computeChangeId(
            commit.getTree(),
            commit,
            commit.getAuthorIdent(),
            commit.getCommitterIdent(),
            commit.getShortMessage());
    StringBuilder changeId = new StringBuilder();
    changeId.append("I").append(ObjectId.toString(id));
    return new Change.Key(changeId.toString());
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public RevCommit getCommit() {
    return commit;
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

  public ChangeInserter setValidatePolicy(CommitValidators.Policy validate) {
    this.validatePolicy = checkNotNull(validate);
    return this;
  }

  public ChangeInserter setNotify(NotifyHandling notify) {
    this.notify = notify;
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

  public ChangeInserter setDraft(boolean draft) {
    checkState(change == null, "setDraft(boolean) only valid before creating change");
    return setStatus(draft ? Change.Status.DRAFT : Change.Status.NEW);
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

  public void setUpdateRefCommand(ReceiveCommand cmd) {
    updateRefCommand = cmd;
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

  @Override
  public void updateRepo(RepoContext ctx) throws ResourceConflictException, IOException {
    validate(ctx);
    if (!updateRef) {
      return;
    }
    if (updateRefCommand == null) {
      ctx.addRefUpdate(new ReceiveCommand(ObjectId.zeroId(), commit, psId.toRefName()));
    } else {
      ctx.addRefUpdate(updateRefCommand);
    }
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException, IOException {
    change = ctx.getChange(); // Use defensive copy created by ChangeControl.
    ReviewDb db = ctx.getDb();
    ChangeControl ctl = ctx.getControl();
    patchSetInfo = patchSetInfoFactory.get(ctx.getRevWalk(), commit, psId);
    ctx.getChange().setCurrentPatchSet(patchSetInfo);

    ChangeUpdate update = ctx.getUpdate(psId);
    update.setChangeId(change.getKey().get());
    update.setSubjectForCommit("Create change");
    update.setBranch(change.getDest().get());
    update.setTopic(change.getTopic());

    boolean draft = status == Change.Status.DRAFT;
    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      newGroups = GroupCollector.getDefaultGroups(commit);
    }
    patchSet =
        psUtil.insert(
            ctx.getDb(), ctx.getRevWalk(), update, psId, commit, draft, newGroups, pushCert);

    /* TODO: fixStatus is used here because the tests
     * (byStatusClosed() in AbstractQueryChangesTest)
     * insert changes that are already merged,
     * and setStatus may not be used to set the Status to merged
     *
     * is it possible to make the tests use the merge code path,
     * instead of setting the status directly?
     */
    update.fixStatus(change.getStatus());

    LabelTypes labelTypes = ctl.getProjectControl().getLabelTypes();
    approvalsUtil.addReviewers(
        db,
        update,
        labelTypes,
        change,
        patchSet,
        patchSetInfo,
        filterOnChangeVisibility(db, ctx.getNotes(), reviewers),
        Collections.<Account.Id>emptySet());
    approvalsUtil.addApprovalsForNewPatchSet(
        db, update, labelTypes, patchSet, ctx.getControl(), approvals);
    if (message != null) {
      changeMessage =
          ChangeMessagesUtil.newMessage(
              db,
              patchSet.getId(),
              ctx.getUser(),
              patchSet.getCreatedOn(),
              message,
              ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);
      cmUtil.addChangeMessage(db, update, changeMessage);
    }
    return true;
  }

  private Set<Account.Id> filterOnChangeVisibility(
      final ReviewDb db, final ChangeNotes notes, Set<Account.Id> accounts) {
    return accounts
        .stream()
        .filter(
            accountId -> {
              try {
                IdentifiedUser user = userFactory.create(accountId);
                return changeControlFactory.controlFor(notes, user).isVisible(db);
              } catch (OrmException | NoSuchChangeException e) {
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
  public void postUpdate(Context ctx) throws OrmException, NoSuchChangeException {
    if (sendMail) {
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
    if (validatePolicy == CommitValidators.Policy.NONE) {
      return;
    }

    try {
      RefControl refControl =
          projectControlFactory.controlFor(ctx.getProject(), ctx.getUser()).controlForRef(refName);
      String refName = psId.toRefName();
      CommitReceivedEvent event =
          new CommitReceivedEvent(
              new ReceiveCommand(ObjectId.zeroId(), commit.getId(), refName),
              refControl.getProjectControl().getProject(),
              change.getDest().get(),
              commit,
              ctx.getIdentifiedUser());
      commitValidatorsFactory
          .create(validatePolicy, refControl, new NoSshInfo(), ctx.getRepository())
          .validate(event);
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    } catch (NoSuchProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
