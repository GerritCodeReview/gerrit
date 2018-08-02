// Copyright (C) 2018 The Android Open Source Project
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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

public enum ObjectIdCacheSerializer implements CacheSerializer<ObjectId> {
  INSTANCE;

  @Override
  public byte[] serialize(ObjectId object) {
    byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
    object.copyRawTo(buf, 0);
    return buf;
  }

  @Override
  public ObjectId deserialize(byte[] in) {
    if (in == null || in.length != Constants.OBJECT_ID_LENGTH) {
      throw new IllegalArgumentException("Failed to deserialize ObjectId");
    }
    return ObjectId.fromRaw(in);
  }
}
