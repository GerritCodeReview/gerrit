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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** Serializer that uses default Java serialization. */
public class JavaCacheSerializer<T extends Serializable> implements CacheSerializer<T> {
  @Override
  public byte[] serialize(T object) throws IOException {
    try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout)) {
      oout.writeObject(object);
      oout.flush();
      return bout.toByteArray();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T deserialize(byte[] in) throws IOException {
    Object object;
    try (ByteArrayInputStream bin = new ByteArrayInputStream(in);
        ObjectInputStream oin = new ObjectInputStream(bin)) {
      object = oin.readObject();
    } catch (ClassNotFoundException e) {
      throw new IOException("Failed to deserialize object of type", e);
    }
    return (T) object;
  }
}
