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

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;
import java.sql.Timestamp;
import java.util.Objects;

@Immutable
public enum PatchSetApprovalProtoConverter
    implements ProtoConverter<Entities.PatchSetApproval, PatchSetApproval> {
  INSTANCE;

  private final ProtoConverter<Entities.PatchSetApproval_Key, PatchSetApproval.Key>
      patchSetApprovalKeyProtoConverter = PatchSetApprovalKeyProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;

  @Override
  public Entities.PatchSetApproval toProto(PatchSetApproval patchSetApproval) {
    Entities.PatchSetApproval.Builder builder =
        Entities.PatchSetApproval.newBuilder()
            .setKey(patchSetApprovalKeyProtoConverter.toProto(patchSetApproval.key()))
            .setValue(patchSetApproval.value())
            .setGranted(patchSetApproval.granted().getTime())
            .setPostSubmit(patchSetApproval.postSubmit());

    patchSetApproval.tag().ifPresent(builder::setTag);
    Account.Id realAccountId = patchSetApproval.realAccountId();
    // PatchSetApproval#getRealAccountId automatically delegates to PatchSetApproval#getAccountId if
    // the real author is not set. However, the previous protobuf representation kept
    // 'realAccountId' empty if it wasn't set. To ensure binary compatibility, simulate the previous
    // behavior.
    if (realAccountId != null && !Objects.equals(realAccountId, patchSetApproval.accountId())) {
      builder.setRealAccountId(accountIdConverter.toProto(realAccountId));
    }

    return builder.build();
  }

  @Override
  public PatchSetApproval fromProto(Entities.PatchSetApproval proto) {
    PatchSetApproval.Builder builder =
        PatchSetApproval.builder()
            .key(patchSetApprovalKeyProtoConverter.fromProto(proto.getKey()))
            .value(proto.getValue())
            .granted(new Timestamp(proto.getGranted()))
            .postSubmit(proto.getPostSubmit());
    if (proto.hasTag()) {
      builder.tag(proto.getTag());
    }
    if (proto.hasRealAccountId()) {
      builder.realAccountId(accountIdConverter.fromProto(proto.getRealAccountId()));
    }
    return builder.build();
  }

  @Override
  public Parser<Entities.PatchSetApproval> getParser() {
    return Entities.PatchSetApproval.parser();
  }
}
