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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class StringCacheSerializerTest {
  @Test
  public void serialize() {
    assertThat(StringCacheSerializer.INSTANCE.serialize("")).isEmpty();
    assertThat(StringCacheSerializer.INSTANCE.serialize("abc"))
        .isEqualTo(new byte[] {'a', 'b', 'c'});
    assertThat(StringCacheSerializer.INSTANCE.serialize("a\u1234c"))
        .isEqualTo(new byte[] {'a', (byte) 0xe1, (byte) 0x88, (byte) 0xb4, 'c'});
  }

  @Test
  public void serializeInvalidChar() {
    // Can't use UTF-8 for the test, since it can encode all Unicode code points.
    try {
      StringCacheSerializer.serialize(StandardCharsets.US_ASCII, "\u1234");
      assert_().fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CharacterCodingException.class);
    }
  }

  @Test
  public void deserialize() {
    assertThat(StringCacheSerializer.INSTANCE.deserialize(new byte[0])).isEmpty();
    assertThat(StringCacheSerializer.INSTANCE.deserialize(new byte[] {'a', 'b', 'c'}))
        .isEqualTo("abc");
    assertThat(
            StringCacheSerializer.INSTANCE.deserialize(
                new byte[] {'a', (byte) 0xe1, (byte) 0x88, (byte) 0xb4, 'c'}))
        .isEqualTo("a\u1234c");
  }

  @Test
  public void deserializeInvalidChar() {
    try {
      StringCacheSerializer.INSTANCE.deserialize(new byte[] {(byte) 0xff});
      assert_().fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CharacterCodingException.class);
    }
  }
}
