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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.MergeFailedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.TopicEditedListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class ChangeHookApiListener implements
    ChangeAbandonedListener,
    ChangeMergedListener,
    ChangeRestoredListener,
    CommentAddedListener,
    DraftPublishedListener,
    HashtagsEditedListener,
/*    HeadUpdatedListener,*/
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
      bind(HashtagsEditedListener.class).to(ChangeHookApiListener.class);
      bind(MergeFailedListener.class).to(ChangeHookApiListener.class);
      bind(NewProjectCreatedListener.class).to(ChangeHookApiListener.class);
      bind(ReviewerAddedListener.class).to(ChangeHookApiListener.class);
      bind(RevisionCreatedListener.class).to(ChangeHookApiListener.class);
      bind(TopicEditedListener.class).to(ChangeHookApiListener.class);
    }
  }

  private final Provider<ReviewDb> db;
  private final AccountCache accounts;
  private final ChangeHooks ch;

  @Inject
  public ChangeHookApiListener(
      Provider<ReviewDb> db,
      AccountCache accounts,
      ChangeHooks ch) {
    this.db = db;
    this.accounts = accounts;
    this.ch = ch;
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event ev) {
    ch.doProjectCreatedHook(new Project.NameKey(ev.getProjectName()),
        ev.getHeadName());
  }

  @Override
  public void onRevisionCreated(RevisionCreatedListener.Event ev) {
    try {
      ch.doPatchsetCreatedHook(getChange(ev.getChange()),
        getPatchSet(ev.getRevision()), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onDraftPublished(DraftPublishedListener.Event ev) {
    try {
      ch.doDraftPublishedHook(getChange(ev.getChange()),
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
      ch.doCommentAddedHook(getChange(ev.getChange()),
        getAccount(ev.getAuthor()), getPatchSet(ev.getRevision()), ev.getComment(), approvals, db.get());
    } catch (OrmException e) {
        log.warn("CommentAdded hook failed to fun" + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeMerged(ChangeMergedListener.Event ev) {
    try {
      ch.doChangeMergedHook(getChange(ev.getChange()),
          getAccount(ev.getMerger()), getPatchSet(ev.getRevision()),
          db.get(), ev.getNewRevisionId());
    } catch (OrmException e) {
      log.error("ChangeMerged hook failed to run " + ev.getChange()._number, e);
    }
  }

  @Override
  public void onMergeFailed(MergeFailedListener.Event ev) {
    try {
     ch.doMergeFailedHook(getChange(ev.getChange()),
         getAccount(ev.getSubmitter()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
      log.error("MergeFailed hook failed to run" + ev.getChange()._number, e);
    }
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event ev) {
    try {
      ch.doChangeAbandonedHook(getChange(ev.getChange()),
          getAccount(ev.getAbandoner()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onChangeRestored(ChangeRestoredListener.Event ev) {
    try {
      ch.doChangeRestoredHook(getChange(ev.getChange()),
          getAccount(ev.getRestorer()), getPatchSet(ev.getRevision()), ev.getReason(), db.get());
    } catch (OrmException e) {
    }
  }

/*  @Override
  public void onHeadUpdated(HeadUpdatedListener.Event ev) {
    try {
    ch.doRefUpdatedHook(refName, RefUpdate refUpdate,
    } catch (OrmException e) {
    }
  }*/
// 
//  @Override
//   public void onHeadUpdated(HeadUpdatedListener.Event ev) {
//    try {
//     ch.doRefUpdatedHook(refName, ObjectId oldId,
//   }

  @Override
  public void onReviewerAdded(ReviewerAddedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      PatchSet patch = db.get().patchSets().get(change.currentPatchSetId());

      ch.doReviewerAddedHook(change, getAccount(ev.getReviewer()), patch,
          db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onTopicEdited(TopicEditedListener.Event ev) {
    try {
      ch.doTopicChangedHook(getChange(ev.getChange()),
        getAccount(ev.getEditor()), ev.getOldTopic(), db.get());
    } catch (OrmException e) {
    }
  }

  @Override
  public void onHashtagsEdited(HashtagsEditedListener.Event ev) {
    try {
      ch.doHashtagsChangedHook(getChange(ev.getChange()),
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
