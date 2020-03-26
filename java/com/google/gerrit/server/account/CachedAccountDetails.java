// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/** Details of an account that are cached persistently in {@link AccountCache}. */
@AutoValue
abstract class CachedAccountDetails {
  @AutoValue
  abstract static class Key {
    static Key create(Account.Id accountId, ObjectId id) {
      return new AutoValue_CachedAccountDetails_Key(accountId, id.copy());
    }

    /** Identifier of the account. */
    abstract Account.Id accountId();

    /**
     * Git revision at which the account was loaded. Corresponds to a revision on the account ref
     * ({@code refs/users/<sharded-id>}).
     */
    abstract ObjectId id();

    /** Serializer used to read this entity from and write it to a persistent storage. */
    enum Serializer implements CacheSerializer<Key> {
      INSTANCE;

      @Override
      public byte[] serialize(Key object) {
        return Protos.toByteArray(
            Cache.AccountKeyProto.newBuilder()
                .setAccountId(object.accountId().get())
                .setId(ObjectIdConverter.create().toByteString(object.id()))
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        Cache.AccountKeyProto proto = Protos.parseUnchecked(Cache.AccountKeyProto.parser(), in);
        return Key.create(
            Account.id(proto.getAccountId()),
            ObjectIdConverter.create().fromByteString(proto.getId()));
      }
    }
  }

  /** Essential attributes of the account, such as name or registration time. */
  abstract Account account();

  /** Projects that the user has configured to watch. */
  abstract ImmutableMap<ProjectWatches.ProjectWatchKey, ImmutableSet<ProjectWatches.NotifyType>>
      projectWatches();

  /** Preferences that this user has. Serialized as Git-config style string. */
  abstract String rawPreferences();

  static CachedAccountDetails create(
      Account account,
      ImmutableMap<ProjectWatches.ProjectWatchKey, ImmutableSet<ProjectWatches.NotifyType>>
          projectWatches,
      String rawPreferences) {
    return new AutoValue_CachedAccountDetails(account, projectWatches, rawPreferences);
  }

  /** Serializer used to read this entity from and write it to a persistent storage. */
  enum Serializer implements CacheSerializer<CachedAccountDetails> {
    INSTANCE;

    @Override
    public byte[] serialize(CachedAccountDetails cachedAccountDetails) {
      Cache.AccountDetailsProto.Builder serialized = Cache.AccountDetailsProto.newBuilder();
      // We don't care about the difference of empty strings and null in the Account entity.
      Account account = cachedAccountDetails.account();
      Cache.AccountProto.Builder accountProto =
          Cache.AccountProto.newBuilder()
              .setId(account.id().get())
              .setRegisteredOn(account.registeredOn().toInstant().toEpochMilli())
              .setInactive(account.inactive())
              .setFullName(Strings.nullToEmpty(account.fullName()))
              .setDisplayName(Strings.nullToEmpty(account.displayName()))
              .setPreferredEmail(Strings.nullToEmpty(account.preferredEmail()))
              .setStatus(Strings.nullToEmpty(account.status()))
              .setMetaId(Strings.nullToEmpty(account.metaId()));
      serialized.setAccount(accountProto);

      for (Map.Entry<ProjectWatches.ProjectWatchKey, ImmutableSet<ProjectWatches.NotifyType>>
          watch : cachedAccountDetails.projectWatches().entrySet()) {
        Cache.ProjectWatchProto.Builder proto =
            Cache.ProjectWatchProto.newBuilder().setProject(watch.getKey().project().get());
        if (watch.getKey().filter() != null) {
          proto.setFilter(watch.getKey().filter());
        }
        watch
            .getValue()
            .forEach(
                n ->
                    proto.addNotifyType(
                        Enums.stringConverter(ProjectWatches.NotifyType.class)
                            .reverse()
                            .convert(n)));
        serialized.addProjectWatchProto(proto);
      }

      serialized.setUserPreferences(cachedAccountDetails.rawPreferences());
      return Protos.toByteArray(serialized.build());
    }

    @Override
    public CachedAccountDetails deserialize(byte[] in) {
      Cache.AccountDetailsProto proto =
          Protos.parseUnchecked(Cache.AccountDetailsProto.parser(), in);
      Account account =
          Account.builder(
                  Account.id(proto.getAccount().getId()),
                  Timestamp.from(Instant.ofEpochMilli(proto.getAccount().getRegisteredOn())))
              .setFullName(Strings.emptyToNull(proto.getAccount().getFullName()))
              .setDisplayName(Strings.emptyToNull(proto.getAccount().getDisplayName()))
              .setPreferredEmail(Strings.emptyToNull(proto.getAccount().getPreferredEmail()))
              .setInactive(proto.getAccount().getInactive())
              .setStatus(Strings.emptyToNull(proto.getAccount().getStatus()))
              .setMetaId(Strings.emptyToNull(proto.getAccount().getMetaId()))
              .build();

      ImmutableMap.Builder<ProjectWatches.ProjectWatchKey, ImmutableSet<ProjectWatches.NotifyType>>
          projectWatches = ImmutableMap.builder();
      proto.getProjectWatchProtoList().stream()
          .forEach(
              p ->
                  projectWatches.put(
                      ProjectWatches.ProjectWatchKey.create(
                          Project.nameKey(p.getProject()), p.getFilter()),
                      p.getNotifyTypeList().stream()
                          .map(
                              e ->
                                  Enums.stringConverter(ProjectWatches.NotifyType.class).convert(e))
                          .collect(toImmutableSet())));

      return CachedAccountDetails.create(
          account, projectWatches.build(), proto.getUserPreferences());
    }
  }
}
