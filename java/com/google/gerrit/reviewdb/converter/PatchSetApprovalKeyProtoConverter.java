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

import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.protobuf.Parser;

public enum PatchSetApprovalKeyProtoConverter
    implements ProtoConverter<Reviewdb.PatchSetApproval_Key, PatchSetApproval.Key> {
  INSTANCE;

  private final ProtoConverter<Reviewdb.PatchSet_Id, PatchSet.Id> patchSetIdConverter =
      PatchSetIdProtoConverter.INSTANCE;
  private final ProtoConverter<Reviewdb.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;
  private final ProtoConverter<Reviewdb.LabelId, LabelId> labelIdConverter =
      LabelIdProtoConverter.INSTANCE;

  @Override
  public Reviewdb.PatchSetApproval_Key toProto(PatchSetApproval.Key key) {
    return Reviewdb.PatchSetApproval_Key.newBuilder()
        .setPatchSetId(patchSetIdConverter.toProto(key.getParentKey()))
        .setAccountId(accountIdConverter.toProto(key.getAccountId()))
        .setCategoryId(labelIdConverter.toProto(key.getLabelId()))
        .build();
  }

  @Override
  public PatchSetApproval.Key fromProto(Reviewdb.PatchSetApproval_Key proto) {
    return new PatchSetApproval.Key(
        patchSetIdConverter.fromProto(proto.getPatchSetId()),
        accountIdConverter.fromProto(proto.getAccountId()),
        labelIdConverter.fromProto(proto.getCategoryId()));
  }

  @Override
  public Parser<Reviewdb.PatchSetApproval_Key> getParser() {
    return Reviewdb.PatchSetApproval_Key.parser();
  }
}
