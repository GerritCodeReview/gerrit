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

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.gerrit.entities.Address;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class AddressSerializer {
  public static Address deserialize(Cache.AddressProto proto) {
    return Address.create(emptyToNull(proto.getName()), proto.getEmail());
  }

  public static Cache.AddressProto serialize(Address autoValue) {
    return Cache.AddressProto.newBuilder()
        .setName(nullToEmpty(autoValue.name()))
        .setEmail(autoValue.email())
        .build();
  }

  private AddressSerializer() {}
}
