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

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PatchSetInserter {
  private static final Logger log =
      LoggerFactory.getLogger(PatchSetInserter.class);

  public static interface Factory {
    PatchSetInserter create(Repository git, RevWalk revWalk, RefControl refControl,
        IdentifiedUser user, Change change, RevCommit commit);
  }

  /**
   * Whether to use {@link CommitValidators#validateForGerritCommits},
   * {@link CommitValidators#validateForReceiveCommits}, or no commit
   * validation.
   */
  public static enum ValidatePolicy {
    GERRIT, RECEIVE_COMMITS, NONE;
  }

  public static enum ChangeKind {
    REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE;
  }

  private final ChangeHooks hooks;
  private final TrackingFooters trackingFooters;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final IdentifiedUser user;
  private final GitReferenceUpdated gitRefUpdated;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeIndexer indexer;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final MergeUtil.Factory mergeUtilFactory;

  private final Repository git;
  private final RevWalk revWalk;
  private final RevCommit commit;
  private final Change change;
  private final RefControl refControl;

  private PatchSet patchSet;
  private ChangeMessage changeMessage;
  private boolean copyLabels;
  private SshInfo sshInfo;
  private ValidatePolicy validatePolicy = ValidatePolicy.GERRIT;
  private boolean draft;
  private boolean runHooks;
  private boolean sendMail;
  private Account.Id uploader;

  @Inject
  public PatchSetInserter(ChangeHooks hooks,
      TrackingFooters trackingFooters,
      ReviewDb db,
      PatchSetInfoFactory patchSetInfoFactory,
      GitReferenceUpdated gitRefUpdated,
      CommitValidators.Factory commitValidatorsFactory,
      ChangeIndexer indexer,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      MergeUtil.Factory mergeUtilFactory,
      @Assisted Repository git,
      @Assisted RevWalk revWalk,
      @Assisted RefControl refControl,
      @Assisted IdentifiedUser user,
      @Assisted Change change,
      @Assisted RevCommit commit) {
    this.hooks = hooks;
    this.trackingFooters = trackingFooters;
    this.db = db;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.user = user;
    this.gitRefUpdated = gitRefUpdated;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.indexer = indexer;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.mergeUtilFactory = mergeUtilFactory;

    this.git = git;
    this.revWalk = revWalk;
    this.refControl = refControl;
    this.change = change;
    this.commit = commit;
    this.runHooks = true;
    this.sendMail = true;
  }

  public PatchSetInserter setPatchSet(PatchSet patchSet) {
    PatchSet.Id psid = patchSet.getId();
    checkArgument(psid.getParentKey().equals(change.getId()),
        "patch set %s not for change %s", psid, change.getId());
    checkArgument(psid.get() > change.currentPatchSetId().get(),
        "new patch set ID %s is not greater than current patch set ID %s",
        psid.get(), change.currentPatchSetId().get());
    this.patchSet = patchSet;
    return this;
  }

  public PatchSet.Id getPatchSetId() throws IOException {
    init();
    return patchSet.getId();
  }

  public PatchSetInserter setMessage(String message) throws OrmException {
    changeMessage = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        user.getAccountId(), TimeUtil.nowTs(), patchSet.getId());
    changeMessage.setMessage(message);
    return this;
  }

  public PatchSetInserter setMessage(ChangeMessage changeMessage) throws OrmException {
    this.changeMessage = changeMessage;
    return this;
  }

  public PatchSetInserter setCopyLabels(boolean copyLabels) {
    this.copyLabels = copyLabels;
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
      IOException {
    init();
    validate();

    Change updatedChange;
    RefUpdate ru = git.updateRef(patchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(commit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", patchSet.getRefName(),
          change.getDest().getParentKey().get(), ru.getResult()));
    }
    gitRefUpdated.fire(change.getProject(), ru);

    final PatchSet.Id currentPatchSetId = change.currentPatchSetId();

    db.changes().beginTransaction(change.getId());
    try {
      if (!db.changes().get(change.getId()).getStatus().isOpen()) {
        throw new InvalidChangeOperationException(String.format(
            "Change %s is closed", change.getId()));
      }

      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));

      final List<PatchSetApproval> oldPatchSetApprovals =
          db.patchSetApprovals().byChange(change.getId()).toList();
      final Set<Account.Id> oldReviewers = Sets.newHashSet();
      final Set<Account.Id> oldCC = Sets.newHashSet();
      for (PatchSetApproval a : oldPatchSetApprovals) {
        if (a.getValue() != 0) {
          oldReviewers.add(a.getAccountId());
        } else {
          oldCC.add(a.getAccountId());
        }
      }

      updatedChange =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
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
              change.setLastSha1MergeTested(null);
              change.setCurrentPatchSet(patchSetInfoFactory.get(commit,
                  patchSet.getId()));
              ChangeUtil.updated(change);
              return change;
            }
          });
      if (updatedChange == null) {
        throw new ChangeModifiedException(String.format(
            "Change %s was modified", change.getId()));
      }

      if (copyLabels) {
        PatchSet priorPatchSet = db.patchSets().get(currentPatchSetId);
        ObjectId priorCommitId = ObjectId.fromString(priorPatchSet.getRevision().get());
        RevCommit priorCommit = revWalk.parseCommit(priorCommitId);
        ProjectState projectState =
            refControl.getProjectControl().getProjectState();
        ChangeKind changeKind =
            getChangeKind(mergeUtilFactory, projectState, git, priorCommit, commit);

        ApprovalsUtil.copyLabels(db, refControl.getProjectControl()
            .getLabelTypes(), currentPatchSetId, patchSet, changeKind);
      }

      final List<FooterLine> footerLines = commit.getFooterLines();
      ChangeUtil.updateTrackingIds(db, updatedChange, trackingFooters, footerLines);
      db.commit();

      if (changeMessage != null) {
        db.changeMessages().insert(Collections.singleton(changeMessage));
      }

      if (sendMail) {
        try {
          PatchSetInfo info = patchSetInfoFactory.get(commit, patchSet.getId());
          ReplacePatchSetSender cm =
              replacePatchSetFactory.create(updatedChange);
          cm.setFrom(user.getAccountId());
          cm.setPatchSet(patchSet, info);
          cm.setChangeMessage(changeMessage);
          cm.addReviewers(oldReviewers);
          cm.addExtraCC(oldCC);
          cm.send();
        } catch (Exception err) {
          log.error("Cannot send email for new patch set on change " + updatedChange.getId(),
              err);
        }
      }

    } finally {
      db.rollback();
    }
    CheckedFuture<?, IOException> e = indexer.indexAsync(updatedChange);
    if (runHooks) {
      hooks.doPatchsetCreatedHook(updatedChange, patchSet, db);
    }
    e.checkedGet();
    return updatedChange;
  }

  private void init() throws IOException {
    if (sshInfo == null) {
      sshInfo = new NoSshInfo();
    }
    if (patchSet == null) {
      patchSet = new PatchSet(
          ChangeUtil.nextPatchSetId(git, change.currentPatchSetId()));
      patchSet.setCreatedOn(TimeUtil.nowTs());
      patchSet.setUploader(change.getOwner());
      patchSet.setRevision(new RevId(commit.name()));
    }
    patchSet.setDraft(draft);
    if (uploader != null) {
      patchSet.setUploader(uploader);
    }
  }

  private void validate() throws InvalidChangeOperationException {
    CommitValidators cv = commitValidatorsFactory.create(refControl, sshInfo, git);

    String refName = patchSet.getRefName();
    CommitReceivedEvent event = new CommitReceivedEvent(
        new ReceiveCommand(
            ObjectId.zeroId(),
            commit.getId(),
            refName.substring(0, refName.lastIndexOf('/') + 1) + "new"),
        refControl.getProjectControl().getProject(), refControl.getRefName(),
        commit, user);

    try {
      switch (validatePolicy) {
      case RECEIVE_COMMITS:
        cv.validateForReceiveCommits(event);
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

  public static ChangeKind getChangeKind(MergeUtil.Factory mergeUtilFactory, ProjectState project,
      Repository git, RevCommit prior, RevCommit next) {
    if (!next.getFullMessage().equals(prior.getFullMessage())) {
      if (next.getTree() == prior.getTree() && isSameParents(prior, next)) {
        return ChangeKind.NO_CODE_CHANGE;
      } else {
        return ChangeKind.REWORK;
      }
    }

    if (prior.getParentCount() != 1 || next.getParentCount() != 1) {
      // Trivial rebases done by machine only work well on 1 parent.
      return ChangeKind.REWORK;
    }

    if (next.getTree() == prior.getTree() &&
       isSameParents(prior, next)) {
      return ChangeKind.TRIVIAL_REBASE;
    }

    // A trivial rebase can be detected by looking for the next commit
    // having the same tree as would exist when the prior commit is
    // cherry-picked onto the next commit's new first parent.
    try {
      MergeUtil mergeUtil = mergeUtilFactory.create(project);
      ThreeWayMerger merger =
          mergeUtil.newThreeWayMerger(git, mergeUtil.createDryRunInserter());
      merger.setBase(prior.getParent(0));
      if (merger.merge(next.getParent(0), prior)
          && merger.getResultTreeId().equals(next.getTree())) {
        return ChangeKind.TRIVIAL_REBASE;
      } else {
        return ChangeKind.REWORK;
      }
    } catch (IOException err) {
      log.warn("Cannot check trivial rebase of new patch set " + next.name()
          + " in " + project.getProject().getName(), err);
      return ChangeKind.REWORK;
    }
  }

  private static boolean isSameParents(RevCommit prior, RevCommit next) {
    if (prior.getParentCount() != next.getParentCount()) {
      return false;
    } else if (prior.getParentCount() == 0) {
      return true;
    }
    return prior.getParent(0).equals(next.getParent(0));
  }

  public class ChangeModifiedException extends InvalidChangeOperationException {
    private static final long serialVersionUID = 1L;

    public ChangeModifiedException(String msg) {
      super(msg);
    }
  }
}
