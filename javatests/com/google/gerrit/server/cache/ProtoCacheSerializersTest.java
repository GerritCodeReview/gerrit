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
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.bytes;

import com.google.gerrit.server.cache.ProtoCacheSerializers.ObjectIdHelper;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ProtoCacheSerializersTest {
  @Test
  public void objectIdFromByteString() {
    assertThat(
            ObjectIdHelper.fromByteString(
                bytes(
                    0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde,
                    0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef)))
        .isEqualTo(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  @Test
  public void objectIdToByteString() {
    ObjectIdHelper idHelper = new ObjectIdHelper();
    assertThat(
            idHelper.toByteString(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))
        .isEqualTo(
            bytes(
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa));
    assertThat(
            idHelper.toByteString(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")))
        .isEqualTo(
            bytes(
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb));
  }
}
