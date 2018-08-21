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

import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto.ExternalIdProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ProtoCacheSerializers;
import com.google.gerrit.server.cache.serialize.ProtoCacheSerializers.ObjectIdConverter;
import java.util.Collection;

/**
 * Cache value containing all external IDs.
 *
 * <p>All returned fields are unmodifiable.
 */
@AutoValue
public abstract class AllExternalIds {
  static AllExternalIds create(Multimap<Account.Id, ExternalId> byAccount) {
    return create(
        newByAccountBuilder(byAccount.size()).build(byAccount), copyByEmail(byAccount.values()));
  }

  private static AllExternalIds create(Collection<ExternalId> externalIds) {
    SetMultimap<Account.Id, ExternalId> byAccount = newByAccountBuilder(externalIds.size()).build();
    externalIds.forEach(id -> byAccount.put(id.accountId(), id));
    return create(byAccount, copyByEmail(externalIds));
  }

  private static AllExternalIds create(
      SetMultimap<Account.Id, ExternalId> byAccount, SetMultimap<String, ExternalId> byEmail) {
    return new AutoValue_AllExternalIds(
        Multimaps.unmodifiableSetMultimap(byAccount), Multimaps.unmodifiableSetMultimap(byEmail));
  }

  private static SetMultimapBuilder<Object, Object> newByAccountBuilder(int expectedAccounts) {
    return MultimapBuilder.hashKeys(expectedAccounts).hashSetValues(5);
  }

  private static SetMultimap<String, ExternalId> copyByEmail(Collection<ExternalId> externalIds) {
    SetMultimap<String, ExternalId> byEmailCopy =
        MultimapBuilder.hashKeys(externalIds.size()).hashSetValues(1).build();
    externalIds
        .stream()
        .filter(e -> !Strings.isNullOrEmpty(e.email()))
        .forEach(e -> byEmailCopy.put(e.email(), e));
    return byEmailCopy;
  }

  public abstract SetMultimap<Account.Id, ExternalId> byAccount();

  public abstract SetMultimap<String, ExternalId> byEmail();

  static enum Serializer implements CacheSerializer<AllExternalIds> {
    INSTANCE;

    @Override
    public byte[] serialize(AllExternalIds object) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      AllExternalIdsProto.Builder allBuilder = AllExternalIdsProto.newBuilder();
      for (ExternalId id : object.byAccount().values()) {
        ExternalIdProto.Builder b =
            allBuilder
                .addExternalIdBuilder()
                .setKey(id.key().get())
                .setAccountId(id.accountId().get());
        if (id.email() != null) {
          b.setEmail(id.email());
        }
        if (id.password() != null) {
          b.setPassword(id.password());
        }
        if (id.blobId() != null) {
          b.setBlobId(idConverter.toByteString(id.blobId()));
        }
      }
      return ProtoCacheSerializers.toByteArray(allBuilder.build());
    }

    @Override
    public AllExternalIds deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return create(
          ProtoCacheSerializers.parseUnchecked(AllExternalIdsProto.parser(), in)
              .getExternalIdList()
              .stream()
              .map(proto -> toExternalId(idConverter, proto))
              .collect(toList()));
    }

    private static ExternalId toExternalId(ObjectIdConverter idConverter, ExternalIdProto proto) {
      return ExternalId.create(
          ExternalId.Key.parse(proto.getKey()),
          new Id(proto.getAccountId()),
          // ExternalId treats null and empty strings the same, so no need to distinguish here.
          proto.getEmail(),
          proto.getPassword(),
          !proto.getBlobId().isEmpty() ? idConverter.fromByteString(proto.getBlobId()) : null);
    }
  }
}
