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
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;
import static org.junit.Assert.assertThrows;

import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ObjectIdConverterTest {
  @Test
  public void objectIdFromByteString() {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    assertThat(
            idConverter.fromByteString(
                byteString(
                    0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                    0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa)))
        .isEqualTo(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    assertThat(
            idConverter.fromByteString(
                byteString(
                    0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                    0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb)))
        .isEqualTo(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
  }

  @Test
  public void objectIdFromByteStringWrongSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ObjectIdConverter.create().fromByteString(ByteString.copyFromUtf8("foo")));
  }

  @Test
  public void objectIdToByteString() {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    assertThat(
            idConverter.toByteString(
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))
        .isEqualTo(
            byteString(
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa));
    assertThat(
            idConverter.toByteString(
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")))
        .isEqualTo(
            byteString(
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb));
  }
}
