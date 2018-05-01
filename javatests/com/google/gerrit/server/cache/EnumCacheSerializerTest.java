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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.protobuf.TextFormat;
import org.junit.Test;

public class EnumCacheSerializerTest {
  @Test
  public void serialize() throws Exception {
    for (MyEnum value : MyEnum.values()) {
      assertRoundTrip(value);
    }
  }

  @Test
  public void serializeToString() throws Exception {
    for (MyEnum value : MyEnum.values()) {
      assertRoundTripToString(value);
    }
  }

  private enum MyEnum {
    FOO,
    BAR,
    BAZ;
  }

  private static void assertRoundTrip(MyEnum e) throws Exception {
    CacheSerializer<MyEnum> s = new EnumCacheSerializer<>(MyEnum.class);
    byte[] serialized = s.serialize(e);
    MyEnum result = s.deserialize(serialized);
    assertThat(result)
        .named("round-trip of %s via \"%s\"", e, TextFormat.escapeBytes(serialized))
        .isEqualTo(e);
  }

  private static void assertRoundTripToString(MyEnum e) throws Exception {
    EnumCacheSerializer<MyEnum> s = new EnumCacheSerializer<>(MyEnum.class);
    String serialized = s.serializeToString(e);
    MyEnum result = s.deserialize(serialized.getBytes(UTF_8));
    assertThat(result).named("round-trip of %s via \"%s\"", e, serialized).isEqualTo(e);
  }
}
