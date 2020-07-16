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

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class PermissionRuleSerializer {
  private static final Converter<String, PermissionRule.Action> ACTION_CONVERTER =
      Enums.stringConverter(PermissionRule.Action.class);

  public static PermissionRule deserialize(Cache.PermissionRuleProto proto) {
    return PermissionRule.builder(GroupReferenceSerializer.deserialize(proto.getGroup()))
        .setAction(ACTION_CONVERTER.convert(proto.getAction()))
        .setForce(proto.getForce())
        .setMin(proto.getMin())
        .setMax(proto.getMax())
        .build();
  }

  public static Cache.PermissionRuleProto serialize(PermissionRule autoValue) {
    return Cache.PermissionRuleProto.newBuilder()
        .setAction(ACTION_CONVERTER.reverse().convert(autoValue.getAction()))
        .setForce(autoValue.getForce())
        .setMin(autoValue.getMin())
        .setMax(autoValue.getMax())
        .setGroup(GroupReferenceSerializer.serialize(autoValue.getGroup()))
        .build();
  }

  private PermissionRuleSerializer() {}
}
