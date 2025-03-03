// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.entities.converter;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.cache.proto.Cache.AccountProto;
import com.google.protobuf.Parser;
import java.time.Instant;

/** Proto converter between {@link Account} and {@link AccountProto}. */
@Immutable
public enum AccountProtoConverter implements ProtoConverter<AccountProto, Account> {
  INSTANCE;

  @Override
  public AccountProto toProto(Account account) {
    return AccountProto.newBuilder()
        .setId(account.id().get())
        .setRegisteredOn(account.registeredOn().toEpochMilli())
        .setInactive(account.inactive())
        .setFullName(Strings.nullToEmpty(account.fullName()))
        .setDisplayName(Strings.nullToEmpty(account.displayName()))
        .setPreferredEmail(Strings.nullToEmpty(account.preferredEmail()))
        .setStatus(Strings.nullToEmpty(account.status()))
        .setMetaId(Strings.nullToEmpty(account.metaId()))
        .setUniqueTag(Strings.nullToEmpty(account.uniqueTag()))
        .build();
  }

  @Override
  public Account fromProto(AccountProto proto) {
    Account.Builder builder =
        Account.builder(Account.id(proto.getId()), Instant.ofEpochMilli(proto.getRegisteredOn()))
            .setFullName(Strings.emptyToNull(proto.getFullName()))
            .setDisplayName(Strings.emptyToNull(proto.getDisplayName()))
            .setPreferredEmail(Strings.emptyToNull(proto.getPreferredEmail()))
            .setInactive(proto.getInactive())
            .setStatus(Strings.emptyToNull(proto.getStatus()))
            .setMetaId(Strings.emptyToNull(proto.getMetaId()))
            .setUniqueTag(Strings.emptyToNull(proto.getUniqueTag()));
    if (Strings.isNullOrEmpty(builder.uniqueTag())) {
      builder.setUniqueTag(builder.metaId());
    }
    return builder.build();
  }

  @Override
  public Parser<AccountProto> getParser() {
    return AccountProto.parser();
  }
}
