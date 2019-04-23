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

package com.google.gerrit.server.account.externalids;

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto.ExternalIdProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.util.Collection;

/** Cache value containing all external IDs. */
@AutoValue
public abstract class AllExternalIds {
  static AllExternalIds create(SetMultimap<Account.Id, ExternalId> byAccount) {
    return new AutoValue_AllExternalIds(
        ImmutableSetMultimap.copyOf(byAccount), byEmailCopy(byAccount.values()));
  }

  static AllExternalIds create(Collection<ExternalId> externalIds) {
    return new AutoValue_AllExternalIds(
        externalIds.stream().collect(toImmutableSetMultimap(ExternalId::accountId, e -> e)),
        byEmailCopy(externalIds));
  }

  private static ImmutableSetMultimap<String, ExternalId> byEmailCopy(
      Collection<ExternalId> externalIds) {
    return externalIds.stream()
        .filter(e -> !Strings.isNullOrEmpty(e.email()))
        .collect(toImmutableSetMultimap(ExternalId::email, e -> e));
  }

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
              .setAccountId(externalId.accountId().get());
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
              .map(proto -> toExternalId(idConverter, proto))
              .collect(toList()));
    }

    private static ExternalId toExternalId(ObjectIdConverter idConverter, ExternalIdProto proto) {
      return ExternalId.create(
          ExternalId.Key.parse(proto.getKey()),
          Account.id(proto.getAccountId()),
          // ExternalId treats null and empty strings the same, so no need to distinguish here.
          proto.getEmail(),
          proto.getPassword(),
          !proto.getBlobId().isEmpty() ? idConverter.fromByteString(proto.getBlobId()) : null);
    }
  }
}
