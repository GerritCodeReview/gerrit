// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.ehcache;

import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.inject.Provider;
import com.google.protobuf.CodedInputStream;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

public class SerializableProtobuf<T> implements Serializable {
  private transient T object;
  private transient ProtobufCodec<T> codec;
  private byte[] buf;
  private final int hash;

  public SerializableProtobuf(T object, ProtobufCodec<T> codec) {
    buf = new byte[codec.sizeof(object)];
    codec.encode(object, buf);
    this.object = object;
    this.codec = codec;
    hash = object.hashCode();
  }

  public T toObject(ProtobufCodec<T> codec, Provider<T> provider) {
    if (object == null) {
      this.codec = codec;
      if (provider == null) {
        object = codec.decode(buf);
      } else {
        object = provider.get();
        try {
          codec.mergeFrom(CodedInputStream.newInstance(buf), object);
        } catch (IOException e) {
          throw new RuntimeException("Cannot decode message", e);
        }
      }
      // Free the memory being taken up by the buffer.
      buf = null;
    }
    return object;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SerializableProtobuf<?>)) {
      return false;
    }
    SerializableProtobuf<?> other = ((SerializableProtobuf<?>) obj);
    if (hash != other.hash) {
      return false;
    }
    if (object != null && other.object != null) {
      return object.equals(other.object);
    }
    return Arrays.equals(buf, other.buf);
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    if (buf == null) {
      // If buffer is null, toObject must have been called, which means that we
      // have the object and the codec.
      buf = new byte[codec.sizeof(object)];
      codec.encode(object, buf);
    }
    oos.defaultWriteObject();
  }
}
