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

import com.google.gerrit.common.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Serializer that uses default Java serialization.
 *
 * <p>Unlike most {@link CacheSerializer} implementations, serializing null is supported.
 *
 * @param <T> type to serialize. Must implement {@code Serializable}, but due to implementation
 *     details this is only checked at runtime.
 */
public class JavaCacheSerializer<T> implements CacheSerializer<T> {
  @Override
  public byte[] serialize(@Nullable T object) {
    try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout)) {
      oout.writeObject(object);
      oout.flush();
      return bout.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to serialize object", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T deserialize(byte[] in) {
    Object object;
    try (ByteArrayInputStream bin = new ByteArrayInputStream(in);
        ObjectInputStream oin = new ObjectInputStream(bin)) {
      object = oin.readObject();
    } catch (ClassNotFoundException | IOException e) {
      throw new IllegalArgumentException("Failed to deserialize object", e);
    }
    return (T) object;
  }
}
