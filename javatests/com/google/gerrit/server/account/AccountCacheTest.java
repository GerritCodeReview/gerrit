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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectWatchKey;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.config.CachedPreferences;
import java.time.Instant;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Test to ensure that we are serializing and deserializing {@link Account} correctly. This is part
 * of the {@code AccountCache}.
 */
public class AccountCacheTest {
  private static final Account ACCOUNT = Account.builder(Account.id(1), Instant.EPOCH).build();
  private static final Cache.AccountProto ACCOUNT_PROTO =
      Cache.AccountProto.newBuilder().setId(1).setRegisteredOn(0).build();
  private static final CachedAccountDetails.Serializer SERIALIZER =
      CachedAccountDetails.Serializer.INSTANCE;

  @Test
  public void account_roundTrip() throws Exception {
    // The uniqueTag and metaId can be different (in google internal implementation).
    // This tests ensures that they are serialized/deserialized separately.
    Account account =
        Account.builder(Account.id(1), Instant.EPOCH)
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(false)
            .setMetaId("dead..beef")
            .setUniqueTag("dead..beef..tag")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld")
            .build();
    CachedAccountDetails original =
        CachedAccountDetails.create(account, ImmutableMap.of(), CachedPreferences.EMPTY);
    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(
                Cache.AccountProto.newBuilder()
                    .setId(1)
                    .setRegisteredOn(0)
                    .setFullName("foo bar")
                    .setDisplayName("foo")
                    .setInactive(true)
                    .setMetaId("dead..beef")
                    .setUniqueTag("dead..beef..tag")
                    .setStatus("OOO")
                    .setPreferredEmail("foo@bar.tld"))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void account_deserializeOldRecordWithoutUniqueTag() throws Exception {
    Account.Builder builder =
        Account.builder(Account.id(1), Instant.EPOCH)
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(false)
            .setMetaId("dead..beef")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld");
    CachedAccountDetails original =
        CachedAccountDetails.create(builder.build(), ImmutableMap.of(), CachedPreferences.EMPTY);
    CachedAccountDetails expected =
        CachedAccountDetails.create(
            builder.setUniqueTag("dead..beef").build(), ImmutableMap.of(), CachedPreferences.EMPTY);
    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expectedProto =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(
                Cache.AccountProto.newBuilder()
                    .setId(1)
                    .setRegisteredOn(0)
                    .setFullName("foo bar")
                    .setDisplayName("foo")
                    .setInactive(true)
                    .setMetaId("dead..beef")
                    .setStatus("OOO")
                    .setPreferredEmail("foo@bar.tld"))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expectedProto);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(expected);
  }

  @Test
  public void account_roundTripNullFields() throws Exception {
    CachedAccountDetails original =
        CachedAccountDetails.create(ACCOUNT, ImmutableMap.of(), CachedPreferences.EMPTY);
    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder().setAccount(ACCOUNT_PROTO).build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void config_gitConfig_roundTrip() throws Exception {
    Config cfg = new Config();
    cfg.fromText("[general]\n\tfoo = bar");
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT, ImmutableMap.of(), CachedPreferences.fromLegacyConfig(cfg));

    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(ACCOUNT_PROTO)
            .setUserPreferences(
                Cache.CachedPreferencesProto.newBuilder().setLegacyGitConfig(cfg.toText()))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void config_protoConfig_roundTrip() throws Exception {
    Entities.UserPreferences proto =
        Entities.UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                Entities.UserPreferences.GeneralPreferencesInfo.newBuilder().setChangesPerPage(17))
            .build();
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT, ImmutableMap.of(), CachedPreferences.fromUserPreferencesProto(proto));

    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(ACCOUNT_PROTO)
            .setUserPreferences(Cache.CachedPreferencesProto.newBuilder().setUserPreferences(proto))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void projectWatch_roundTrip() throws Exception {
    ProjectWatchKey key = ProjectWatchKey.create(Project.nameKey("pro/ject"), "*");
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT,
            ImmutableMap.of(key, ImmutableSet.of(NotifyConfig.NotifyType.ALL_COMMENTS)),
            CachedPreferences.EMPTY);

    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(ACCOUNT_PROTO)
            .addProjectWatchProto(
                Cache.ProjectWatchProto.newBuilder()
                    .setProject("pro/ject")
                    .setFilter("*")
                    .addNotifyType("ALL_COMMENTS"))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void projectWatch_roundTripNullFilter() throws Exception {
    ProjectWatchKey key = ProjectWatchKey.create(Project.nameKey("pro/ject"), null);
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT,
            ImmutableMap.of(key, ImmutableSet.of(NotifyConfig.NotifyType.ALL_COMMENTS)),
            CachedPreferences.EMPTY);

    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(ACCOUNT_PROTO)
            .addProjectWatchProto(
                Cache.ProjectWatchProto.newBuilder()
                    .setProject("pro/ject")
                    .addNotifyType("ALL_COMMENTS"))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }
}
