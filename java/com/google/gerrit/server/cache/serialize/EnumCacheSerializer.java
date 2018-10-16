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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Converter;
import com.google.common.base.Enums;

public class EnumCacheSerializer<E extends Enum<E>> implements CacheSerializer<E> {
  private final Converter<String, E> converter;

  public EnumCacheSerializer(Class<E> clazz) {
    this.converter = Enums.stringConverter(clazz);
  }

  @Override
  public byte[] serialize(E object) {
    return converter.reverse().convert(requireNonNull(object)).getBytes(UTF_8);
  }

  @Override
  public E deserialize(byte[] in) {
    return converter.convert(new String(requireNonNull(in), UTF_8));
  }
}
