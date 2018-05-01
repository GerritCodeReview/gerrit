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
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import org.junit.Test;

public class IntegerCacheSerializerTest {
  @Test
  public void serialize() throws Exception {
    for (int i :
        ImmutableList.of(
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 20,
            -1,
            0,
            1,
            Integer.MAX_VALUE - 20,
            Integer.MAX_VALUE)) {
      assertRoundTrip(i);
    }
  }

  @Test
  public void deserializeExtraBytesFails() throws Exception {
    byte[] serialized = IntegerCacheSerializer.INSTANCE.serialize(1234);
    assertThat(IntegerCacheSerializer.INSTANCE.deserialize(serialized)).isEqualTo(1234);

    byte[] tooLong = Bytes.concat(serialized, new byte[] {0, 0, 0, 0});
    try {
      IntegerCacheSerializer.INSTANCE.deserialize(tooLong);
      assert_()
          .fail("expected deserialization to fail for \"%s\"", TextFormat.escapeBytes(tooLong));
    } catch (IOException e) {
      // Expected.
    }
  }

  private static void assertRoundTrip(int i) throws Exception {
    byte[] serialized = IntegerCacheSerializer.INSTANCE.serialize(i);
    int result = IntegerCacheSerializer.INSTANCE.deserialize(serialized);
    assertThat(result)
        .named("round-trip of %s via \"%s\"", i, TextFormat.escapeBytes(serialized))
        .isEqualTo(i);
  }
}
