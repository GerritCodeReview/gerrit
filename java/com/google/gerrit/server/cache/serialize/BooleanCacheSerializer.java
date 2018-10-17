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

import com.google.protobuf.TextFormat;
import java.util.Arrays;

public enum BooleanCacheSerializer implements CacheSerializer<Boolean> {
  INSTANCE;

  private static final byte[] TRUE = Boolean.toString(true).getBytes(UTF_8);
  private static final byte[] FALSE = Boolean.toString(false).getBytes(UTF_8);

  @Override
  public byte[] serialize(Boolean object) {
    byte[] bytes = requireNonNull(object) ? TRUE : FALSE;
    return Arrays.copyOf(bytes, bytes.length);
  }

  @Override
  public Boolean deserialize(byte[] in) {
    if (Arrays.equals(in, TRUE)) {
      return Boolean.TRUE;
    } else if (Arrays.equals(in, FALSE)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("Invalid Boolean value: " + TextFormat.escapeBytes(in));
  }
}
