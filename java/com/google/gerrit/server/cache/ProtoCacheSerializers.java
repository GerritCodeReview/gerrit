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

package com.google.gerrit.server.cache;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import java.io.IOException;

/** Static utilities for writing protobuf-based {@link CacheSerializer} implementations. */
public class ProtoCacheSerializers {
  /**
   * Serializes a proto to a byte array.
   *
   * <p>Guarantees deterministic serialization and thus is suitable for use as a persistent cache
   * key. Should be used in preference to {@link MessageLite#toByteArray()}, which is not guaranteed
   * deterministic.
   *
   * @param message the proto message to serialize.
   * @return a byte array with the message contents.
   */
  public static byte[] toByteArray(MessageLite message) {
    byte[] bytes = new byte[message.getSerializedSize()];
    CodedOutputStream cout = CodedOutputStream.newInstance(bytes);
    cout.useDeterministicSerialization();
    try {
      message.writeTo(cout);
      cout.checkNoSpaceLeft();
      return bytes;
    } catch (IOException e) {
      throw new IllegalStateException("exception writing to byte array");
    }
  }

  private ProtoCacheSerializers() {}
}
