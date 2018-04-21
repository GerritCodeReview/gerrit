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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Interface for serializing/deserializing a type to/from a persistent cache. */
public interface CacheSerializer<T> {
  /** Serializes the object to the given output stream. */
  public void serialize(T object, OutputStream out) throws IOException;

  /** Deserializes a single object form the given input stream. */
  public T deserialize(InputStream in) throws IOException;

  /**
   * Provides a hint for the serialized size of the given object.
   *
   * <p>This hint may be used for presizing a buffer, but is not guaranteed to be an upper bound.
   *
   * @param object the object.
   * @return size hint, or negative to indicate no estimate can be provided.
   */
  default int sizeHint(T object) {
    return -1;
  }

  /** Creates an input stream from which the serialized contents of the object may be read. */
  default InputStream createInputStream(T object) throws IOException {
    int sizeHint = sizeHint(object);
    ByteArrayOutputStream out =
        sizeHint >= 0 ? new ByteArrayOutputStream(sizeHint) : new ByteArrayOutputStream();
    serialize(object, out);
    return new ByteArrayInputStream(out.toByteArray());
  }
}
