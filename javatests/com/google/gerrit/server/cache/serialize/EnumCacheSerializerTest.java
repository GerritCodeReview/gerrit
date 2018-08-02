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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.Test;

public class EnumCacheSerializerTest {
  @Test
  public void serialize() throws Exception {
    assertRoundTrip(MyEnum.FOO);
    assertRoundTrip(MyEnum.BAR);
    assertRoundTrip(MyEnum.BAZ);
  }

  @Test
  public void deserializeInvalidValues() throws Exception {
    assertDeserializeFails(null);
    assertDeserializeFails("".getBytes(UTF_8));
    assertDeserializeFails("foo".getBytes(UTF_8));
    assertDeserializeFails("QUUX".getBytes(UTF_8));
  }

  private enum MyEnum {
    FOO,
    BAR,
    BAZ;
  }

  private static void assertRoundTrip(MyEnum e) throws Exception {
    CacheSerializer<MyEnum> s = new EnumCacheSerializer<>(MyEnum.class);
    assertThat(s.deserialize(s.serialize(e))).isEqualTo(e);
  }

  private static void assertDeserializeFails(byte[] in) {
    CacheSerializer<MyEnum> s = new EnumCacheSerializer<>(MyEnum.class);
    try {
      s.deserialize(in);
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
