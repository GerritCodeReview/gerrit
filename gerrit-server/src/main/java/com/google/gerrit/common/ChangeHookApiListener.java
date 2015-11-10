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
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.common.ChangeHookRunner.HookResult;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.events.MergeFailedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
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
import com.google.gwtorm.server.OrmException;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.project.RefControl;

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
    ChangeAbandonedListener,
    ChangeMergedListener,
    ChangeRestoredListener,
    CommentAddedListener,
    DraftPublishedListener,
    GitReferenceUpdatedListener,
    HashtagsEditedListener,
    MergeFailedListener,
    NewProjectCreatedListener,
    ReviewerAddedListener,
    RevisionCreatedListener,
    TopicEditedListener {
  /** A logger for this class. */
  private static final Logger log =
      LoggerFactory.getLogger(ChangeHookApiListener.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(ChangeAbandonedListener.class).to(ChangeHookApiListener.class);
      bind(ChangeMergedListener.class).to(ChangeHookApiListener.class);
      bind(ChangeRestoredListener.class).to(ChangeHookApiListener.class);
      bind(CommentAddedListener.class).to(ChangeHookApiListener.class);
      bind(DraftPublishedListener.class).to(ChangeHookApiListener.class);
      bind(GitReferenceUpdatedListener.class).to(ChangeHookApiListener.class);
      bind(HashtagsEditedListener.class).to(ChangeHookApiListener.class);
      bind(MergeFailedListener.class).to(ChangeHookApiListener.class);
      bind(NewProjectCreatedListener.class).to(ChangeHookApiListener.class);
      bind(ReviewerAddedListener.class).to(ChangeHookApiListener.class);
      bind(RevisionCreatedListener.class).to(ChangeHookApiListener.class);
      bind(TopicEditedListener.class).to(ChangeHookApiListener.class);
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
      ObjectId old = receiveEvent.commit.getParent(0);

      if (receiveEvent.command.getRefName().startsWith(REFS_CHANGES)) {
          /*
          * If the ref-update hook tries to distinguish behavior between pushes to
          * refs/heads/... and refs/for/..., make sure we send it the correct refname.
          * Also, if this is targetting refs/for/, make sure we behave the same as
          * what a push to refs/for/ would behave; in particular, setting oldrev to
          * 0000000000000000000000000000000000000000.
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

  @Inject
  public ChangeHookApiListener(
      Provider<ReviewDb> db,
      AccountCache accounts,
      ChangeHooks hooks) {
    this.db = db;
    this.accounts = accounts;
    this.hooks = hooks;
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event ev) {
    hooks.doProjectCreatedHook(new Project.NameKey(ev.getProjectName()),
        ev.getHeadName());
  }

  @Override
  public void onRevisionCreated(RevisionCreatedListener.Event ev) {
    try {
      hooks.doPatchsetCreatedHook(getChange(ev.getChange()),
        getPatchSet(ev.getRevision()), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onDraftPublished(DraftPublishedListener.Event ev) {
    try {
      hooks.doDraftPublishedHook(getChange(ev.getChange()),
        getPatchSet(ev.getRevision()), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onCommentAdded(CommentAddedListener.Event ev) {
    Map<String, Short> approvals = new HashMap<String, Short>();
    for (Entry<String, ApprovalInfo> e: ev.getApprovals().entrySet()) {
      approvals.put(e.getKey(), e.getValue().value.shortValue());
    }
    try {
      hooks.doCommentAddedHook(getChange(ev.getChange()),
        getAccount(ev.getAuthor()), getPatchSet(ev.getRevision()), ev.getComment(), approvals, db.get());
    } catch (OrmException e) {
        log.warn("CommentAdded hook failed to fun" + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeMerged(ChangeMergedListener.Event ev) {
    try {
      hooks.doChangeMergedHook(getChange(ev.getChange()),
          getAccount(ev.getMerger()), getPatchSet(ev.getRevision()),
          db.get(), ev.getNewRevisionId());
    } catch (OrmException e) {
      log.error("ChangeMerged hook failed to run " + ev.getChange()._number, e);
    }
  }

  @Override
  public void onMergeFailed(MergeFailedListener.Event ev) {
    try {
     hooks.doMergeFailedHook(getChange(ev.getChange()),
         getAccount(ev.getSubmitter()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
      log.error("MergeFailed hook failed to run" + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event ev) {
    try {
      hooks.doChangeAbandonedHook(getChange(ev.getChange()),
          getAccount(ev.getAbandoner()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onChangeRestored(ChangeRestoredListener.Event ev) {
    try {
      hooks.doChangeRestoredHook(getChange(ev.getChange()),
          getAccount(ev.getRestorer()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
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
      Change change = getChange(ev.getChange());
      PatchSet patch = db.get().patchSets().get(change.currentPatchSetId());

      hooks.doReviewerAddedHook(change, getAccount(ev.getReviewer()), patch,
          db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onTopicEdited(TopicEditedListener.Event ev) {
    try {
      hooks.doTopicChangedHook(getChange(ev.getChange()),
        getAccount(ev.getEditor()), ev.getOldTopic(), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onHashtagsEdited(HashtagsEditedListener.Event ev) {
    try {
      hooks.doHashtagsChangedHook(getChange(ev.getChange()),
          getAccount(ev.getEditor()),
          new HashSet<String>(ev.getAddedHashtags()),
          new HashSet<String>(ev.getRemovedHashtags()),
          new HashSet<String>(ev.getHashtags()),
          db.get());
    } catch (OrmException e) {
    }
  }

  private Change getChange(ChangeInfo info) throws OrmException {
    return db.get().changes().get(new Change.Id(info._number));
  }

  private PatchSet getPatchSet(RevisionInfo info) throws OrmException {
    return db.get().patchSets().get(PatchSet.Id.fromRef(info.ref));
  }

  private Account getAccount(AccountInfo info) {
    return accounts.get(new Account.Id(info._accountId)).getAccount();
  }
}
