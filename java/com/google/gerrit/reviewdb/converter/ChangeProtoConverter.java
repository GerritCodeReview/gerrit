// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.reviewdb.converter;

import com.google.gerrit.proto.Entities;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.protobuf.Parser;
import java.sql.Timestamp;

public enum ChangeProtoConverter implements ProtoConverter<Entities.Change, Change> {
  INSTANCE;

  private final ProtoConverter<Entities.Change_Id, Change.Id> changeIdConverter =
      ChangeIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Change_Key, Change.Key> changeKeyConverter =
      ChangeKeyProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Branch_NameKey, Branch.NameKey> branchNameConverter =
      BranchNameKeyProtoConverter.INSTANCE;

  @Override
  public Entities.Change toProto(Change change) {
    Entities.Change.Builder builder =
        Entities.Change.newBuilder()
            .setChangeId(changeIdConverter.toProto(change.getId()))
            .setRowVersion(change.getRowVersion())
            .setChangeKey(changeKeyConverter.toProto(change.getKey()))
            .setCreatedOn(change.getCreatedOn().getTime())
            .setLastUpdatedOn(change.getLastUpdatedOn().getTime())
            .setOwnerAccountId(accountIdConverter.toProto(change.getOwner()))
            .setDest(branchNameConverter.toProto(change.getDest()))
            .setStatus(change.getStatus().getCode())
            .setIsPrivate(change.isPrivate())
            .setWorkInProgress(change.isWorkInProgress())
            .setReviewStarted(change.hasReviewStarted());
    PatchSet.Id currentPatchSetId = change.currentPatchSetId();
    // Special behavior necessary to ensure binary compatibility.
    builder.setCurrentPatchSetId(currentPatchSetId == null ? 0 : currentPatchSetId.get());
    String subject = change.getSubject();
    if (subject != null) {
      builder.setSubject(subject);
    }
    String topic = change.getTopic();
    if (topic != null) {
      builder.setTopic(topic);
    }
    String originalSubject = change.getOriginalSubjectOrNull();
    if (originalSubject != null) {
      builder.setOriginalSubject(originalSubject);
    }
    String submissionId = change.getSubmissionId();
    if (submissionId != null) {
      builder.setSubmissionId(submissionId);
    }
    Account.Id assignee = change.getAssignee();
    if (assignee != null) {
      builder.setAssignee(accountIdConverter.toProto(assignee));
    }
    Change.Id revertOf = change.getRevertOf();
    if (revertOf != null) {
      builder.setRevertOf(changeIdConverter.toProto(revertOf));
    }
    return builder.build();
  }

  @Override
  public Change fromProto(Entities.Change proto) {
    Change.Id changeId = changeIdConverter.fromProto(proto.getChangeId());
    Change.Key key =
        proto.hasChangeKey() ? changeKeyConverter.fromProto(proto.getChangeKey()) : null;
    Account.Id owner =
        proto.hasOwnerAccountId() ? accountIdConverter.fromProto(proto.getOwnerAccountId()) : null;
    Branch.NameKey destination =
        proto.hasDest() ? branchNameConverter.fromProto(proto.getDest()) : null;
    Change change =
        new Change(key, changeId, owner, destination, new Timestamp(proto.getCreatedOn()));
    if (proto.hasLastUpdatedOn()) {
      change.setLastUpdatedOn(new Timestamp(proto.getLastUpdatedOn()));
    }
    Change.Status status = Change.Status.forCode((char) proto.getStatus());
    if (status != null) {
      change.setStatus(status);
    }
    String subject = proto.hasSubject() ? proto.getSubject() : null;
    String originalSubject = proto.hasOriginalSubject() ? proto.getOriginalSubject() : null;
    change.setCurrentPatchSet(
        PatchSet.id(changeId, proto.getCurrentPatchSetId()), subject, originalSubject);
    if (proto.hasTopic()) {
      change.setTopic(proto.getTopic());
    }
    if (proto.hasSubmissionId()) {
      change.setSubmissionId(proto.getSubmissionId());
    }
    if (proto.hasAssignee()) {
      change.setAssignee(accountIdConverter.fromProto(proto.getAssignee()));
    }
    change.setPrivate(proto.getIsPrivate());
    change.setWorkInProgress(proto.getWorkInProgress());
    change.setReviewStarted(proto.getReviewStarted());
    if (proto.hasRevertOf()) {
      change.setRevertOf(changeIdConverter.fromProto(proto.getRevertOf()));
    }
    return change;
  }

  @Override
  public Parser<Entities.Change> getParser() {
    return Entities.Change.parser();
  }
}
