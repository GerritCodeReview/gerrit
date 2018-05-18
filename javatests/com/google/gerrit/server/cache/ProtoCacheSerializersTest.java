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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.bytes;

import com.google.gerrit.server.cache.ProtoCacheSerializers.ObjectIdConverter;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesKeyProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ProtoCacheSerializersTest {
  @Test
  public void objectIdFromByteString() {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    assertThat(
            idConverter.fromByteString(
                bytes(
                    0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                    0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa)))
        .isEqualTo(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    assertThat(
            idConverter.fromByteString(
                bytes(
                    0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                    0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb)))
        .isEqualTo(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
  }

  @Test
  public void objectIdFromByteStringWrongSize() {
    try {
      ObjectIdConverter.create().fromByteString(ByteString.copyFromUtf8("foo"));
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void objectIdToByteString() {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    assertThat(
            idConverter.toByteString(
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))
        .isEqualTo(
            bytes(
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa));
    assertThat(
            idConverter.toByteString(
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")))
        .isEqualTo(
            bytes(
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb));
  }

  @Test
  public void parseUncheckedWrongProtoType() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] bytes = ProtoCacheSerializers.toByteArray(proto);
    try {
      ProtoCacheSerializers.parseUnchecked(ChangeNotesStateProto.parser(), bytes);
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void parseUncheckedInvalidData() {
    byte[] bytes = new byte[] {0x00};
    try {
      ProtoCacheSerializers.parseUnchecked(ChangeNotesStateProto.parser(), bytes);
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void parseUnchecked() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] bytes = ProtoCacheSerializers.toByteArray(proto);
    assertThat(ProtoCacheSerializers.parseUnchecked(ChangeNotesKeyProto.parser(), bytes))
        .isEqualTo(proto);
  }
}
