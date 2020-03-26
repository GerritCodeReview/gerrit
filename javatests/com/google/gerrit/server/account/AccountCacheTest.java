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
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.Test;

/**
 * Test to ensure that we are serializing and deserializing {@link Account} correctly. This is part
 * of the {@code AccountCache}.
 */
public class AccountCacheTest {
  private static final Account ACCOUNT =
      Account.builder(Account.id(1), Timestamp.from(Instant.EPOCH)).build();
  private static final Cache.AccountProto ACCOUNT_PROTO =
      Cache.AccountProto.newBuilder().setId(1).setRegisteredOn(0).build();
  private static final CachedAccountDetails.Serializer SERIALIZER =
      CachedAccountDetails.Serializer.INSTANCE;

  @Test
  public void account_roundTrip() throws Exception {
    Account account =
        Account.builder(Account.id(1), Timestamp.from(Instant.EPOCH))
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(false)
            .setMetaId("dead..beef")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld")
            .build();
    CachedAccountDetails original = CachedAccountDetails.create(account, ImmutableMap.of(), "");
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
                    .setStatus("OOO")
                    .setPreferredEmail("foo@bar.tld"))
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void account_roundTripNullFields() throws Exception {
    CachedAccountDetails original = CachedAccountDetails.create(ACCOUNT, ImmutableMap.of(), "");
    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder().setAccount(ACCOUNT_PROTO).build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void config_roundTrip() throws Exception {
    CachedAccountDetails original =
        CachedAccountDetails.create(ACCOUNT, ImmutableMap.of(), "[general]\n\tfoo = bar");

    byte[] serialized = SERIALIZER.serialize(original);
    Cache.AccountDetailsProto expected =
        Cache.AccountDetailsProto.newBuilder()
            .setAccount(ACCOUNT_PROTO)
            .setUserPreferences("[general]\n\tfoo = bar")
            .build();
    ProtoTruth.assertThat(Cache.AccountDetailsProto.parseFrom(serialized)).isEqualTo(expected);
    Truth.assertThat(SERIALIZER.deserialize(serialized)).isEqualTo(original);
  }

  @Test
  public void projectWatch_roundTrip() throws Exception {
    ProjectWatches.ProjectWatchKey key =
        ProjectWatches.ProjectWatchKey.create(Project.nameKey("pro/ject"), "*");
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT,
            ImmutableMap.of(key, ImmutableSet.of(ProjectWatches.NotifyType.ALL_COMMENTS)),
            "");

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
    ProjectWatches.ProjectWatchKey key =
        ProjectWatches.ProjectWatchKey.create(Project.nameKey("pro/ject"), null);
    CachedAccountDetails original =
        CachedAccountDetails.create(
            ACCOUNT,
            ImmutableMap.of(key, ImmutableSet.of(ProjectWatches.NotifyType.ALL_COMMENTS)),
            "");

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
