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
import com.google.gerrit.common.data.ChangeSetPublishDetail;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChangeSetPublishDetailFactory extends Handler<ChangeSetPublishDetail> {
  interface Factory {
    ChangeSetPublishDetailFactory create(ChangeSet.Id changeSetId);
  }

  private final ChangeSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final TopicControl.Factory topicControlFactory;
  private final TopicFunctionState.Factory topicFunctionState;
  private final ApprovalTypes approvalTypes;
  private final AccountInfoCacheFactory aic;
  private final IdentifiedUser user;

  private final ChangeSet.Id changeSetId;

  private ChangeSetInfo changeSetInfo;
  private Topic topic;

  @Inject
  ChangeSetPublishDetailFactory(final ChangeSetInfoFactory infoFactory,
      final ReviewDb db,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final ChangeControl.Factory changeControlFactory,
      final TopicControl.Factory topicControlFactory,
      final TopicFunctionState.Factory topicFunctionState,
      final ApprovalTypes approvalTypes,
      final IdentifiedUser user, @Assisted final ChangeSet.Id changeSetId) {
    this.infoFactory = infoFactory;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.topicControlFactory = topicControlFactory;
    this.topicFunctionState = topicFunctionState;
    this.approvalTypes = approvalTypes;
    this.aic = accountInfoCacheFactory.create();
    this.user = user;

    this.changeSetId = changeSetId;
  }

  @Override
  public ChangeSetPublishDetail call() throws OrmException,
      ChangeSetInfoNotAvailableException, NoSuchTopicException,
      NoSuchEntityException, NoSuchChangeException {
    final Topic.Id topicId = changeSetId.getParentKey();
    final TopicControl control = topicControlFactory.validateFor(topicId);
    topic = control.getTopic();
    changeSetInfo = infoFactory.get(changeSetId);

    aic.want(topic.getOwner());

    ChangeSetPublishDetail detail = new ChangeSetPublishDetail();
    detail.setChangeSetInfo(changeSetInfo);
    detail.setTopic(topic);

    List<PermissionRange> allowed = Collections.emptyList();
    List<ChangeSetApproval> given = Collections.emptyList();

    if (topic.getStatus().isOpen()
        && changeSetId.equals(topic.currentChangeSetId())) {
      Map<String, PermissionRange> rangeByName =
        new HashMap<String, PermissionRange>();
      for (PermissionRange r : control.getLabelRanges()) {
        if (r.isLabel()) {
          rangeByName.put(r.getLabel(), r);
        }
      }
      allowed = new ArrayList<PermissionRange>();

      given = db.changeSetApprovals() //
          .byChangeSetUser(changeSetId, user.getAccountId()) //
          .toList();

      boolean couldSubmit = false;
      List<SubmitRecord> submitRecords = control.canSubmit(db, changeSetId,
          changeControlFactory, approvalTypes, topicFunctionState);
      for (SubmitRecord rec : submitRecords) {
        if (rec.status == SubmitRecord.Status.OK) {
          couldSubmit = true;
        }

        if (rec.labels != null) {
          int ok = 0;

          for (SubmitRecord.Label lbl : rec.labels) {
            boolean canMakeOk = false;
            PermissionRange range = rangeByName.get(lbl.label);
            if (range != null) {
              if (!allowed.contains(range)) {
                allowed.add(range);
              }

              ApprovalType at = approvalTypes.byLabel(lbl.label);
              if (at != null && at.getMax().getValue() == range.getMax()) {
                canMakeOk = true;
              } else if (at == null) {
                canMakeOk = true;
              }
            }

            switch (lbl.status) {
              case OK:
                ok++;
                break;

              case NEED:
                if (canMakeOk) {
                  ok++;
                }
                break;
            }
          }

          if (rec.status == SubmitRecord.Status.NOT_READY
              && ok == rec.labels.size()) {
            couldSubmit = true;
          }
        }
      }

      if (couldSubmit && control.getRefControl().canSubmit()) {
        detail.setCanSubmit(true);
      }
    }

    detail.setLabels(allowed);
    detail.setGiven(given);
    detail.setAccounts(aic.create());

    return detail;
  }
}
