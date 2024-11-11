// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.cache.serialize.entities.InternalGroupSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.InternalGroupSerializer.serialize;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.util.time.TimeUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class InternalGroupSerializerTest {
  static final InternalGroup MINIMAL_VALUES_SET =
      InternalGroup.builder()
          .setId(AccountGroup.id(123456))
          .setNameKey(AccountGroup.nameKey("group name"))
          .setOwnerGroupUUID(AccountGroup.uuid("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
          .setVisibleToAll(false)
          .setGroupUUID(AccountGroup.uuid("deadbeefdeadbeefdeadbeefdeadbeef12345678"))
          .setCreatedOn(TimeUtil.now())
          .setMembers(ImmutableSet.of(Account.id(123), Account.id(321)))
          .setSubgroups(
              ImmutableSet.of(
                  AccountGroup.uuid("87654321deadbeefdeadbeefdeadbeefdeadbeef"),
                  AccountGroup.uuid("deadbeefdeadbeefdeadbeefdeadbeef87654321")))
          .build();

  static final InternalGroup ALL_VALUES_SET =
      MINIMAL_VALUES_SET.toBuilder()
          .setDescription("description")
          .setRefState(ObjectId.fromString("12345678deadbeefdeadbeefdeadbeefdeadbeef"))
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripWithMinimalValues() {
    assertThat(deserialize(serialize(MINIMAL_VALUES_SET))).isEqualTo(MINIMAL_VALUES_SET);
  }
}
