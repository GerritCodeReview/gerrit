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
  private static final long serialVersionUID = 1L;

  private transient volatile Object data;
  private transient ProtobufCodec<T> codec;
  private transient int hash;

  SerializableProtobuf(T object, ProtobufCodec<T> codec) {
    this.data = object;
    this.codec = codec;
    this.hash = object.hashCode();
  }

  T toObject(ProtobufCodec<T> codec, Provider<T> provider) {
    if (codec == null) {
      return null;
    }
    if (data instanceof byte[]) {
      this.codec = codec;
      if (provider == null) {
        data = codec.decode((byte[]) data);
      } else {
        T tmp = provider.get();
        codec.mergeFrom((byte[]) data, tmp);
        data = tmp;
      }
    }
    return (T) data;
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

    // Make sure we either both have codecs, or we both do not
    if (this.codec == null && other.codec != null) {
      this.codec = other.codec;
    } else if (this.codec != null && other.codec == null) {
      other.codec = this.codec;
    }

    // Equals is only ever called on keys, which cannot have providers
    T thisObject = this.toObject(codec, null);
    T otherObject = other.toObject(other.codec, null);

    if (thisObject == null && otherObject == null) {
      // Neither of us had codecs, so we must compare byte arrays
      return Arrays.equals((byte[]) this.data, (byte[]) other.data);
    } else if (thisObject != null && otherObject != null){
      return thisObject.equals(otherObject);
    } else {
      return false;
    }
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.writeInt(hash);

    Object d = data;
    if (d instanceof byte[]) {
      byte[] buf = (byte[]) d;
      oos.writeInt(buf.length);
      oos.write(buf);
    } else {
      // We assume that if we have an object, we must have a codec
      T obj = (T) d;
      oos.writeInt(codec.sizeof(obj));
      CodedOutputStream cos = CodedOutputStream.newInstance(oos);
      codec.encode(obj, cos);
      cos.flush();
    }
  }

  private void readObject(ObjectInputStream in) throws IOException,
      ClassNotFoundException {
    hash = in.readInt();
    int len = in.readInt();
    data = new byte[len];
    in.readFully((byte[]) data);
  }
}
