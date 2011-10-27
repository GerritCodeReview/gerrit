// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ChangeSetApprovalDetail;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.topic.ChangeSetDetailFactory;
import com.google.gerrit.reviewdb.AbstractEntity;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetAncestor;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.workflow.TopicCategoryFunction;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a {@link TopicDetail} from a {@link Topic}. */
public class TopicDetailFactory extends Handler<TopicDetail> {
  public interface Factory {
    TopicDetailFactory create(Topic.Id id);
  }

  private final ApprovalTypes approvalTypes;
  private final ChangeControl.Factory changeControlFactory;
  private final TopicControl.Factory topicControlFactory;
  private final TopicFunctionState.Factory functionState;
  private final ChangeSetDetailFactory.Factory changeSetDetail;
  private final AccountInfoCacheFactory aic;
  private final AnonymousUser anonymousUser;
  private final ReviewDb db;

  private final Topic.Id topicId;

  private TopicDetail detail;
  private TopicControl control;

  @Inject
  TopicDetailFactory(final ApprovalTypes approvalTypes,
      final TopicFunctionState.Factory functionState,
      final ChangeSetDetailFactory.Factory changeSetDetail, final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final TopicControl.Factory topicControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AnonymousUser anonymousUser,
      @Assisted final Topic.Id id) {
    this.approvalTypes = approvalTypes;
    this.functionState = functionState;
    this.changeSetDetail = changeSetDetail;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.topicControlFactory = topicControlFactory;
    this.anonymousUser = anonymousUser;
    this.aic = accountInfoCacheFactory.create();

    this.topicId = id;
  }

  @Override
  public TopicDetail call() throws OrmException, NoSuchEntityException,
      ChangeSetInfoNotAvailableException, NoSuchTopicException, NoSuchChangeException {
    control = topicControlFactory.validateFor(topicId);
    final Topic topic= control.getTopic();
    final ChangeSet changeSet = db.changeSets().get(topic.currentChangeSetId());
    if (changeSet == null) {
      throw new NoSuchEntityException();
    }

    aic.want(topic.getOwner());

    detail = new TopicDetail();
    detail.setTopic(topic);
    detail.setAllowsAnonymous(control.forUser(anonymousUser).isVisible());

    detail.setCanAbandon(topic.getStatus().isOpen() && control.canAbandon());
    detail.setCanRestore(topic.getStatus() == AbstractEntity.Status.ABANDONED && control.canRestore());
    detail.setStarred(control.getCurrentUser().getStarredChanges().contains(
        topicId));

    detail.setCanRevert(topic.getStatus() == AbstractEntity.Status.MERGED && control.canAddChangeSet());

    if (detail.getTopic().getStatus().isOpen()) {
      List<SubmitRecord> submitRecords = control.canSubmit(db, changeSet.getId(),
          changeControlFactory, approvalTypes, functionState);
      for (SubmitRecord rec : submitRecords) {
        if (rec.labels != null) {
          for (SubmitRecord.Label lbl : rec.labels) {
            aic.want(lbl.appliedBy);
          }
        }
        if (rec.status == SubmitRecord.Status.OK && control.getRefControl().canSubmit()) {
          detail.setCanSubmit(true);
        }
      }
      detail.setSubmitRecords(submitRecords);
    }

    loadChangeSets();
    loadMessages();
    if (topic.currentChangeSetId() != null) {
      loadCurrentChangeSet();
    }
    load();
    detail.setAccounts(aic.create());
    return detail;
  }

  private void loadChangeSets() throws OrmException {
    detail.setChangeSets(db.changeSets().byTopic(topicId).toList());
  }

  private void loadMessages() throws OrmException {
    detail.setMessages(db.topicMessages().byTopic(topicId).toList());
    for (final TopicMessage m : detail.getMessages()) {
      aic.want(m.getAuthor());
    }
  }

  private void load() throws OrmException {
    final ChangeSet.Id csId = detail.getTopic().currentChangeSetId();
    final List<ChangeSetApproval> allApprovals =
        db.changeSetApprovals().byTopic(topicId).toList();

    if (detail.getTopic().getStatus().isOpen()) {
      final TopicFunctionState fs =
          functionState.create(detail.getTopic(), csId, allApprovals);

      final Set<ApprovalCategory.Id> missingApprovals =
          new HashSet<ApprovalCategory.Id>();

      for (final ApprovalType at : approvalTypes.getApprovalTypes()) {
        TopicCategoryFunction.forCategory(at.getCategory()).run(at, fs);
        if (!fs.isValid(at)) {
          missingApprovals.add(at.getCategory().getId());
        }
      }
      detail.setMissingApprovals(missingApprovals);
    }

    final boolean canRemoveReviewers = detail.getTopic().getStatus().isOpen() //
        && control.getCurrentUser() instanceof IdentifiedUser;
    final HashMap<Account.Id, ChangeSetApprovalDetail> ad =
        new HashMap<Account.Id, ChangeSetApprovalDetail>();
    for (ChangeSetApproval ca : allApprovals) {
      ChangeSetApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ChangeSetApprovalDetail(ca.getAccountId());
        d.setCanRemove(canRemoveReviewers);
        ad.put(d.getAccount(), d);
      }
      if (d.canRemove()) {
        d.setCanRemove(control.canRemoveReviewer(ca));
      }
      if (ca.getChangeSetId().equals(csId)) {
        d.add(ca);
      }
    }

    final Account.Id owner = detail.getTopic().getOwner();
    if (ad.containsKey(owner)) {
      // Ensure the owner always sorts to the top of the table
      //
      ad.get(owner).sortFirst();
    }

    aic.want(ad.keySet());
    detail.setApprovals(ad.values());
  }

  private void loadCurrentChangeSet() throws OrmException,
      NoSuchEntityException, ChangeSetInfoNotAvailableException,
      NoSuchTopicException {
    final ChangeSet.Id csId = detail.getTopic().currentChangeSetId();
    final ChangeSetDetailFactory loader = changeSetDetail.create(csId);
    loader.changeSet = detail.getCurrentChangeSet();
    loader.control = control;
    detail.setCurrentChangeSetDetail(loader.call());

    // We need to know the last patchSet of the first change in the current ChangeSet
    // in order to find the dependency of this topic.
    // Also, we need to know the last patchSet of the last change in order to
    // find the dependent changes.
    final List<Change> cList = detail.getCurrentChangeSetDetail().getChanges();
    final Change firstChange = cList.get(0);
    final Change lastChange = cList.get(cList.size() - 1);
    PatchSet.Id psId = firstChange.currentPatchSetId();

    final HashSet<Change.Id> changesToGet = new HashSet<Change.Id>();
    final List<Change.Id> ancestorOrder = new ArrayList<Change.Id>();

    for (PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(psId)) {
      for (PatchSet p : db.patchSets().byRevision(a.getAncestorRevision())) {
        final Change.Id ck = p.getId().getParentKey();
        if (changesToGet.add(ck)) {
          ancestorOrder.add(ck);
        }
      }
    }
    psId = lastChange.currentPatchSetId();

    final RevId cprev = db.patchSets().get(psId).getRevision();
    final Set<Change.Id> descendants = new HashSet<Change.Id>();
    if (cprev != null) {
      for (PatchSetAncestor a : db.patchSetAncestors().descendantsOf(cprev)) {
        final Change.Id ck = a.getPatchSet().getParentKey();
        if (descendants.add(ck)) {
          changesToGet.add(a.getPatchSet().getParentKey());
        }
      }
    }
    final Map<Change.Id, Change> m =
        db.changes().toMap(db.changes().get(changesToGet));

    final ArrayList<ChangeInfo> dependsOn = new ArrayList<ChangeInfo>();
    for (final Change.Id a : ancestorOrder) {
      final Change ac = m.get(a);
      if (ac != null) {
        dependsOn.add(newChangeInfo(ac));
      }
    }

    final ArrayList<ChangeInfo> neededBy = new ArrayList<ChangeInfo>();
    for (final Change.Id a : descendants) {
      final Change ac = m.get(a);
      if (ac != null) {
        neededBy.add(newChangeInfo(ac));
      }
    }

    Collections.sort(neededBy, new Comparator<ChangeInfo>() {
      public int compare(final ChangeInfo o1, final ChangeInfo o2) {
        return o1.getId().get() - o2.getId().get();
      }
    });

    detail.setDependsOn(dependsOn);
    detail.setNeededBy(neededBy);
  }

  private ChangeInfo newChangeInfo(final Change ac) {
    aic.want(ac.getOwner());
    ChangeInfo ci = new ChangeInfo(ac);
    ci.setStarred(isStarred(ac));
    return ci;
  }

  private boolean isStarred(final Change ac) {
    return control.getCurrentUser().getStarredChanges().contains(ac.getId());
  }
}
