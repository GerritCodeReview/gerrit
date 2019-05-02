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
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteArray;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ObjectIdCacheSerializerTest {
  @Test
  public void serialize() {
    ObjectId id = ObjectId.fromString("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    byte[] serialized = ObjectIdCacheSerializer.INSTANCE.serialize(id);
    assertThat(serialized)
        .isEqualTo(
            byteArray(
                0xaa, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb));
    assertThat(ObjectIdCacheSerializer.INSTANCE.deserialize(serialized)).isEqualTo(id);
  }

  @Test
  public void deserializeInvalid() {
    assertDeserializeFails(null);
    assertDeserializeFails(byteArray());
    assertDeserializeFails(byteArray(0xaa));
    assertDeserializeFails(
        byteArray(
            0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
            0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa));
  }

  private void assertDeserializeFails(byte[] bytes) {
    try {
      ObjectIdCacheSerializer.INSTANCE.deserialize(bytes);
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }
}
