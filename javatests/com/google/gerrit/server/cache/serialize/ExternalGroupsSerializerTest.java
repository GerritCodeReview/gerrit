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

package com.google.gerrit.server.cache.serialize;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.GroupIncludeCacheImpl.ExternalGroupsSerializer;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto.ExternalGroupProto;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;

public class ExternalGroupsSerializerTest {
  private static final ImmutableList<AccountGroup.UUID> GROUPS =
      ImmutableList.of(
          AccountGroup.UUID.parse("593f90fcf688109f61b0fd4aa47ddf65abb96012"),
          AccountGroup.UUID.parse("bc9f75584ac0362584a64fb3f0095d905415b153"));

  @Test
  public void serialize() throws InvalidProtocolBufferException {
    byte[] serialized = ExternalGroupsSerializer.INSTANCE.serialize(GROUPS);

    assertThat(AllExternalGroupsProto.parseFrom(serialized))
        .isEqualTo(
            AllExternalGroupsProto.newBuilder()
                .addAllExternalGroup(
                    GROUPS.stream()
                        .map(g -> ExternalGroupProto.newBuilder().setGroupUuid(g.get()).build())
                        .collect(toImmutableList()))
                .build());
  }

  @Test
  public void deserialize() {
    byte[] serialized = ExternalGroupsSerializer.INSTANCE.serialize(GROUPS);
    assertThat(ExternalGroupsSerializer.INSTANCE.deserialize(serialized)).isEqualTo(GROUPS);
  }
}
