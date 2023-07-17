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

package com.google.gerrit.server.account.externalids.storage.notedb;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto.ExternalIdProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.util.stream.Stream;

/** Cache value containing all external IDs. */
@AutoValue
public abstract class AllExternalIds {
  static AllExternalIds create(Stream<ExternalId> externalIds) {
    ImmutableMap.Builder<ExternalId.Key, ExternalId> byKey = ImmutableMap.builder();
    ImmutableSetMultimap.Builder<Account.Id, ExternalId> byAccount = ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<String, ExternalId> byEmail = ImmutableSetMultimap.builder();
    externalIds.forEach(
        id -> {
          byKey.put(id.key(), id);
          byAccount.put(id.accountId(), id);
          if (!Strings.isNullOrEmpty(id.email())) {
            byEmail.put(id.email(), id);
          }
        });

    return new AutoValue_AllExternalIds(byKey.build(), byAccount.build(), byEmail.build());
  }

  static AllExternalIds create(
      ImmutableMap<ExternalId.Key, ExternalId> byKey,
      ImmutableSetMultimap<Account.Id, ExternalId> byAccount,
      ImmutableSetMultimap<String, ExternalId> byEmail) {
    return new AutoValue_AllExternalIds(byKey, byAccount, byEmail);
  }

  public abstract ImmutableMap<ExternalId.Key, ExternalId> byKey();

  public abstract ImmutableSetMultimap<Account.Id, ExternalId> byAccount();

  public abstract ImmutableSetMultimap<String, ExternalId> byEmail();

  enum Serializer implements CacheSerializer<AllExternalIds> {
    INSTANCE;

    @Override
    public byte[] serialize(AllExternalIds object) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      AllExternalIdsProto.Builder allBuilder = AllExternalIdsProto.newBuilder();
      object.byAccount().values().stream()
          .map(extId -> toProto(idConverter, extId))
          .forEach(allBuilder::addExternalId);
      return Protos.toByteArray(allBuilder.build());
    }

    private static ExternalIdProto toProto(ObjectIdConverter idConverter, ExternalId externalId) {
      ExternalIdProto.Builder b =
          ExternalIdProto.newBuilder()
              .setKey(externalId.key().get())
              .setAccountId(externalId.accountId().get())
              .setIsCaseInsensitive(externalId.isCaseInsensitive());
      if (externalId.email() != null) {
        b.setEmail(externalId.email());
      }
      if (externalId.password() != null) {
        b.setPassword(externalId.password());
      }
      if (externalId.blobId() != null) {
        b.setBlobId(idConverter.toByteString(externalId.blobId()));
      }
      return b.build();
    }

    @Override
    public AllExternalIds deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return create(
          Protos.parseUnchecked(AllExternalIdsProto.parser(), in).getExternalIdList().stream()
              .map(proto -> toExternalId(idConverter, proto)));
    }

    private static ExternalId toExternalId(ObjectIdConverter idConverter, ExternalIdProto proto) {
      return ExternalId.create(
          ExternalId.Key.parse(proto.getKey(), proto.getIsCaseInsensitive()),
          Account.id(proto.getAccountId()),
          // ExternalId treats null and empty strings the same, so no need to distinguish here.
          proto.getEmail(),
          proto.getPassword(),
          !proto.getBlobId().isEmpty() ? idConverter.fromByteString(proto.getBlobId()) : null);
    }
  }
}
