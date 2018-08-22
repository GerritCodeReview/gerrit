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

import com.google.protobuf.TextFormat;
import org.junit.Test;

public class BooleanCacheSerializerTest {
  @Test
  public void serialize() throws Exception {
    assertThat(BooleanCacheSerializer.INSTANCE.serialize(true))
        .isEqualTo(new byte[] {'t', 'r', 'u', 'e'});
    assertThat(BooleanCacheSerializer.INSTANCE.serialize(false))
        .isEqualTo(new byte[] {'f', 'a', 'l', 's', 'e'});
  }

  @Test
  public void deserialize() throws Exception {
    assertThat(BooleanCacheSerializer.INSTANCE.deserialize(new byte[] {'t', 'r', 'u', 'e'}))
        .isEqualTo(true);
    assertThat(BooleanCacheSerializer.INSTANCE.deserialize(new byte[] {'f', 'a', 'l', 's', 'e'}))
        .isEqualTo(false);
  }

  @Test
  public void deserializeInvalid() throws Exception {
    assertDeserializeFails(null);
    assertDeserializeFails("t".getBytes(UTF_8));
    assertDeserializeFails("tru".getBytes(UTF_8));
    assertDeserializeFails("trueee".getBytes(UTF_8));
    assertDeserializeFails("TRUE".getBytes(UTF_8));
    assertDeserializeFails("f".getBytes(UTF_8));
    assertDeserializeFails("fal".getBytes(UTF_8));
    assertDeserializeFails("falseee".getBytes(UTF_8));
    assertDeserializeFails("FALSE".getBytes(UTF_8));
  }

  private static void assertDeserializeFails(byte[] in) {
    try {
      BooleanCacheSerializer.INSTANCE.deserialize(in);
      assert_().fail("expected deserialization to fail for \"%s\"", TextFormat.escapeBytes(in));
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
