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

import com.google.common.base.Function;
import com.google.gwtorm.client.IntKey;
import java.io.IOException;

/** Static utilities for writing {@link CacheSerializer}s. */
public class CacheSerializers {
  public static <K extends IntKey<?>> CacheSerializer<K> newIntKeySerializer(
      Function<Integer, K> factory) {
    return new CacheSerializer<K>() {
      @Override
      public byte[] serialize(K object) throws IOException {
        return IntegerCacheSerializer.INSTANCE.serialize(object.get());
      }

      @Override
      public K deserialize(byte[] in) throws IOException {
        return factory.apply(IntegerCacheSerializer.INSTANCE.deserialize(in));
      }
    };
  }

  private CacheSerializers() {}
}
