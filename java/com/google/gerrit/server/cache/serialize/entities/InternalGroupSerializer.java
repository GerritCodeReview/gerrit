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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.time.Instant;

/** Helper to (de)serialize values for caches. */
public class InternalGroupSerializer {
  public static InternalGroup deserialize(Cache.InternalGroupProto proto) {
    InternalGroup.Builder builder =
        InternalGroup.builder()
            .setId(AccountGroup.id(proto.getId()))
            .setNameKey(AccountGroup.nameKey(proto.getName()))
            .setOwnerGroupUUID(AccountGroup.uuid(proto.getOwnerGroupUuid()))
            .setVisibleToAll(proto.getIsVisibleToAll())
            .setGroupUUID(AccountGroup.uuid(proto.getGroupUuid()))
            .setCreatedOn(Instant.ofEpochMilli(proto.getCreatedOn()))
            .setMembers(
                proto.getMembersIdsList().stream()
                    .map(a -> Account.id(a))
                    .collect(toImmutableSet()))
            .setSubgroups(
                proto.getSubgroupUuidsList().stream()
                    .map(s -> AccountGroup.uuid(s))
                    .collect(toImmutableSet()));

    if (!proto.getDescription().isEmpty()) {
      builder.setDescription(proto.getDescription());
    }

    if (!proto.getRefState().isEmpty()) {
      builder.setRefState(ObjectIdConverter.create().fromByteString(proto.getRefState()));
    }

    return builder.build();
  }

  public static Cache.InternalGroupProto serialize(InternalGroup autoValue) {
    Cache.InternalGroupProto.Builder builder =
        Cache.InternalGroupProto.newBuilder()
            .setId(autoValue.getId().get())
            .setName(autoValue.getName())
            .setOwnerGroupUuid(autoValue.getOwnerGroupUUID().get())
            .setIsVisibleToAll(autoValue.isVisibleToAll())
            .setGroupUuid(autoValue.getGroupUUID().get())
            .setCreatedOn(autoValue.getCreatedOn().toEpochMilli());

    autoValue.getMembers().stream().forEach(m -> builder.addMembersIds(m.get()));
    autoValue.getSubgroups().stream().forEach(s -> builder.addSubgroupUuids(s.get()));

    if (autoValue.getDescription() != null) {
      builder.setDescription(autoValue.getDescription());
    }

    if (autoValue.getRefState() != null) {
      builder.setRefState(ObjectIdConverter.create().toByteString(autoValue.getRefState()));
    }

    return builder.build();
  }

  private InternalGroupSerializer() {}
}
