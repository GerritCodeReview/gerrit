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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class AccessSectionSerializer {
  public static AccessSection deserialize(Cache.AccessSectionProto proto) {
    AccessSection.Builder builder = AccessSection.builder(proto.getName());
    proto.getPermissionsList().stream()
        .map(PermissionSerializer::deserialize)
        .map(Permission::toBuilder)
        .forEach(p -> builder.addPermission(p));
    return builder.build();
  }

  public static Cache.AccessSectionProto serialize(AccessSection autoValue) {
    return Cache.AccessSectionProto.newBuilder()
        .setName(autoValue.getName())
        .addAllPermissions(
            autoValue.getPermissions().stream()
                .map(PermissionSerializer::serialize)
                .collect(toImmutableList()))
        .build();
  }

  private AccessSectionSerializer() {}
}
