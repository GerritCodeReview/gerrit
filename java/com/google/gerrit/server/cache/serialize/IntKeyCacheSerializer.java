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

import com.google.gwtorm.client.IntKey;
import java.util.function.Function;

public class IntKeyCacheSerializer<K extends IntKey<?>> implements CacheSerializer<K> {
  private final Function<Integer, K> factory;

  public IntKeyCacheSerializer(Function<Integer, K> factory) {
    this.factory = requireNonNull(factory);
  }

  @Override
  public byte[] serialize(K object) {
    return IntegerCacheSerializer.INSTANCE.serialize(object.get());
  }

  @Override
  public K deserialize(byte[] in) {
    return factory.apply(IntegerCacheSerializer.INSTANCE.deserialize(in));
  }
}
