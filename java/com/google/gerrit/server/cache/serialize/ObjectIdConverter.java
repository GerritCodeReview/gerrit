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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.git.ObjectIds;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Helper for serializing {@link ObjectId} instances to/from protobuf fields.
 *
 * <p>Reuse a single instance's {@link #toByteString(ObjectId)} and {@link
 * #fromByteString(ByteString)} within a single {@link CacheSerializer#serialize} or {@link
 * CacheSerializer#deserialize} method body to minimize allocation of temporary buffers.
 *
 * <p><strong>Note:</strong> This class is not threadsafe. Instances must not be stored in {@link
 * CacheSerializer} fields if the serializer instances will be used from multiple threads.
 */
public class ObjectIdConverter {
  public static ObjectIdConverter create() {
    return new ObjectIdConverter();
  }

  private final byte[] buf = new byte[ObjectIds.LEN];

  private ObjectIdConverter() {}

  public ByteString toByteString(ObjectId id) {
    id.copyRawTo(buf, 0);
    return ByteString.copyFrom(buf);
  }

  public ObjectId fromByteString(ByteString in) {
    checkArgument(
        in.size() == ObjectIds.LEN, "expected ByteString of length %s: %s", ObjectIds.LEN, in);
    in.copyTo(buf, 0);
    return ObjectId.fromRaw(buf);
  }
}
