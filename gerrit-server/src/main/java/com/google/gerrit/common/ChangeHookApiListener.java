// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.common;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.common.ChangeHookRunner.HookResult;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.AgreementSignupListener;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.TopicEditedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.git.validators.CommitValidationException;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class ChangeHookApiListener implements
    AgreementSignupListener,
    ChangeAbandonedListener,
    ChangeMergedListener,
    ChangeRestoredListener,
    CommentAddedListener,
    DraftPublishedListener,
    GitReferenceUpdatedListener,
    HashtagsEditedListener,
    NewProjectCreatedListener,
    ReviewerAddedListener,
    ReviewerDeletedListener,
    RevisionCreatedListener,
    TopicEditedListener {
  /** A logger for this class. */
  private static final Logger log =
      LoggerFactory.getLogger(ChangeHookApiListener.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), AgreementSignupListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), ChangeAbandonedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), ChangeMergedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), ChangeRestoredListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), CommentAddedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), DraftPublishedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), HashtagsEditedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), NewProjectCreatedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), ReviewerAddedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), ReviewerDeletedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), RevisionCreatedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), TopicEditedListener.class)
        .to(ChangeHookApiListener.class);
      DynamicSet.bind(binder(), CommitValidationListener.class)
        .to(ChangeHookValidator.class);
    }
  }

  /** Reject commits that don't pass user-supplied ref-update hook. */
  public static class ChangeHookValidator implements
      CommitValidationListener {
    private final ChangeHooks hooks;

    @Inject
    public ChangeHookValidator(ChangeHooks hooks) {
      this.hooks = hooks;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser user = receiveEvent.user;
      String refname = receiveEvent.refName;
      ObjectId old = ObjectId.zeroId();
      if (receiveEvent.commit.getParentCount() > 0) {
        old = receiveEvent.commit.getParent(0);
      }

      if (receiveEvent.command.getRefName().startsWith(REFS_CHANGES)) {
        /*
        * If the ref-update hook tries to distinguish behavior between pushes to
        * refs/heads/... and refs/for/..., make sure we send it the correct
        * refname.
        * Also, if this is targetting refs/for/, make sure we behave the same as
        * what a push to refs/for/ would behave; in particular, setting oldrev
        * to 0000000000000000000000000000000000000000.
        */
        refname = refname.replace(R_HEADS, "refs/for/refs/heads/");
        old = ObjectId.zeroId();
      }
      HookResult result = hooks.doRefUpdateHook(receiveEvent.project, refname,
          user.getAccount(), old, receiveEvent.commit);
      if (result != null && result.getExitValue() != 0) {
          throw new CommitValidationException(result.toString().trim());
      }
      return Collections.emptyList();
    }
  }

  private final Provider<ReviewDb> db;
  private final AccountCache accounts;
  private final ChangeHooks hooks;
  private final PatchSetUtil psUtil;
  private final ChangeNotes.Factory changeNotesFactory;

  @Inject
  public ChangeHookApiListener(
      Provider<ReviewDb> db,
      AccountCache accounts,
      ChangeHooks hooks,
      PatchSetUtil psUtil,
      ChangeNotes.Factory changeNotesFactory) {
    this.db = db;
    this.accounts = accounts;
    this.hooks = hooks;
    this.psUtil = psUtil;
    this.changeNotesFactory = changeNotesFactory;
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event ev) {
    hooks.doProjectCreatedHook(new Project.NameKey(ev.getProjectName()),
        ev.getHeadName());
  }

  @Override
  public void onRevisionCreated(RevisionCreatedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doPatchsetCreatedHook(notes.getChange(),
        getPatchSet(notes, ev.getRevision()), db.get());
    } catch (OrmException e) {
      log.error("PatchsetCreated hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onDraftPublished(DraftPublishedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doDraftPublishedHook(notes.getChange(),
        getPatchSet(notes, ev.getRevision()), db.get());
    } catch (OrmException e) {
      log.error("DraftPublished hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onCommentAdded(CommentAddedListener.Event ev) {
    Map<String, Short> approvals = convertApprovalsMap(ev.getApprovals());
    Map<String, Short> oldApprovals = convertApprovalsMap(ev.getOldApprovals());
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doCommentAddedHook(notes.getChange(),
        getAccount(ev.getAuthor()),
        getPatchSet(notes, ev.getRevision()),
        ev.getComment(), approvals, oldApprovals, db.get());
    } catch (OrmException e) {
      log.error("CommentAdded hook failed to fun" + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeMerged(ChangeMergedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doChangeMergedHook(notes.getChange(),
          getAccount(ev.getMerger()),
          getPatchSet(notes, ev.getRevision()),
          db.get(), ev.getNewRevisionId());
    } catch (OrmException e) {
      log.error("ChangeMerged hook failed to run " + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doChangeAbandonedHook(notes.getChange(),
          getAccount(ev.getAbandoner()),
          getPatchSet(notes, ev.getRevision()),
          ev.getReason(), db.get());
    } catch (OrmException e) {
      log.error("ChangeAbandoned hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeRestored(ChangeRestoredListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doChangeRestoredHook(notes.getChange(),
          getAccount(ev.getRestorer()),
          getPatchSet(notes, ev.getRevision()),
          ev.getReason(), db.get());
    } catch (OrmException e) {
      log.error("ChangeRestored hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event ev) {
    hooks.doRefUpdatedHook(
        new Branch.NameKey(ev.getProjectName(), ev.getRefName()),
        ObjectId.fromString(ev.getOldObjectId()),
        ObjectId.fromString(ev.getNewObjectId()),
        getAccount(ev.getUpdater()));
  }

  @Override
  public void onReviewerAdded(ReviewerAddedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doReviewerAddedHook(notes.getChange(),
          getAccount(ev.getReviewer()),
          psUtil.current(db.get(), notes),
          db.get());
    } catch (OrmException e) {
      log.error("ReviewerAdded hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onReviewerDeleted(ReviewerDeletedListener.Event ev) {
    try {
      ChangeNotes notes = getNotes(ev.getChange());
      hooks.doReviewerDeletedHook(notes.getChange(),
          getAccount(ev.getReviewer()),
          psUtil.current(db.get(), notes),
          ev.getComment(),
          convertApprovalsMap(ev.getNewApprovals()),
          convertApprovalsMap(ev.getOldApprovals()),
          db.get());
    } catch (OrmException e) {
      log.error("ReviewerDeleted hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onTopicEdited(TopicEditedListener.Event ev) {
    try {
      hooks.doTopicChangedHook(getNotes(ev.getChange()).getChange(),
        getAccount(ev.getEditor()), ev.getOldTopic(), db.get());
    } catch (OrmException e) {
      log.error("TopicChanged hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onHashtagsEdited(HashtagsEditedListener.Event ev) {
    try {
      hooks.doHashtagsChangedHook(getNotes(ev.getChange()).getChange(),
          getAccount(ev.getEditor()),
          new HashSet<>(ev.getAddedHashtags()),
          new HashSet<>(ev.getRemovedHashtags()),
          new HashSet<>(ev.getHashtags()),
          db.get());
    } catch (OrmException e) {
      log.error("HashtagsChanged hook failed to run "
          + ev.getChange()._number, e);
    }
  }

  @Override
  public void onAgreementSignup(AgreementSignupListener.Event ev) {
    hooks.doClaSignupHook(getAccount(ev.getAccount()), ev.getAgreementName());
  }

  private ChangeNotes getNotes(ChangeInfo info) throws OrmException {
    try {
      return changeNotesFactory.createChecked(new Change.Id(info._number));
    } catch (NoSuchChangeException e) {
      throw new OrmException(e);
    }
  }

  private PatchSet getPatchSet(ChangeNotes notes, RevisionInfo info)
      throws OrmException {
    return psUtil.get(db.get(), notes, PatchSet.Id.fromRef(info.ref));
  }

  private Account getAccount(AccountInfo info) {
    return accounts.get(new Account.Id(info._accountId)).getAccount();
  }

  private static Map<String, Short> convertApprovalsMap(
      Map<String, ApprovalInfo> approvals) {
    Map<String, Short> result = new HashMap<>();
    for (Entry<String, ApprovalInfo> e : approvals.entrySet()) {
      Short value =
          e.getValue().value == null ? null : e.getValue().value.shortValue();
      result.put(e.getKey(), value);
    }
    return result;
  }
}
