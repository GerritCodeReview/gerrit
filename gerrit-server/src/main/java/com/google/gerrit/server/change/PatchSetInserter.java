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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalCopier;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeKind;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerState;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ChangeModifiedException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

public class PatchSetInserter {
  private static final Logger log =
      LoggerFactory.getLogger(PatchSetInserter.class);

  public static interface Factory {
    PatchSetInserter create(Repository git, RevWalk revWalk, ChangeControl ctl,
        RevCommit commit);
  }

  /**
   * Whether to use {@link CommitValidators#validateForGerritCommits},
   * {@link CommitValidators#validateForReceiveCommits}, or no commit
   * validation.
   */
  public static enum ValidatePolicy {
    GERRIT, RECEIVE_COMMITS, NONE
  }

  private final ChangeHooks hooks;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeControl.GenericFactory ctlFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeIndexer indexer;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ApprovalCopier approvalCopier;
  private final ChangeKindCache changeKindCache;
  private final ChangeMessagesUtil cmUtil;
  private final ProjectCache projectCache;

  private final Repository git;
  private final RevWalk revWalk;
  private final RevCommit commit;
  private final ChangeControl ctl;
  private final IdentifiedUser user;

  private PatchSet patchSet;
  private ChangeMessage changeMessage;
  private SshInfo sshInfo;
  private ValidatePolicy validatePolicy = ValidatePolicy.GERRIT;
  private boolean draft;
  private boolean runHooks;
  private boolean sendMail;
  private Account.Id uploader;

  @Inject
  public PatchSetInserter(ChangeHooks hooks,
      ReviewDb db,
      ChangeUpdate.Factory updateFactory,
      ChangeControl.GenericFactory ctlFactory,
      ApprovalsUtil approvalsUtil,
      ApprovalCopier approvalCopier,
      ChangeKindCache changeKindCache,
      ChangeMessagesUtil cmUtil,
      ProjectCache projectCache,
      PatchSetInfoFactory patchSetInfoFactory,
      GitReferenceUpdated gitRefUpdated,
      CommitValidators.Factory commitValidatorsFactory,
      ChangeIndexer indexer,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      @Assisted Repository git,
      @Assisted RevWalk revWalk,
      @Assisted ChangeControl ctl,
      @Assisted RevCommit commit) {
    checkArgument(ctl.getCurrentUser().isIdentifiedUser(),
        "only IdentifiedUser may create patch set on change %s",
        ctl.getChange().getId());
    this.hooks = hooks;
    this.db = db;
    this.updateFactory = updateFactory;
    this.ctlFactory = ctlFactory;
    this.approvalsUtil = approvalsUtil;
    this.approvalCopier = approvalCopier;
    this.changeKindCache = changeKindCache;
    this.cmUtil = cmUtil;
    this.projectCache = projectCache;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.indexer = indexer;
    this.replacePatchSetFactory = replacePatchSetFactory;

    this.git = git;
    this.revWalk = revWalk;
    this.commit = commit;
    this.ctl = ctl;
    this.user = (IdentifiedUser) ctl.getCurrentUser();
    this.runHooks = true;
    this.sendMail = true;
  }

  public PatchSetInserter setPatchSet(PatchSet patchSet) {
    Change c = ctl.getChange();
    PatchSet.Id psid = patchSet.getId();
    checkArgument(psid.getParentKey().equals(c.getId()),
        "patch set %s not for change %s", psid, c.getId());
    checkArgument(psid.get() > c.currentPatchSetId().get(),
        "new patch set ID %s is not greater than current patch set ID %s",
        psid.get(), c.currentPatchSetId().get());
    this.patchSet = patchSet;
    return this;
  }

  public PatchSet.Id getPatchSetId() throws IOException {
    init();
    return patchSet.getId();
  }

  public PatchSetInserter setMessage(String message) throws OrmException {
    changeMessage = new ChangeMessage(
        new ChangeMessage.Key(
            ctl.getChange().getId(), ChangeUtil.messageUUID(db)),
        user.getAccountId(), TimeUtil.nowTs(), patchSet.getId());
    changeMessage.setMessage(message);
    return this;
  }

  public PatchSetInserter setMessage(ChangeMessage changeMessage) {
    this.changeMessage = changeMessage;
    return this;
  }

  public PatchSetInserter setSshInfo(SshInfo sshInfo) {
    this.sshInfo = sshInfo;
    return this;
  }

  public PatchSetInserter setValidatePolicy(ValidatePolicy validate) {
    this.validatePolicy = checkNotNull(validate);
    return this;
  }

  public PatchSetInserter setDraft(boolean draft) {
    this.draft = draft;
    return this;
  }

  public PatchSetInserter setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
    return this;
  }

  public PatchSetInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
    return this;
  }

  public PatchSetInserter setUploader(Account.Id uploader) {
    this.uploader = uploader;
    return this;
  }

  public Change insert() throws InvalidChangeOperationException, OrmException,
      IOException, NoSuchChangeException {
    init();
    validate();

    Change c = ctl.getChange();
    Change updatedChange;
    RefUpdate ru = git.updateRef(patchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(commit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", patchSet.getRefName(),
          c.getDest().getParentKey().get(), ru.getResult()));
    }
    gitRefUpdated.fire(c.getProject(), ru);

    final PatchSet.Id currentPatchSetId = c.currentPatchSetId();

    ChangeUpdate update = updateFactory.create(ctl, patchSet.getCreatedOn());

    db.changes().beginTransaction(c.getId());
    try {
      if (!db.changes().get(c.getId()).getStatus().isOpen()) {
        throw new InvalidChangeOperationException(String.format(
            "Change %s is closed", c.getId()));
      }

      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));

      SetMultimap<ReviewerState, Account.Id> oldReviewers = sendMail
          ? approvalsUtil.getReviewers(db, ctl.getNotes())
          : null;

      updatedChange =
          db.changes().atomicUpdate(c.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.getStatus().isClosed()) {
                return null;
              }
              if (!change.currentPatchSetId().equals(currentPatchSetId)) {
                return null;
              }
              if (change.getStatus() != Change.Status.DRAFT) {
                change.setStatus(Change.Status.NEW);
              }
              change.setCurrentPatchSet(patchSetInfoFactory.get(commit,
                  patchSet.getId()));
              ChangeUtil.updated(change);
              return change;
            }
          });
      if (updatedChange == null) {
        throw new ChangeModifiedException(String.format(
            "Change %s was modified", c.getId()));
      }

      if (messageIsForChange()) {
        cmUtil.addChangeMessage(db, update, changeMessage);
      }

      approvalCopier.copy(db, ctl, patchSet);

      ProjectState project =
          projectCache.checkedGet(c.getDest().getParentKey());
      final PatchSet currentPatchSet = db.patchSets().get(currentPatchSetId);
      ChangeKind changeKind = changeKindCache.getChangeKind(
          project, git,
          ObjectId.fromString(currentPatchSet.getRevision().get()),
          ObjectId.fromString(patchSet.getRevision().get()));
      if (changeKind != ChangeKind.REWORK) {
        sendMail = false;
      }

      db.commit();
      if (messageIsForChange()) {
        update.commit();
      }

      if (!messageIsForChange()) {
        commitMessageNotForChange(updatedChange);
      }

      if (sendMail) {
        try {
          PatchSetInfo info = patchSetInfoFactory.get(commit, patchSet.getId());
          ReplacePatchSetSender cm =
              replacePatchSetFactory.create(updatedChange);
          cm.setFrom(user.getAccountId());
          cm.setPatchSet(patchSet, info);
          cm.setChangeMessage(changeMessage);
          cm.addReviewers(oldReviewers.get(ReviewerState.REVIEWER));
          cm.addExtraCC(oldReviewers.get(ReviewerState.CC));
          cm.send();
        } catch (Exception err) {
          log.error("Cannot send email for new patch set on change "
              + updatedChange.getId(), err);
        }
      }

    } finally {
      db.rollback();
    }
    indexer.index(db, updatedChange);
    if (runHooks) {
      hooks.doPatchsetCreatedHook(updatedChange, patchSet, db);
    }
    return updatedChange;
  }

  private void commitMessageNotForChange(Change updatedChange)
      throws OrmException, NoSuchChangeException, IOException {
    if (changeMessage != null) {
      Change otherChange =
          db.changes().get(changeMessage.getPatchSetId().getParentKey());
      ChangeControl otherControl =
          ctlFactory.controlFor(otherChange, user);
      ChangeUpdate updateForOtherChange =
          updateFactory.create(otherControl, updatedChange.getLastUpdatedOn());
      cmUtil.addChangeMessage(db, updateForOtherChange, changeMessage);
      updateForOtherChange.commit();
    }
  }

  private void init() throws IOException {
    if (sshInfo == null) {
      sshInfo = new NoSshInfo();
    }
    if (patchSet == null) {
      patchSet = new PatchSet(
          ChangeUtil.nextPatchSetId(git, ctl.getChange().currentPatchSetId()));
      patchSet.setCreatedOn(TimeUtil.nowTs());
      patchSet.setUploader(ctl.getChange().getOwner());
      patchSet.setRevision(new RevId(commit.name()));
    }
    patchSet.setDraft(draft);
    if (uploader != null) {
      patchSet.setUploader(uploader);
    }
  }

  private void validate() throws InvalidChangeOperationException, IOException {
    CommitValidators cv =
        commitValidatorsFactory.create(ctl.getRefControl(), sshInfo, git);

    String refName = patchSet.getRefName();
    CommitReceivedEvent event = new CommitReceivedEvent(
        new ReceiveCommand(
            ObjectId.zeroId(),
            commit.getId(),
            refName.substring(0, refName.lastIndexOf('/') + 1) + "new"),
        ctl.getProjectControl().getProject(), ctl.getRefControl().getRefName(),
        commit, user);

    try {
      switch (validatePolicy) {
      case RECEIVE_COMMITS:
        NoteMap rejectCommits = BanCommit.loadRejectCommitsMap(git, revWalk);
        cv.validateForReceiveCommits(event, rejectCommits);
        break;
      case GERRIT:
        cv.validateForGerritCommits(event);
        break;
      case NONE:
        break;
      }
    } catch (CommitValidationException e) {
      throw new InvalidChangeOperationException(e.getMessage());
    }
  }

  private boolean messageIsForChange() {
    return changeMessage != null && changeMessage.getKey().getParentKey()
        .equals(patchSet.getId().getParentKey());
  }
}
