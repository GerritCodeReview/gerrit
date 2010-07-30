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
import com.google.protobuf.CodedOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

class SerializableProtobuf<T> implements Serializable {
  private transient volatile T object;
  private transient ProtobufCodec<T> codec;
  private transient byte[] buf;
  private transient int hash;

  SerializableProtobuf(T object, ProtobufCodec<T> codec) {
    this.object = object;
    this.codec = codec;
    this.hash = object.hashCode();
  }

  T toObject(ProtobufCodec<T> codec, Provider<T> provider) {
    if (object == null) {
      this.codec = codec;
      if (provider == null) {
        object = codec.decode(buf);
      } else {
        object = provider.get();
        codec.mergeFrom(buf, object);
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
    SerializableProtobuf<T> other = ((SerializableProtobuf<T>) obj);

    if (hash != other.hash) {
      return false;
    }

    T thisObject = toObject(this, this.codec, other.codec);
    T otherObject = toObject(other, this.codec, other.codec);

    if (thisObject != null && otherObject != null) {
      return thisObject.equals(otherObject);
    } else if (this.buf != null && other.buf != null) {
      return Arrays.equals(this.buf, other.buf);
    } else {
      return false;
    }
  }

  private static <T> T toObject(SerializableProtobuf<T> sp,
      ProtobufCodec<T> codec1, ProtobufCodec<T> codec2) {
    if (sp.object != null) {
      return sp.object;
    }

    ProtobufCodec<T> codec = codec1 != null ? codec1 : codec2;
    if (codec != null) {
      sp.object = codec.decode(sp.buf);
      sp.buf = null;
      return sp.object;
    } else {
      return null;
    }
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    if (buf == null) {
      // If buffer is null, we must have the object and codec.
      buf = new byte[codec.sizeof(object)];
      codec.encode(object, buf);
    }

    oos.writeInt(hash);
    if (object == null) {
      oos.writeInt(buf.length);
      oos.write(buf);
    } else {
      oos.writeInt(codec.sizeof(object));
      CodedOutputStream cos = CodedOutputStream.newInstance(oos);
      codec.encode(object, cos);
      cos.flush();
    }
  }

  private void readObject(ObjectInputStream in) throws IOException,
      ClassNotFoundException {
    hash = in.readInt();
    int len = in.readInt();
    buf = new byte[len];
    in.readFully(buf);
  }
}
