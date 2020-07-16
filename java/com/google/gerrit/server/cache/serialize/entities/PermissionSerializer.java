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

import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class PermissionSerializer {
  public static Permission deserialize(Cache.PermissionProto proto) {
    Permission.Builder builder =
        Permission.builder(proto.getName()).setExclusiveGroup(proto.getExclusiveGroup());
    proto.getRulesList().stream()
        .map(PermissionRuleSerializer::deserialize)
        .map(PermissionRule::toBuilder)
        .forEach(rule -> builder.add(rule));
    return builder.build();
  }

  public static Cache.PermissionProto serialize(Permission autoValue) {
    return Cache.PermissionProto.newBuilder()
        .setName(autoValue.getName())
        .setExclusiveGroup(autoValue.getExclusiveGroup())
        .addAllRules(
            autoValue.getRules().stream()
                .map(PermissionRuleSerializer::serialize)
                .collect(toImmutableList()))
        .build();
  }

  private PermissionSerializer() {}
}
