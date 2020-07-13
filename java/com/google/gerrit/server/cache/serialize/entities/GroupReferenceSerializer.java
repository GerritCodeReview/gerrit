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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class GroupReferenceSerializer {
  public static GroupReference deserialize(Cache.GroupReferenceProto proto) {
    if (!proto.getUuid().isEmpty()) {
      return GroupReference.create(AccountGroup.uuid(proto.getUuid()), proto.getName());
    } else {
      return GroupReference.create(proto.getName());
    }
  }

  public static Cache.GroupReferenceProto serialize(GroupReference autoValue) {
    return Cache.GroupReferenceProto.newBuilder()
        .setName(autoValue.getName())
        .setUuid(autoValue.getUUID() == null ? "" : autoValue.getUUID().get())
        .build();
  }

  private GroupReferenceSerializer() {}
}
