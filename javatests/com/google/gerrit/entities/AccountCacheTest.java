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

package com.google.gerrit.entities;

import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.gerrit.server.cache.proto.Cache.AccountProto;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.Test;

/**
 * Test to ensure that we are serializing and deserializing {@link Account} correctly. This is part
 * of the {@code AccountCache}.
 */
public class AccountCacheTest {
  @Test
  public void roundTrip() throws Exception {
    Account account =
        Account.builder(Account.id(1), Timestamp.from(Instant.EPOCH))
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(false)
            .setMetaId("dead..beef")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld")
            .build();
    byte[] serialized = Account.Serializer.INSTANCE.serialize(account);
    ProtoTruth.assertThat(AccountProto.parseFrom(serialized))
        .isEqualTo(
            AccountProto.newBuilder()
                .setId(1)
                .setRegisteredOn(0)
                .setFullName("foo bar")
                .setDisplayName("foo")
                .setInactive(true)
                .setMetaId("dead..beef")
                .setStatus("OOO")
                .setPreferredEmail("foo@bar.tld")
                .build());
    Truth.assertThat(Account.Serializer.INSTANCE.deserialize(serialized)).isEqualTo(account);
  }

  @Test
  public void roundTripNullFields() throws Exception {
    Account account = Account.builder(Account.id(1), Timestamp.from(Instant.EPOCH)).build();
    byte[] serialized = Account.Serializer.INSTANCE.serialize(account);
    ProtoTruth.assertThat(AccountProto.parseFrom(serialized))
        .isEqualTo(AccountProto.newBuilder().setId(1).setRegisteredOn(0).build());
    Truth.assertThat(Account.Serializer.INSTANCE.deserialize(serialized)).isEqualTo(account);
  }
}
