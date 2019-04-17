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
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.protobuf.Parser;

public enum PatchSetApprovalKeyProtoConverter
    implements ProtoConverter<Entities.PatchSetApproval_Key, PatchSetApproval.Key> {
  INSTANCE;

  private final ProtoConverter<Entities.PatchSet_Id, PatchSet.Id> patchSetIdConverter =
      PatchSetIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.LabelId, LabelId> labelIdConverter =
      LabelIdProtoConverter.INSTANCE;

  @Override
  public Entities.PatchSetApproval_Key toProto(PatchSetApproval.Key key) {
    return Entities.PatchSetApproval_Key.newBuilder()
        .setPatchSetId(patchSetIdConverter.toProto(key.patchSetId()))
        .setAccountId(accountIdConverter.toProto(key.accountId()))
        .setCategoryId(labelIdConverter.toProto(key.labelId()))
        .build();
  }

  @Override
  public PatchSetApproval.Key fromProto(Entities.PatchSetApproval_Key proto) {
    return PatchSetApproval.key(
        patchSetIdConverter.fromProto(proto.getPatchSetId()),
        accountIdConverter.fromProto(proto.getAccountId()),
        labelIdConverter.fromProto(proto.getCategoryId()));
  }

  @Override
  public Parser<Entities.PatchSetApproval_Key> getParser() {
    return Entities.PatchSetApproval_Key.parser();
  }
}
