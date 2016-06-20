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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
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
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.TopicEditedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.ChangeAbandonedEvent;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.ChangeRestoredEvent;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.DraftPublishedEvent;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.HashtagsChangedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.events.ReviewerAddedEvent;
import com.google.gerrit.server.events.ReviewerDeletedEvent;
import com.google.gerrit.server.events.TopicChangedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class StreamEventsApiListener implements
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
  private static final Logger log =
      LoggerFactory.getLogger(StreamEventsApiListener.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), ChangeAbandonedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), ChangeMergedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), ChangeRestoredListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), CommentAddedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), DraftPublishedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), HashtagsEditedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), NewProjectCreatedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), ReviewerAddedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), ReviewerDeletedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), RevisionCreatedListener.class)
        .to(StreamEventsApiListener.class);
      DynamicSet.bind(binder(), TopicEditedListener.class)
        .to(StreamEventsApiListener.class);
    }
  }

  private final DynamicItem<EventDispatcher> dispatcher;
  private final Provider<ReviewDb> db;
  private final EventFactory eventFactory;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;

  @Inject
  StreamEventsApiListener(DynamicItem<EventDispatcher> dispatcher,
      Provider<ReviewDb> db,
      EventFactory eventFactory,
      AccountCache accountCache,
      ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    this.dispatcher = dispatcher;
    this.db = db;
    this.eventFactory = eventFactory;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
  }

  private ReviewDb unwrap(ReviewDb db) {
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }

  private Change getChange(ChangeInfo info) throws OrmException {
    return unwrap(db.get()).changes().get(new Change.Id(info._number));
  }

  private PatchSet getPatchSet(RevisionInfo info) throws OrmException {
    return unwrap(db.get()).patchSets().get(PatchSet.Id.fromRef(info.ref));
  }

  private PatchSet getPatchSet(PatchSet.Id id) throws OrmException {
    return unwrap(db.get()).patchSets().get(id);
  }

  private Account getAccount(AccountInfo info) {
    return accountCache.get(new Account.Id(info._accountId)).getAccount();
  }

  private Supplier<ChangeAttribute> changeAttributeSupplier(
      final Change change) {
    return Suppliers.memoize(
        new Supplier<ChangeAttribute>() {
          @Override
          public ChangeAttribute get() {
            return eventFactory.asChangeAttribute(change);
          }
        });
  }

  private Supplier<AccountAttribute> accountAttributeSupplier(
      final Account account) {
    return Suppliers.memoize(
        new Supplier<AccountAttribute>() {
          @Override
          public AccountAttribute get() {
            return eventFactory.asAccountAttribute(account);
          }
        });
  }

  private Supplier<PatchSetAttribute> patchSetAttributeSupplier(
      final Change change, final PatchSet patchSet) {
    return Suppliers.memoize(
        new Supplier<PatchSetAttribute>() {
          @Override
          public PatchSetAttribute get() {
            try (Repository repo =
                  repoManager.openRepository(change.getProject());
                RevWalk revWalk = new RevWalk(repo)) {
              return eventFactory.asPatchSetAttribute(
                  revWalk, change, patchSet);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
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

  private ApprovalAttribute getApprovalAttribute(LabelTypes labelTypes,
      Entry<String, Short> approval,
      Map<String, Short> oldApprovals) {
  ApprovalAttribute a = new ApprovalAttribute();
    a.type = approval.getKey();

    if (oldApprovals != null && !oldApprovals.isEmpty()) {
      if (oldApprovals.get(approval.getKey()) != null) {
        a.oldValue = Short.toString(oldApprovals.get(approval.getKey()));
      }
    }
    LabelType lt = labelTypes.byLabel(approval.getKey());
    if (lt != null) {
      a.description = lt.getName();
    }
    if (approval.getValue() != null) {
      a.value = Short.toString(approval.getValue());
    }
    return a;
  }

  private Supplier<ApprovalAttribute[]> approvalsAttributeSupplier(
      final Change change, Map<String, ApprovalInfo> newApprovals,
      final Map<String, ApprovalInfo> oldApprovals) {
    final Map<String, Short> approvals = convertApprovalsMap(newApprovals);
    return Suppliers.memoize(
        new Supplier<ApprovalAttribute[]>() {
          @Override
          public ApprovalAttribute[] get() {
            LabelTypes labelTypes = projectCache.get(
                change.getProject()).getLabelTypes();
            if (approvals.size() > 0) {
              ApprovalAttribute[] r = new ApprovalAttribute[approvals.size()];
              int i = 0;
              for (Map.Entry<String, Short> approval : approvals.entrySet()) {
                r[i++] = getApprovalAttribute(labelTypes, approval,
                    convertApprovalsMap(oldApprovals));
              }
              return r;
            }
            return null;
          }
        });
  }

  String[] hashtagArray(Collection<String> hashtags) {
    if (hashtags != null && hashtags.size() > 0) {
      return Sets.newHashSet(hashtags).toArray(
          new String[hashtags.size()]);
    }
    return null;
  }

  @Override
  public void onTopicEdited(TopicEditedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      TopicChangedEvent event = new TopicChangedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.changer = accountAttributeSupplier(getAccount(ev.getEditor()));
      event.oldTopic = ev.getOldTopic();

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onRevisionCreated(RevisionCreatedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      PatchSet patchSet = getPatchSet(ev.getRevision());
      PatchSetCreatedEvent event = new PatchSetCreatedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.uploader = accountAttributeSupplier(getAccount(ev.getUploader()));

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onReviewerDeleted(final ReviewerDeletedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      ReviewerDeletedEvent event = new ReviewerDeletedEvent(change);
      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.reviewer = accountAttributeSupplier(getAccount(ev.getReviewer()));
      event.comment = ev.getComment();
      event.approvals = approvalsAttributeSupplier(change,
          ev.getNewApprovals(), ev.getOldApprovals());

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }

  }

  @Override
  public void onReviewerAdded(ReviewerAddedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      ReviewerAddedEvent event = new ReviewerAddedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.reviewer = accountAttributeSupplier(getAccount(ev.getReviewer()));

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event ev) {
    ProjectCreatedEvent event = new ProjectCreatedEvent();
    event.projectName = ev.getProjectName();
    event.headName = ev.getHeadName();

    dispatcher.get().postEvent(event.getProjectNameKey(), event);
  }

  @Override
  public void onHashtagsEdited(HashtagsEditedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      HashtagsChangedEvent event = new HashtagsChangedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.editor = accountAttributeSupplier(getAccount(ev.getEditor()));
      event.hashtags = hashtagArray(ev.getHashtags());
      event.added = hashtagArray(ev.getAddedHashtags());
      event.removed = hashtagArray(ev.getRemovedHashtags());

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onGitReferenceUpdated(final GitReferenceUpdatedListener.Event ev) {
    RefUpdatedEvent event = new RefUpdatedEvent();
    if (ev.getUpdater() != null) {
      event.submitter = accountAttributeSupplier(getAccount(ev.getUpdater()));
    }
    final Branch.NameKey refName =
        new Branch.NameKey(ev.getProjectName(), ev.getRefName());
    event.refUpdate = Suppliers.memoize(
        new Supplier<RefUpdateAttribute>() {
          @Override
          public RefUpdateAttribute get() {
            return eventFactory.asRefUpdateAttribute(
                ObjectId.fromString(ev.getOldObjectId()),
                ObjectId.fromString(ev.getNewObjectId()),
                refName);
          }
        });
    dispatcher.get().postEvent(refName, event);
  }

  @Override
  public void onDraftPublished(DraftPublishedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      DraftPublishedEvent event = new DraftPublishedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.uploader = accountAttributeSupplier(getAccount(ev.getPublisher()));

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onCommentAdded(CommentAddedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      CommentAddedEvent event = new CommentAddedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.author =  accountAttributeSupplier(getAccount(ev.getAuthor()));
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.comment = ev.getComment();
      event.approvals = approvalsAttributeSupplier(
          change, ev.getApprovals(), ev.getOldApprovals());

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onChangeRestored(ChangeRestoredListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      ChangeRestoredEvent event = new ChangeRestoredEvent(change);

      event.change = changeAttributeSupplier(change);
      event.restorer = accountAttributeSupplier(getAccount(ev.getRestorer()));
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.reason = ev.getReason();

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onChangeMerged(ChangeMergedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      ChangeMergedEvent event = new ChangeMergedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.submitter = accountAttributeSupplier(getAccount(ev.getMerger()));
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.newRev = ev.getNewRevisionId();

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event ev) {
    try {
      Change change = getChange(ev.getChange());
      ChangeAbandonedEvent event = new ChangeAbandonedEvent(change);

      event.change = changeAttributeSupplier(change);
      event.abandoner = accountAttributeSupplier(getAccount(ev.getAbandoner()));
      event.patchSet = patchSetAttributeSupplier(change,
          getPatchSet(change.currentPatchSetId()));
      event.reason = ev.getReason();

      dispatcher.get().postEvent(change, event, unwrap(db.get()));
    } catch (OrmException e) {
      log.error("Failed to dispatch event", e);
    }
  }
}
