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

import static java.util.Objects.requireNonNull;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

public enum IntegerCacheSerializer implements CacheSerializer<Integer> {
  INSTANCE;

  // Same as com.google.protobuf.WireFormat#MAX_VARINT_SIZE. Note that negative values take up more
  // than MAX_VARINT32_SIZE space.
  private static final int MAX_VARINT_SIZE = 10;

  public static <T> CacheSerializer<T> onResultOf(
      Function<T, Integer> toInteger, Function<Integer, T> fromInteger) {
    return new CacheSerializer<T>() {
      @Override
      public byte[] serialize(T object) {
        return IntegerCacheSerializer.INSTANCE.serialize(toInteger.apply(object));
      }

      @Override
      public T deserialize(byte[] in) {
        return fromInteger.apply(IntegerCacheSerializer.INSTANCE.deserialize(in));
      }
    };
  }

  @Override
  public byte[] serialize(Integer object) {
    byte[] buf = new byte[MAX_VARINT_SIZE];
    CodedOutputStream cout = CodedOutputStream.newInstance(buf);
    try {
      cout.writeInt32NoTag(requireNonNull(object));
      cout.flush();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize int");
    }
    int n = cout.getTotalBytesWritten();
    return n == buf.length ? buf : Arrays.copyOfRange(buf, 0, n);
  }

  @Override
  public Integer deserialize(byte[] in) {
    CodedInputStream cin = CodedInputStream.newInstance(requireNonNull(in));
    int ret;
    try {
      ret = cin.readRawVarint32();
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to deserialize int");
    }
    int n = cin.getTotalBytesRead();
    if (n != in.length) {
      throw new IllegalArgumentException(
          "Extra bytes in int representation: "
              + TextFormat.escapeBytes(Arrays.copyOfRange(in, n, in.length)));
    }
    return ret;
  }
}
